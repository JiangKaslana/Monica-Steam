package takagi.ru.monica.steam.friends.chat.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.data.SteamChatCache
import takagi.ru.monica.steam.friends.chat.data.SteamChatOutbox
import takagi.ru.monica.steam.friends.chat.domain.SteamChatDeliveryState
import takagi.ru.monica.steam.friends.chat.domain.SteamChatGateway
import takagi.ru.monica.steam.friends.chat.domain.SteamChatHistoryBoundary
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot
import takagi.ru.monica.steam.friends.chat.domain.SteamChatThreadSnapshot
import takagi.ru.monica.steam.friends.chat.domain.mergeSteamChatMessages
import takagi.ru.monica.steam.network.SteamSessionRefreshService
class SteamChatViewModel(
    private val gateway: SteamChatGateway,
    private val cache: SteamChatCache,
    private val sessionRefreshService: SteamSessionRefreshService? = SteamSessionRefreshService(),
    private val forceSessionRefresh: ((SteamAccount) -> SteamAccount?)? = null,
    private val persistSession: suspend (SteamAccount) -> Unit = {},
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val clientMessageId: () -> String = { UUID.randomUUID().toString() },
    private val outbox: SteamChatOutbox? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(SteamChatUiState())
    val uiState: StateFlow<SteamChatUiState> = _uiState.asStateFlow()
    private var activeAccount: SteamAccount? = null
    private val requestGuard = SteamChatRequestGuard()
    private var pollingJob: Job? = null
    private var foreground = false
    private val outgoingCoordinator = SteamChatOutgoingCoordinator(
        scope = viewModelScope,
        gateway = gateway,
        sessionRefreshService = sessionRefreshService,
        forceSessionRefresh = forceSessionRefresh,
        persistSession = persistSession,
        ioDispatcher = ioDispatcher,
        outbox = outbox
    )

    fun selectAccount(account: SteamAccount?) {
        if (account?.id == activeAccount?.id && account?.steamId == activeAccount?.steamId) {
            activeAccount = account
            return
        }
        activeAccount = account
        val generation = requestGuard.selectAccount(account)
        if (account == null) {
            _uiState.value = SteamChatUiState(
                sessionsFailure = SteamChatFailureReason.ACCOUNT_REQUIRED
            )
            restartPolling()
            return
        }
        _uiState.value = SteamChatUiState(
            accountId = account.id,
            accountSteamId = account.steamId,
            sessionsLoading = true
        )
        viewModelScope.launch {
            val cached = withContext(ioDispatcher) { cache.loadSessions(account.steamId) }
            if (!isSessionsCurrent(account, generation)) return@launch
            _uiState.value = _uiState.value.copy(
                sessions = cached,
                sessionsLoading = cached == null,
                sessionsRefreshing = cached != null,
                sessionsFromCache = cached != null
            )
            fetchSessions(account, generation, silent = cached != null)
        }
        restartPolling()
    }
    fun openThread(partnerSteamId: String) {
        val account = activeAccount ?: return
        if (partnerSteamId.isBlank()) return
        val generation = requestGuard.selectThread(partnerSteamId)
        _uiState.value = _uiState.value.copy(
            selectedPartnerSteamId = partnerSteamId,
            thread = null,
            threadLoading = true,
            threadRefreshing = false,
            threadFromCache = false,
            threadFailure = null
        )
        viewModelScope.launch {
            val cached = withContext(ioDispatcher) {
                cache.loadThread(account.steamId, partnerSteamId)
            }
            if (!isThreadCurrent(account, partnerSteamId, generation)) return@launch
            _uiState.value = _uiState.value.copy(
                thread = cached,
                threadLoading = cached == null,
                threadRefreshing = cached != null,
                threadFromCache = cached != null
            )
            recoverPendingSteamChatOutbox(
                outbox = outbox,
                account = account,
                partnerSteamId = partnerSteamId,
                ioDispatcher = ioDispatcher,
                isCurrent = { isThreadCurrent(account, partnerSteamId, generation) },
                onRecovered = { item ->
                    updateMessage(account, partnerSteamId, item.message)
                    dispatchSend(
                        account = account,
                        partnerSteamId = partnerSteamId,
                        pending = item.message,
                        verifyBeforeSend = item.verifyBeforeSend
                    )
                }
            )
            fetchThread(account, partnerSteamId, generation, silent = cached != null)
        }
    }
    fun closeThread() {
        requestGuard.closeThread()
        _uiState.value = _uiState.value.copy(
            selectedPartnerSteamId = null,
            thread = null,
            threadLoading = false,
            threadRefreshing = false,
            loadingOlder = false,
            threadFromCache = false,
            threadFailure = null
        )
    }
    fun refreshSessions() {
        val account = activeAccount ?: return
        val generation = requestGuard.nextSessions()
        _uiState.value = _uiState.value.copy(
            sessionsLoading = _uiState.value.sessions == null,
            sessionsRefreshing = _uiState.value.sessions != null,
            sessionsFailure = null
        )
        fetchSessions(account, generation, silent = false)
    }
    fun refreshThread() {
        val account = activeAccount ?: return
        val partnerSteamId = _uiState.value.selectedPartnerSteamId ?: return
        val generation = requestGuard.selectThread(partnerSteamId)
        _uiState.value = _uiState.value.copy(
            threadLoading = _uiState.value.thread == null,
            threadRefreshing = _uiState.value.thread != null,
            threadFailure = null
        )
        fetchThread(account, partnerSteamId, generation, silent = false)
    }

    fun loadOlder() {
        val account = activeAccount ?: return
        val state = _uiState.value
        val partnerSteamId = state.selectedPartnerSteamId ?: return
        val thread = state.thread ?: return
        val oldest = thread.messages.firstOrNull() ?: return
        if (!thread.moreAvailable || state.loadingOlder) return
        val generation = requestGuard.currentThreadGeneration()
        _uiState.value = state.copy(loadingOlder = true, threadFailure = null)
        viewModelScope.launch {
            val result = runCatching {
                withContext(ioDispatcher) {
                    gateway.fetchMessages(
                        account = prepareSteamChatSession(account, sessionRefreshService),
                        partnerSteamId = partnerSteamId,
                        before = SteamChatHistoryBoundary(oldest.timestamp, oldest.ordinal)
                    )
                }
            }
            if (!isThreadCurrent(account, partnerSteamId, generation)) return@launch
            result.fold(
                onSuccess = { page ->
                    val current = _uiState.value.thread ?: thread
                    val updated = current.copy(
                        messages = mergeSteamChatMessages(page.messages, current.messages),
                        moreAvailable = page.moreAvailable,
                        fetchedAt = nowMillis()
                    )
                    persistThread(account, partnerSteamId, updated)
                    _uiState.value = _uiState.value.copy(
                        thread = updated,
                        loadingOlder = false,
                        threadFromCache = false,
                        threadFailure = null
                    )
                },
                onFailure = { error ->
                    logSteamChatFailure("load_older", error)
                    _uiState.value = _uiState.value.copy(
                        loadingOlder = false,
                        threadFailure = error.toSteamChatFailureReason()
                    )
                }
            )
        }
    }

    fun sendMessage(body: String) = sendMessage(body, replyToStableId = null)
    fun sendReply(body: String, replyToStableId: String) {
        if (replyToStableId.isNotBlank()) sendMessage(body, replyToStableId)
    }
    private fun sendMessage(body: String, replyToStableId: String?) {
        val normalized = body.trim()
        if (normalized.isBlank()) return
        val account = activeAccount ?: return
        val partnerSteamId = _uiState.value.selectedPartnerSteamId ?: return
        val id = clientMessageId()
        val optimistic = newPendingSteamChatMessage(
            accountSteamId = account.steamId,
            partnerSteamId = partnerSteamId,
            body = normalized,
            timestamp = nowMillis() / 1000L,
            clientMessageId = id,
            replyToStableId = replyToStableId
        )
        updateMessage(account, partnerSteamId, optimistic)
        dispatchSend(account, partnerSteamId, optimistic)
    }

    fun retryMessage(clientMessageId: String) {
        val account = activeAccount ?: return
        val partnerSteamId = _uiState.value.selectedPartnerSteamId ?: return
        val failed = _uiState.value.thread?.messages?.firstOrNull {
            it.clientMessageId == clientMessageId &&
                it.deliveryState == SteamChatDeliveryState.FAILED_RETRYABLE
        } ?: return
        val pending = failed.copy(deliveryState = SteamChatDeliveryState.VERIFYING)
        updateMessage(account, partnerSteamId, pending)
        dispatchSend(account, partnerSteamId, pending, verifyBeforeSend = true)
    }

    fun setForeground(active: Boolean) {
        if (foreground == active) return
        foreground = active
        restartPolling()
    }

    fun clearThreadFailure() {
        _uiState.value = _uiState.value.copy(threadFailure = null)
    }

    private fun fetchSessions(account: SteamAccount, generation: Long, silent: Boolean) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(ioDispatcher) {
                    gateway.fetchSessions(prepareSteamChatSession(account, sessionRefreshService))
                }
            }
            if (!isSessionsCurrent(account, generation)) return@launch
            result.fold(
                onSuccess = { snapshot ->
                    val reconciled = reconcileSteamChatSessions(snapshot, _uiState.value.sessions)
                    withContext(ioDispatcher) { cache.saveSessions(account.steamId, reconciled) }
                    if (!isSessionsCurrent(account, generation)) return@launch
                    _uiState.value = _uiState.value.copy(
                        sessions = reconciled,
                        sessionsLoading = false,
                        sessionsRefreshing = false,
                        sessionsFromCache = false,
                        sessionsFailure = null
                    )
                },
                onFailure = { error ->
                    logSteamChatFailure("sessions", error)
                    _uiState.value = _uiState.value.copy(
                        sessionsLoading = false,
                        sessionsRefreshing = false,
                        sessionsFromCache = _uiState.value.sessions != null,
                        sessionsFailure = if (silent && _uiState.value.sessions != null) null
                        else error.toSteamChatFailureReason()
                    )
                }
            )
        }
    }

    private fun fetchThread(
        account: SteamAccount,
        partnerSteamId: String,
        generation: Long,
        silent: Boolean
    ) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(ioDispatcher) {
                    gateway.fetchMessages(
                        prepareSteamChatSession(account, sessionRefreshService),
                        partnerSteamId
                    )
                }
            }
            if (!isThreadCurrent(account, partnerSteamId, generation)) return@launch
            result.fold(
                onSuccess = { page ->
                    val current = _uiState.value.thread
                    val snapshot = SteamChatThreadSnapshot(
                        accountSteamId = account.steamId,
                        partnerSteamId = partnerSteamId,
                        messages = mergeSteamChatMessages(current?.messages.orEmpty(), page.messages),
                        moreAvailable = page.moreAvailable,
                        fetchedAt = nowMillis()
                    ).failUnresolvedVerification()
                    persistThread(account, partnerSteamId, snapshot)
                    _uiState.value = _uiState.value.copy(
                        thread = snapshot,
                        threadLoading = false,
                        threadRefreshing = false,
                        threadFromCache = false,
                        threadFailure = null
                    )
                    acknowledgeLatest(account, partnerSteamId, snapshot, generation)
                },
                onFailure = { error ->
                    logSteamChatFailure("thread", error)
                    _uiState.value = _uiState.value.copy(
                        threadLoading = false,
                        threadRefreshing = false,
                        threadFromCache = _uiState.value.thread != null,
                        threadFailure = if (silent && _uiState.value.thread != null) null
                        else error.toSteamChatFailureReason()
                    )
                }
            )
        }
    }

    private fun dispatchSend(
        account: SteamAccount,
        partnerSteamId: String,
        pending: SteamChatMessage,
        verifyBeforeSend: Boolean = false
    ) {
        val generation = requestGuard.currentThreadGeneration()
        outgoingCoordinator.dispatch(
            account = account,
            partnerSteamId = partnerSteamId,
            pending = pending,
            verifyBeforeSend = verifyBeforeSend,
            isCurrent = { isThreadCurrent(account, partnerSteamId, generation) },
            onSessionRefreshed = { refreshedAccount ->
                if (activeAccount?.id == account.id && activeAccount?.steamId == account.steamId) {
                    activeAccount = refreshedAccount
                }
            },
            onUpdate = { updateMessage(account, partnerSteamId, it) }
        )
    }

    private fun updateMessage(
        account: SteamAccount,
        partnerSteamId: String,
        message: SteamChatMessage
    ) {
        if (activeAccount?.id != account.id || _uiState.value.selectedPartnerSteamId != partnerSteamId) {
            return
        }
        val updatedState = _uiState.value.withChatMessage(
            accountSteamId = account.steamId,
            partnerSteamId = partnerSteamId,
            message = message,
            nowMillis = nowMillis()
        )
        _uiState.value = updatedState
        val updatedThread = updatedState.thread ?: return
        val updatedSessions = updatedState.sessions ?: return
        persistThread(account, partnerSteamId, updatedThread)
        viewModelScope.launch(ioDispatcher) {
            cache.saveSessions(account.steamId, updatedSessions)
        }
    }
    private fun acknowledgeLatest(
        account: SteamAccount,
        partnerSteamId: String,
        snapshot: SteamChatThreadSnapshot,
        generation: Long
    ) {
        val timestamp = snapshot.messages.asReversed()
            .firstOrNull { !it.isOutgoing(account.steamId) }
            ?.timestamp ?: return
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    gateway.acknowledge(
                        prepareSteamChatSession(account, sessionRefreshService),
                        partnerSteamId,
                        timestamp
                    )
                }
            }.onFailure { logSteamChatFailure("ack", it) }
            if (!isThreadCurrent(account, partnerSteamId, generation)) return@launch
            val sessions = _uiState.value.sessions ?: return@launch
            val updated = sessions.copy(
                sessions = sessions.sessions.map { session ->
                    if (session.partnerSteamId == partnerSteamId) {
                        session.copy(unreadCount = 0, lastViewTimestamp = timestamp)
                    } else session
                }
            )
            _uiState.value = _uiState.value.copy(sessions = updated)
            withContext(ioDispatcher) { cache.saveSessions(account.steamId, updated) }
        }
    }
    private fun persistThread(
        account: SteamAccount,
        partnerSteamId: String,
        snapshot: SteamChatThreadSnapshot
    ) {
        viewModelScope.launch(ioDispatcher) {
            cache.saveThread(account.steamId, partnerSteamId, snapshot)
        }
    }
    private fun restartPolling() {
        pollingJob?.cancel()
        pollingJob = null
        if (!foreground || activeAccount == null) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MILLIS)
                refreshSessions()
                if (_uiState.value.selectedPartnerSteamId != null) refreshThread()
            }
        }
    }
    private fun isSessionsCurrent(account: SteamAccount, generation: Long): Boolean =
        requestGuard.isSessionsCurrent(account, generation)
    private fun isThreadCurrent(
        account: SteamAccount,
        partnerSteamId: String,
        generation: Long
    ): Boolean = requestGuard.isThreadCurrent(account, partnerSteamId, generation)
    companion object {
        private const val POLL_INTERVAL_MILLIS = 15_000L

        fun factory(context: Context): ViewModelProvider.Factory =
            SteamChatViewModelFactory.create(context)
    }
}
