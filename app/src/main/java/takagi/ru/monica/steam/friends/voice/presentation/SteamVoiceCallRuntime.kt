package takagi.ru.monica.steam.friends.voice.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.friends.voice.background.SteamVoiceCallService
import takagi.ru.monica.steam.friends.voice.background.SteamVoiceNotificationPublisher
import takagi.ru.monica.steam.friends.voice.data.SteamVoiceRealtimeService
import takagi.ru.monica.steam.friends.voice.data.SteamVoiceService
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceConnectionState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceIncomingRequest
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceParticipant
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceRealtimeEvent
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceTarget
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceTargetType
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceWebRtcSession
import takagi.ru.monica.steam.friends.voice.media.SteamVoiceWebViewCallbacks
import takagi.ru.monica.steam.friends.voice.media.SteamVoiceWebViewEngine
import takagi.ru.monica.steam.network.cm.SteamCmClient
import takagi.ru.monica.steam.network.cm.steamCmAccountKey
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver
import takagi.ru.monica.steam.session.domain.resolveOrKeep

/** Process-owned voice session that survives navigation and screen-off via its foreground service. */
class SteamVoiceCallRuntime private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sourceRepository = SteamAccountSourceRepository.get(appContext)
    private val sessionResolver: SteamAccountSessionResolver = sourceRepository.sessionResolver()
    private val cm = SteamCmClient { account ->
        sourceRepository.sessionHandle(account)?.stableKey ?: steamCmAccountKey(account)
    }
    private val gateway = SteamVoiceService(cm)
    private val realtime = SteamVoiceRealtimeService(cm, sessionResolver)
    private val notificationPublisher = SteamVoiceNotificationPublisher(appContext)
    private val _state = MutableStateFlow(SteamVoiceCallState())
    val state: StateFlow<SteamVoiceCallState> = _state.asStateFlow()

    private var account: SteamAccount? = null
    private var mediaEngine: SteamVoiceWebViewEngine? = null
    private var realtimeJob: Job? = null
    private var webRtcSession: SteamVoiceWebRtcSession? = null
    private var remoteDescriptionVersion = "0"
    private var joiningVoice = false
    private var voiceWebRtcUpdated = false

    fun startGroup(
        account: SteamAccount,
        groupId: String,
        chatId: String,
        title: String
    ) = start(
        account,
        SteamVoiceTarget(
            type = SteamVoiceTargetType.GROUP,
            title = title,
            groupId = groupId,
            chatId = chatId
        )
    )

    /** Keeps the CM voice notification stream alive for incoming call invites. */
    fun observeAccount(account: SteamAccount) {
        if (this.account?.id == account.id && realtimeJob?.isActive == true) return
        if (_state.value.isActive && this.account?.id != account.id) return
        val previousAccount = this.account
        val previousRequest = _state.value.incomingRequest
        if (previousAccount != null && previousAccount.id != account.id && previousRequest != null) {
            scope.launch {
                runNetwork(previousAccount, "reject_previous_account") { prepared ->
                    gateway.answerDirectVoice(
                        prepared,
                        previousRequest.partnerSteamId,
                        previousRequest.voiceChatId,
                        accepted = false
                    )
                }
            }
            notificationPublisher.cancel()
            _state.value = SteamVoiceCallState(accountSteamId = account.steamId)
        }
        this.account = account
        _state.value = _state.value.copy(accountSteamId = account.steamId)
        startRealtime(account)
    }

    fun startDirect(account: SteamAccount, partnerSteamId: String, title: String) = start(
        account,
        SteamVoiceTarget(
            type = SteamVoiceTargetType.DIRECT,
            title = title,
            partnerSteamId = partnerSteamId
        )
    )

    fun acceptIncoming(account: SteamAccount, title: String) {
        val request = _state.value.incomingRequest ?: return
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = _state.value.copy(
                failure = "Microphone permission is required for Steam voice chat"
            )
            return
        }
        this.account = account
        scope.launch {
            val result = runNetwork("accept_direct") { prepared ->
                gateway.answerDirectVoice(
                    prepared,
                    request.partnerSteamId,
                    request.voiceChatId,
                    accepted = true
                )
            }
            if (result.isFailure) {
                _state.value = _state.value.copy(
                    failure = result.exceptionOrNull().voiceMessage("Unable to accept Steam voice call")
                )
                return@launch
            }
            start(
                account = account,
                target = SteamVoiceTarget(
                    type = SteamVoiceTargetType.DIRECT,
                    title = title,
                    partnerSteamId = request.partnerSteamId
                ),
                initialVoiceChatId = request.voiceChatId
            )
        }
    }

    internal fun acceptIncomingFromNotification() {
        val currentAccount = account ?: return
        val request = _state.value.incomingRequest ?: return
        acceptIncoming(currentAccount, request.partnerSteamId)
    }

    internal fun rejectIncomingFromNotification() = rejectIncoming()

    fun rejectIncoming() {
        val currentAccount = account ?: return
        val request = _state.value.incomingRequest ?: return
        scope.launch {
            runNetwork("reject_direct") { prepared ->
                gateway.answerDirectVoice(
                    prepared,
                    request.partnerSteamId,
                    request.voiceChatId,
                    accepted = false
                )
            }
            _state.value = _state.value.copy(incomingRequest = null)
            notificationPublisher.cancel()
        }
    }

    fun toggleMicrophone() {
        val muted = !_state.value.microphoneMuted
        _state.value = _state.value.copy(microphoneMuted = muted)
        mediaEngine?.setMicrophoneMuted(muted)
        publishVoiceStatus()
    }

    fun toggleOutput() {
        val muted = !_state.value.outputMuted
        _state.value = _state.value.copy(outputMuted = muted)
        mediaEngine?.setOutputMuted(muted)
        publishVoiceStatus()
    }

    fun stop() = stopInternal(notifySteam = true)

    fun clearFailure() {
        _state.value = _state.value.copy(failure = null)
    }

    private fun start(
        account: SteamAccount,
        target: SteamVoiceTarget,
        initialVoiceChatId: String = ""
    ) {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = _state.value.copy(
                failure = "Microphone permission is required for Steam voice chat"
            )
            return
        }
        if (_state.value.isActive) stopInternal(notifySteam = true)
        this.account = account
        webRtcSession = null
        remoteDescriptionVersion = "0"
        joiningVoice = false
        voiceWebRtcUpdated = false
        _state.value = SteamVoiceCallState(
            accountSteamId = account.steamId,
            target = target,
            voiceChatId = initialVoiceChatId,
            state = SteamVoiceConnectionState.REQUESTING_MICROPHONE,
            incomingRequest = null
        )
        startRealtime(account)
        runCatching { SteamVoiceCallService.start(appContext) }
            .onFailure { error ->
                SteamDiagLogger.append(
                    "voice_foreground_start failed type=${error::class.java.simpleName}"
                )
            }
        mediaEngine = SteamVoiceWebViewEngine(
            appContext,
            object : SteamVoiceWebViewCallbacks {
                override fun onLocalOffer(descriptionJson: String) {
                    scope.launch { initiateWebRtc(descriptionJson) }
                }

                override fun onLocalAnswer(descriptionJson: String) {
                    scope.launch { acknowledgeRemoteDescription(descriptionJson) }
                }

                override fun onIceStateChanged(state: String) {
                    if (state == "connected" || state == "completed") {
                        scope.launch { onIceConnected() }
                    } else if (state == "failed") {
                        scope.launch { fail("Steam voice media connection failed") }
                    } else if (state == "disconnected") {
                        if (_state.value.isActive) {
                            _state.value = _state.value.copy(
                                state = SteamVoiceConnectionState.RECONNECTING
                            )
                        }
                    }
                }

                override fun onMediaStats(stats: String) {
                    SteamDiagLogger.append("voice_media outbound=${stats.take(240)}")
                }

                override fun onFailure(message: String) {
                    scope.launch { fail("Steam voice media failed: ${message.take(160)}") }
                }
            }
        ).also(SteamVoiceWebViewEngine::start)
    }

    private suspend fun initiateWebRtc(descriptionJson: String) {
        if (!_state.value.isActive) return
        _state.value = _state.value.copy(state = SteamVoiceConnectionState.CONNECTING_MEDIA)
        val remote = runNetwork("init_webrtc") { prepared ->
            gateway.initiateWebRtc(
                prepared,
                descriptionJson,
                clientName = "Chrome",
                clientVersion = "126"
            )
        }.getOrElse {
            fail(it.voiceMessage("Steam WebRTC connection failed"))
            return
        }
        mediaEngine?.setRemoteDescription(remote)
    }

    private suspend fun onIceConnected() {
        if (!_state.value.isActive) return
        if (voiceWebRtcUpdated && _state.value.voiceChatId.isNotBlank()) {
            _state.value = _state.value.copy(state = SteamVoiceConnectionState.CONNECTED)
            return
        }
        if (joiningVoice) return
        val currentTarget = _state.value.target ?: return
        joiningVoice = true
        when (currentTarget.type) {
            SteamVoiceTargetType.GROUP -> {
                val voiceId = runNetwork("join_group") { prepared ->
                    gateway.joinGroupVoice(
                        prepared,
                        requireNotNull(currentTarget.groupId),
                        requireNotNull(currentTarget.chatId)
                    )
                }.getOrElse {
                    joiningVoice = false
                    fail(it.voiceMessage("Unable to join Steam voice channel"))
                    return
                }
                _state.value = _state.value.copy(voiceChatId = voiceId)
                maybeUpdateVoiceWebRtcData()
            }
            SteamVoiceTargetType.DIRECT -> {
                val partnerSteamId = requireNotNull(currentTarget.partnerSteamId)
                if (_state.value.voiceChatId.isNotBlank()) {
                    maybeUpdateVoiceWebRtcData()
                } else {
                    val voiceId = runNetwork("request_direct") { prepared ->
                        gateway.requestDirectVoice(prepared, partnerSteamId)
                    }.getOrElse {
                        joiningVoice = false
                        fail(it.voiceMessage("Unable to start Steam voice call"))
                        return
                    }
                    _state.value = _state.value.copy(
                        voiceChatId = voiceId,
                        state = SteamVoiceConnectionState.WAITING_FOR_ACCEPT
                    )
                }
            }
        }
    }

    private suspend fun maybeUpdateVoiceWebRtcData() {
        val voiceChatId = _state.value.voiceChatId.takeIf(String::isNotBlank) ?: return
        val session = webRtcSession ?: return
        if (voiceWebRtcUpdated) return
        voiceWebRtcUpdated = true
        val result = runNetwork("update_voice_webrtc") { prepared ->
            gateway.updateVoiceWebRtcData(
                prepared,
                voiceChatId,
                session,
                userAgent = ANDROID_WEBVIEW_USER_AGENT
            )
        }
        if (result.isFailure) {
            voiceWebRtcUpdated = false
            joiningVoice = false
            fail(result.exceptionOrNull().voiceMessage("Steam voice session setup failed"))
            return
        }
        joiningVoice = false
        _state.value = _state.value.copy(state = SteamVoiceConnectionState.CONNECTED)
        publishVoiceStatus()
    }

    private suspend fun acknowledgeRemoteDescription(localAnswerJson: String) {
        val session = webRtcSession ?: return
        val version = remoteDescriptionVersion
        val result = runNetwork("ack_remote_description") { prepared ->
            gateway.acknowledgeRemoteDescription(prepared, session, version)
        }
        if (result.isFailure) {
            SteamDiagLogger.append(
                "voice_ack_remote failed type=${result.exceptionOrNull()?.javaClass?.simpleName}"
            )
        }
        // Steam's server receives the local answer through the acknowledged
        // WebRTC session coordinates; keeping the argument makes the callback
        // explicit and prevents accidental acknowledgement before an answer.
        if (localAnswerJson.isBlank()) return
    }

    private fun startRealtime(account: SteamAccount) {
        realtimeJob?.cancel()
        realtimeJob = scope.launch {
            try {
                realtime.events(account).collect(::applyRealtimeEvent)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (_state.value.isActive) {
                    _state.value = _state.value.copy(
                        state = SteamVoiceConnectionState.RECONNECTING,
                        failure = error.voiceMessage("Steam voice signaling disconnected")
                    )
                }
            }
        }
    }

    private suspend fun applyRealtimeEvent(event: SteamVoiceRealtimeEvent) {
        when (event) {
            is SteamVoiceRealtimeEvent.ConnectionChanged -> {
                if (!event.connected && _state.value.isActive) {
                    _state.value = _state.value.copy(state = SteamVoiceConnectionState.RECONNECTING)
                }
            }
            is SteamVoiceRealtimeEvent.IncomingDirectRequest -> {
                if (_state.value.isActive) {
                    runNetwork("reject_busy_direct") { prepared ->
                        gateway.answerDirectVoice(
                            prepared,
                            event.partnerSteamId,
                            event.voiceChatId,
                            accepted = false
                        )
                    }
                } else if (_state.value.incomingRequest?.voiceChatId != event.voiceChatId) {
                    _state.value = _state.value.copy(
                        accountSteamId = account?.steamId.orEmpty(),
                        incomingRequest = SteamVoiceIncomingRequest(
                            event.partnerSteamId,
                            event.voiceChatId
                        )
                    )
                    notificationPublisher.post(_state.value)
                }
            }
            is SteamVoiceRealtimeEvent.DirectResponse -> {
                if (event.voiceChatId == _state.value.voiceChatId &&
                    event.partnerSteamId == _state.value.target?.partnerSteamId
                ) {
                    if (event.accepted) maybeUpdateVoiceWebRtcData()
                    else fail("Steam voice call was declined")
                }
            }
            is SteamVoiceRealtimeEvent.UserJoined -> {
                if (event.voiceChatId == _state.value.voiceChatId) {
                    updatePresence(event.steamId, joined = true)
                }
            }
            is SteamVoiceRealtimeEvent.UserLeft -> {
                if (event.voiceChatId == _state.value.voiceChatId) {
                    updatePresence(event.steamId, joined = false)
                }
            }
            is SteamVoiceRealtimeEvent.UserStatus -> {
                if (event.voiceChatId == _state.value.voiceChatId) {
                    updateParticipant(event.participant)
                }
            }
            is SteamVoiceRealtimeEvent.AllUsersStatus -> {
                if (event.voiceChatId == _state.value.voiceChatId) {
                    _state.value = _state.value.copy(participants = event.participants)
                }
            }
            is SteamVoiceRealtimeEvent.VoiceEnded -> {
                if (event.voiceChatId == _state.value.voiceChatId) stopInternal(notifySteam = false)
            }
            is SteamVoiceRealtimeEvent.RejoinRequired -> {
                val target = _state.value.target
                if (target?.type == SteamVoiceTargetType.GROUP &&
                    target.groupId == event.groupId && target.chatId == event.chatId
                ) {
                    voiceWebRtcUpdated = false
                    joiningVoice = false
                    _state.value = _state.value.copy(
                        voiceChatId = "",
                        state = SteamVoiceConnectionState.RECONNECTING
                    )
                    onIceConnected()
                }
            }
            is SteamVoiceRealtimeEvent.WebRtcConnected -> {
                if (_state.value.isActive) {
                    webRtcSession = event.session
                    maybeUpdateVoiceWebRtcData()
                }
            }
            is SteamVoiceRealtimeEvent.RemoteDescriptionUpdated -> {
                if (_state.value.isActive) {
                    remoteDescriptionVersion = event.description.version
                    mediaEngine?.setRemoteDescription(event.description.descriptionJson)
                }
            }
        }
    }

    private fun updatePresence(steamId: String, joined: Boolean) {
        val current = _state.value.participants.associateBy(SteamVoiceParticipant::steamId).toMutableMap()
        if (joined) {
            current[steamId] = current[steamId]?.copy(joined = true)
                ?: SteamVoiceParticipant(steamId)
        } else {
            current.remove(steamId)
        }
        _state.value = _state.value.copy(participants = current.values.toList())
    }

    private fun updateParticipant(participant: SteamVoiceParticipant) {
        val current = _state.value.participants.associateBy(SteamVoiceParticipant::steamId).toMutableMap()
        current[participant.steamId] = participant
        _state.value = _state.value.copy(participants = current.values.toList())
    }

    private fun publishVoiceStatus() {
        val voiceId = _state.value.voiceChatId.takeIf(String::isNotBlank) ?: return
        scope.launch {
            runNetwork("voice_status") { prepared ->
                gateway.notifyVoiceStatus(
                    prepared,
                    voiceId,
                    _state.value.microphoneMuted,
                    _state.value.outputMuted,
                    hasNoMicrophone = false
                )
            }
        }
    }

    private fun stopInternal(notifySteam: Boolean, failure: String? = null) {
        val currentAccount = account
        val current = _state.value
        if (notifySteam && currentAccount != null && current.target != null) {
            scope.launch {
                runNetwork("leave_voice") { prepared ->
                    when (current.target.type) {
                        SteamVoiceTargetType.GROUP -> if (current.voiceChatId.isNotBlank()) {
                            gateway.leaveGroupVoice(
                                prepared,
                                requireNotNull(current.target.groupId),
                                requireNotNull(current.target.chatId)
                            )
                        }
                        SteamVoiceTargetType.DIRECT -> if (current.voiceChatId.isNotBlank()) {
                            gateway.leaveDirectVoice(
                                prepared,
                                requireNotNull(current.target.partnerSteamId),
                                current.voiceChatId
                            )
                        }
                    }
                }
            }
        }
        realtimeJob?.cancel()
        realtimeJob = null
        mediaEngine?.stop()
        mediaEngine = null
        webRtcSession = null
        remoteDescriptionVersion = "0"
        joiningVoice = false
        voiceWebRtcUpdated = false
        account = currentAccount
        _state.value = SteamVoiceCallState(
            accountSteamId = currentAccount?.steamId.orEmpty(),
            state = if (failure == null) SteamVoiceConnectionState.IDLE
            else SteamVoiceConnectionState.FAILED,
            failure = failure
        )
        currentAccount?.let { startRealtime(it) }
        notificationPublisher.cancel()
        SteamVoiceCallService.stop(appContext)
    }

    private fun fail(message: String) {
        SteamDiagLogger.append("voice_call failed message=${message.take(180)}")
        stopInternal(notifySteam = true, failure = message)
    }

    private suspend fun <T> runNetwork(
        operation: String,
        block: (SteamAccount) -> T
    ): Result<T> {
        val current = account ?: return Result.failure(IllegalStateException("Steam voice account missing"))
        return runNetwork(current, operation, block)
    }

    private suspend fun <T> runNetwork(
        sourceAccount: SteamAccount,
        operation: String,
        block: (SteamAccount) -> T
    ): Result<T> {
        return try {
            val prepared = sessionResolver.resolveOrKeep(sourceAccount)
            if (account?.id == sourceAccount.id && account?.steamId == sourceAccount.steamId) {
                account = prepared
            }
            Result.success(withContext(Dispatchers.IO) { block(prepared) })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            SteamDiagLogger.append(
                "voice_network operation=$operation type=${error::class.java.simpleName}"
            )
            Result.failure(error)
        }
    }

    private fun Throwable?.voiceMessage(fallback: String): String =
        this?.message?.takeIf(String::isNotBlank)?.take(180) ?: fallback

    companion object {
        const val MICROPHONE_PERMISSION = Manifest.permission.RECORD_AUDIO
        private const val ANDROID_WEBVIEW_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36 Monica-Steam"
        private val INSTANCE = AtomicReference<SteamVoiceCallRuntime?>()

        fun get(context: Context): SteamVoiceCallRuntime = INSTANCE.get()
            ?: synchronized(this) {
                INSTANCE.get() ?: SteamVoiceCallRuntime(context).also(INSTANCE::set)
            }
    }
}
