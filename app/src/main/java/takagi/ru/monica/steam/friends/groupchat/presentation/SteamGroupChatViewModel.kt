package takagi.ru.monica.steam.friends.groupchat.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatCache
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatPreferencesCache
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatService
import takagi.ru.monica.steam.friends.groupchat.avatar.data.SteamGroupAvatarUploader
import takagi.ru.monica.steam.friends.groupchat.avatar.domain.SteamGroupAvatarUploadGateway
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatCreateRequest
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatDeliveryState
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatGateway
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatGroupsSnapshot
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatHistoryBoundary
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.friends.groupchat.domain.steamGroupAvatarUrl
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatThreadSnapshot
import takagi.ru.monica.steam.friends.groupchat.domain.mergeSteamGroupMessages
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRealtimeEvent
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRealtimeGateway
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver
import takagi.ru.monica.steam.session.domain.resolveOrKeep

data class SteamGroupChatUiState(
    val accountSteamId: String = "",
    val groups: List<SteamGroupChatSummary> = emptyList(),
    val selectedGroupId: String? = null,
    val selectedChatId: String? = null,
    val thread: SteamGroupChatThreadSnapshot? = null,
    val groupsLoading: Boolean = false,
    val groupsRefreshing: Boolean = false,
    val threadLoading: Boolean = false,
    val loadingOlder: Boolean = false,
    val creatingGroup: Boolean = false,
    val updatingGroup: Boolean = false,
    val updatingGroupAvatar: Boolean = false,
    val createdGroupId: String? = null,
    val realtimeConnected: Boolean = false,
    val failure: String? = null
)

class SteamGroupChatViewModel(
    private val gateway: SteamGroupChatGateway,
    private val cache: SteamGroupChatCache,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newClientId: () -> String = { UUID.randomUUID().toString() },
    private val realtime: SteamGroupChatRealtimeGateway? = null,
    private val sessionResolver: SteamAccountSessionResolver? = null,
    private val avatarUploader: SteamGroupAvatarUploadGateway? = null
) : ViewModel() {
    private val _state = MutableStateFlow(SteamGroupChatUiState())
    val state: StateFlow<SteamGroupChatUiState> = _state.asStateFlow()
    private var account: SteamAccount? = null
    private var accountGeneration = 0L
    private var roomGeneration = 0L
    private var foreground = false
    private var pollingJob: Job? = null
    private var realtimeJob: Job? = null

    fun selectAccount(account: SteamAccount?) {
        if (this.account?.id == account?.id && this.account?.steamId == account?.steamId) {
            this.account = account
            restartRealtime()
            restartPolling()
            return
        }
        this.account = account
        accountGeneration++
        roomGeneration++
        if (account == null) {
            _state.value = SteamGroupChatUiState(failure = "Steam account required")
            restartRealtime()
            restartPolling()
            return
        }
        val currentGeneration = accountGeneration
        _state.value = SteamGroupChatUiState(accountSteamId = account.steamId, groupsLoading = true)
        restartRealtime()
        restartPolling()
        viewModelScope.launch {
            val cached = withContext(ioDispatcher) { cache.loadGroups(account.steamId) }
            if (!isCurrent(account, currentGeneration)) return@launch
            _state.value = _state.value.copy(
                groups = cached?.groups.orEmpty(),
                groupsLoading = cached == null,
                groupsRefreshing = cached != null
            )
            fetchGroups(account, currentGeneration)
        }
    }

    fun refreshGroups() {
        val current = account ?: return
        _state.value = _state.value.copy(groupsRefreshing = _state.value.groups.isNotEmpty(), groupsLoading = _state.value.groups.isEmpty())
        fetchGroups(current, accountGeneration)
    }

    fun openRoom(groupId: String, chatId: String) {
        val current = account ?: return
        if (groupId.isBlank() || chatId.isBlank()) return
        val currentAccountGeneration = accountGeneration
        val currentRoomGeneration = ++roomGeneration
        _state.value = _state.value.copy(
            selectedGroupId = groupId,
            selectedChatId = chatId,
            thread = null,
            threadLoading = true,
            failure = null
        )
        viewModelScope.launch {
            val cached = withContext(ioDispatcher) { cache.loadThread(current.steamId, groupId, chatId) }
            if (!isRoomCurrent(
                    current,
                    groupId,
                    chatId,
                    currentAccountGeneration,
                    currentRoomGeneration
                )
            ) return@launch
            _state.value = _state.value.copy(thread = cached, threadLoading = cached == null)
            fetchThread(
                current,
                groupId,
                chatId,
                currentAccountGeneration,
                currentRoomGeneration
            )
        }
    }

    fun closeRoom() {
        roomGeneration++
        _state.value = _state.value.copy(selectedGroupId = null, selectedChatId = null, thread = null, threadLoading = false)
    }

    fun refreshThread() {
        val current = account ?: return
        val groupId = _state.value.selectedGroupId ?: return
        val chatId = _state.value.selectedChatId ?: return
        _state.value = _state.value.copy(threadLoading = _state.value.thread == null, failure = null)
        val currentAccountGeneration = accountGeneration
        val currentRoomGeneration = roomGeneration
        viewModelScope.launch {
            fetchThread(
                current,
                groupId,
                chatId,
                currentAccountGeneration,
                currentRoomGeneration
            )
        }
    }

    fun loadOlder() {
        val current = account ?: return
        val thread = _state.value.thread ?: return
        if (!thread.moreAvailable || _state.value.loadingOlder) return
        val oldest = thread.messages.firstOrNull() ?: return
        val currentAccountGeneration = accountGeneration
        val currentRoomGeneration = roomGeneration
        _state.value = _state.value.copy(loadingOlder = true)
        viewModelScope.launch {
            val result = runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared ->
                    gateway.getHistory(
                        prepared,
                        thread.groupId,
                        thread.chatId,
                        SteamGroupChatHistoryBoundary(oldest.timestamp, oldest.ordinal)
                    )
                }
            } }
            if (!isRoomCurrent(
                    current,
                    thread.groupId,
                    thread.chatId,
                    currentAccountGeneration,
                    currentRoomGeneration
                )
            ) return@launch
            result.fold(
                onSuccess = { page ->
                    val updated = thread.copy(
                        messages = mergeSteamGroupMessages(page.messages, _state.value.thread?.messages.orEmpty()),
                        moreAvailable = page.moreAvailable,
                        fetchedAt = nowMillis()
                    )
                    updateThread(updated)
                    _state.value = _state.value.copy(loadingOlder = false)
                },
                onFailure = { _state.value = _state.value.copy(loadingOlder = false, failure = it.groupChatMessage()) }
            )
        }
    }

    fun sendMessage(body: String) {
        val current = account ?: return
        val thread = _state.value.thread ?: return
        val normalized = body.trim()
        if (normalized.isBlank()) return
        val optimistic = SteamGroupChatMessage(
            groupId = thread.groupId,
            chatId = thread.chatId,
            senderSteamId = current.steamId,
            timestamp = nowMillis() / 1_000L,
            ordinal = Int.MAX_VALUE,
            body = normalized,
            clientMessageId = newClientId(),
            localCreatedAtMillis = nowMillis(),
            deliveryState = SteamGroupChatDeliveryState.QUEUED
        )
        updateMessage(optimistic)
        val currentAccountGeneration = accountGeneration
        val currentRoomGeneration = roomGeneration
        viewModelScope.launch {
            updateMessage(optimistic.copy(deliveryState = SteamGroupChatDeliveryState.SENDING))
            val result = runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared ->
                    gateway.sendMessage(prepared, thread.groupId, thread.chatId, normalized)
                }
            } }
            if (!isRoomCurrent(
                    current,
                    thread.groupId,
                    thread.chatId,
                    currentAccountGeneration,
                    currentRoomGeneration
                )
            ) return@launch
            result.fold(
                onSuccess = { sent -> updateMessage(sent.copy(
                    clientMessageId = optimistic.clientMessageId,
                    localCreatedAtMillis = optimistic.localCreatedAtMillis,
                    deliveryState = SteamGroupChatDeliveryState.SENT
                )) },
                onFailure = {
                    error ->
                    recoverTimedOutSend(
                        current,
                        optimistic,
                        error,
                        currentAccountGeneration,
                        currentRoomGeneration
                    )
                }
            )
        }
    }

    fun createGroup(name: String, inviteeSteamIds: List<String>) {
        val current = account ?: return
        if (_state.value.creatingGroup) return
        _state.value = _state.value.copy(creatingGroup = true, createdGroupId = null, failure = null)
        viewModelScope.launch {
            val result = runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared ->
                    gateway.createGroup(prepared, SteamGroupChatCreateRequest(name, inviteeSteamIds))
                }
            } }
            result.fold(
                onSuccess = { groupId ->
                    _state.value = _state.value.copy(creatingGroup = false, createdGroupId = groupId)
                    refreshGroups()
                },
                onFailure = { _state.value = _state.value.copy(creatingGroup = false, failure = it.groupChatMessage()) }
            )
        }
    }

    fun inviteFriend(steamId: String) {
        val current = account ?: return
        val groupId = _state.value.selectedGroupId ?: return
        val chatId = _state.value.selectedChatId ?: return
        viewModelScope.launch {
            runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared ->
                    gateway.inviteFriend(prepared, groupId, chatId, steamId)
                }
            } }
                .onFailure { _state.value = _state.value.copy(failure = it.groupChatMessage()) }
        }
    }

    fun updateGroup(name: String, tagline: String) {
        val current = account ?: return
        val groupId = _state.value.selectedGroupId ?: return
        if (_state.value.updatingGroup || name.isBlank()) return
        _state.value = _state.value.copy(updatingGroup = true, failure = null)
        viewModelScope.launch {
            val result = runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared ->
                    gateway.updateGroup(prepared, groupId, name, tagline)
                }
            } }
            result.fold(
                onSuccess = {
                    val groups = _state.value.groups.map { group ->
                        if (group.groupId == groupId) group.copy(name = name.trim(), tagline = tagline.trim()) else group
                    }
                    _state.value = _state.value.copy(groups = groups, updatingGroup = false)
                    withContext(ioDispatcher) {
                        cache.saveGroups(SteamGroupChatGroupsSnapshot(current.steamId, groups, nowMillis()))
                    }
                },
                onFailure = {
                    _state.value = _state.value.copy(updatingGroup = false, failure = it.groupChatMessage())
                }
            )
        }
    }

    fun updateGroupAvatar(rawUri: String) {
        val current = account ?: return
        val groupId = _state.value.selectedGroupId ?: return
        val uploader = avatarUploader ?: return
        if (_state.value.updatingGroupAvatar || rawUri.isBlank()) return
        _state.value = _state.value.copy(updatingGroupAvatar = true, failure = null)
        viewModelScope.launch {
            val result = runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared ->
                    val sha = uploader.upload(prepared, rawUri)
                    gateway.updateGroupAvatar(prepared, groupId, sha)
                    sha
                }
            } }
            result.fold(
                onSuccess = { sha ->
                    val avatarUrl = steamGroupAvatarUrl(sha)
                    val groups = _state.value.groups.map { group ->
                        if (group.groupId == groupId) group.copy(avatarUrl = avatarUrl) else group
                    }
                    _state.value = _state.value.copy(
                        groups = groups,
                        updatingGroupAvatar = false
                    )
                    withContext(ioDispatcher) {
                        cache.saveGroups(SteamGroupChatGroupsSnapshot(current.steamId, groups, nowMillis()))
                    }
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        updatingGroupAvatar = false,
                        failure = error.groupChatMessage()
                    )
                }
            )
        }
    }

    fun clearCreatedGroup() { _state.value = _state.value.copy(createdGroupId = null) }
    fun clearFailure() { _state.value = _state.value.copy(failure = null) }

    fun setForeground(active: Boolean) {
        if (foreground == active) return
        foreground = active
        restartRealtime()
        restartPolling()
    }

    private fun restartRealtime() {
        realtimeJob?.cancel()
        realtimeJob = null
        val current = account
        val gateway = realtime
        if (!foreground || current == null || gateway == null) {
            _state.value = _state.value.copy(realtimeConnected = false)
            return
        }
        val currentGeneration = accountGeneration
        realtimeJob = viewModelScope.launch {
            try {
                gateway.events(current).collect { event ->
                    if (!isCurrent(current, currentGeneration)) return@collect
                    when (event) {
                        is SteamGroupChatRealtimeEvent.ConnectionChanged -> {
                            if (_state.value.realtimeConnected != event.connected) {
                                _state.value = _state.value.copy(
                                    realtimeConnected = event.connected
                                )
                                restartPolling()
                            }
                        }
                        is SteamGroupChatRealtimeEvent.Message ->
                            applyRealtimeMessage(current, event.message)
                        is SteamGroupChatRealtimeEvent.MessageModified ->
                            applyRealtimeModification(event)
                        is SteamGroupChatRealtimeEvent.Acknowledged ->
                            applyRealtimeAcknowledgement(event)
                        is SteamGroupChatRealtimeEvent.RoomChanged -> {
                            refreshGroups()
                            if (_state.value.selectedGroupId == event.groupId) refreshThread()
                        }
                        is SteamGroupChatRealtimeEvent.Disconnected -> {
                            if (event.groupIds.isEmpty() ||
                                _state.value.selectedGroupId in event.groupIds
                            ) {
                                refreshGroups()
                                if (_state.value.selectedChatId != null) refreshThread()
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (isCurrent(current, currentGeneration) && _state.value.realtimeConnected) {
                    _state.value = _state.value.copy(realtimeConnected = false)
                    restartPolling()
                }
            }
        }
    }

    private fun applyRealtimeMessage(
        current: SteamAccount,
        message: SteamGroupChatMessage
    ) {
        val thread = _state.value.thread
        if (thread?.groupId == message.groupId && thread.chatId == message.chatId) {
            val updated = thread.copy(
                messages = mergeSteamGroupMessages(thread.messages, listOf(message)),
                fetchedAt = nowMillis()
            )
            updateThread(updated)
            acknowledgeLatest(current, updated)
            return
        }
        val groups = _state.value.groups.map { group ->
            if (group.groupId != message.groupId) return@map group
            val updatedRooms = group.rooms.map { room ->
                if (room.chatId != message.chatId ||
                    room.lastMessageTimestamp > message.timestamp ||
                    (room.lastMessageTimestamp == message.timestamp &&
                        room.lastMessage == message.body)
                ) return@map room
                room.copy(
                    lastMessageTimestamp = message.timestamp,
                    lastMessage = message.body,
                    lastSenderSteamId = message.senderSteamId,
                    unread = message.senderSteamId != current.steamId
                )
            }
            group.copy(
                rooms = updatedRooms,
                unreadCount = updatedRooms.count { it.unread }
            )
        }
        _state.value = _state.value.copy(groups = groups)
        viewModelScope.launch(ioDispatcher) {
            cache.saveGroups(
                SteamGroupChatGroupsSnapshot(current.steamId, groups, nowMillis())
            )
        }
    }

    private fun applyRealtimeModification(
        event: SteamGroupChatRealtimeEvent.MessageModified
    ) {
        val thread = _state.value.thread ?: return
        if (thread.groupId != event.groupId || thread.chatId != event.chatId) return
        val updatedMessages = thread.messages.map { message ->
            val modification = event.changes.firstOrNull {
                it.timestamp == message.timestamp && it.ordinal == message.ordinal
            }
            if (modification != null) {
                message.copy(deleted = modification.deleted)
            } else {
                message
            }
        }
        if (updatedMessages == thread.messages) return
        updateThread(thread.copy(messages = updatedMessages, fetchedAt = nowMillis()))
    }

    private fun applyRealtimeAcknowledgement(
        event: SteamGroupChatRealtimeEvent.Acknowledged
    ) {
        val groups = _state.value.groups.map { group ->
            if (group.groupId != event.groupId) return@map group
            val updatedRooms = group.rooms.map { room ->
                if (room.chatId != event.chatId) return@map room
                room.copy(
                    lastAcknowledgedTimestamp = maxOf(
                        room.lastAcknowledgedTimestamp,
                        event.timestamp
                    ),
                    unread = room.lastMessageTimestamp > event.timestamp
                )
            }
            group.copy(
                rooms = updatedRooms,
                unreadCount = updatedRooms.count { it.unread }
            )
        }
        _state.value = _state.value.copy(groups = groups)
    }

    private fun fetchGroups(current: SteamAccount, currentGeneration: Long) {
        viewModelScope.launch {
            val result = runCatchingCancellable { withContext(ioDispatcher) {
                withPreparedSession(current) { prepared -> gateway.getMyGroups(prepared) }
            } }
            if (!isCurrent(current, currentGeneration)) return@launch
            result.fold(
                onSuccess = { groups ->
                    val snapshot = SteamGroupChatGroupsSnapshot(current.steamId, groups, nowMillis())
                    withContext(ioDispatcher) { cache.saveGroups(snapshot) }
                    _state.value = _state.value.copy(groups = groups, groupsLoading = false, groupsRefreshing = false, failure = null)
                },
                onFailure = { _state.value = _state.value.copy(groupsLoading = false, groupsRefreshing = false, failure = it.groupChatMessage()) }
            )
        }
    }

    private suspend fun fetchThread(
        current: SteamAccount,
        groupId: String,
        chatId: String,
        currentAccountGeneration: Long,
        currentRoomGeneration: Long
    ) {
        val result = runCatchingCancellable { withContext(ioDispatcher) {
            withPreparedSession(current) { prepared ->
                gateway.getHistory(prepared, groupId, chatId)
            }
        } }
        if (!isRoomCurrent(
                current,
                groupId,
                chatId,
                currentAccountGeneration,
                currentRoomGeneration
            )
        ) return
        result.fold(
            onSuccess = { page ->
                val snapshot = SteamGroupChatThreadSnapshot(
                    current.steamId, groupId, chatId,
                    mergeSteamGroupMessages(_state.value.thread?.messages.orEmpty(), page.messages),
                    page.moreAvailable, nowMillis()
                )
                updateThread(snapshot)
                _state.value = _state.value.copy(threadLoading = false, failure = null)
                acknowledgeLatest(current, snapshot)
            },
            onFailure = { _state.value = _state.value.copy(threadLoading = false, failure = it.groupChatMessage()) }
        )
    }

    private suspend fun recoverTimedOutSend(
        current: SteamAccount,
        optimistic: SteamGroupChatMessage,
        error: Throwable,
        currentAccountGeneration: Long,
        currentRoomGeneration: Long
    ) {
        if (error !is IOException) {
            updateMessage(optimistic.copy(deliveryState = SteamGroupChatDeliveryState.FAILED))
            return
        }
        updateMessage(optimistic.copy(deliveryState = SteamGroupChatDeliveryState.VERIFYING))
        val history = runCatchingCancellable { withContext(ioDispatcher) {
            withPreparedSession(current) { prepared ->
                gateway.getHistory(prepared, optimistic.groupId, optimistic.chatId)
            }
        } }.getOrNull()
        if (!isRoomCurrent(
                current,
                optimistic.groupId,
                optimistic.chatId,
                currentAccountGeneration,
                currentRoomGeneration
            )
        ) return
        val echo = history?.messages?.firstOrNull { message ->
            message.senderSteamId == current.steamId && message.body.trim() == optimistic.body &&
                kotlin.math.abs(message.timestamp - optimistic.localCreatedAtMillis / 1_000L) <= 45L
        }
        updateMessage(echo?.copy(
            clientMessageId = optimistic.clientMessageId,
            localCreatedAtMillis = optimistic.localCreatedAtMillis,
            deliveryState = SteamGroupChatDeliveryState.SENT
        ) ?: optimistic.copy(deliveryState = SteamGroupChatDeliveryState.FAILED))
    }

    private fun acknowledgeLatest(current: SteamAccount, snapshot: SteamGroupChatThreadSnapshot) {
        val timestamp = snapshot.messages.lastOrNull()?.timestamp?.takeIf { it > 0L } ?: return
        viewModelScope.launch(ioDispatcher) {
            runCatchingCancellable {
                withPreparedSession(current) { prepared ->
                    gateway.acknowledge(prepared, snapshot.groupId, snapshot.chatId, timestamp)
                }
            }
        }
    }

    private fun updateMessage(message: SteamGroupChatMessage) {
        val thread = _state.value.thread ?: return
        updateThread(thread.copy(messages = mergeSteamGroupMessages(thread.messages, listOf(message)), fetchedAt = nowMillis()))
    }

    private fun updateThread(snapshot: SteamGroupChatThreadSnapshot) {
        _state.value = _state.value.copy(thread = snapshot)
        viewModelScope.launch(ioDispatcher) { cache.saveThread(snapshot) }
    }

    private suspend fun <T> withPreparedSession(
        current: SteamAccount,
        block: suspend (SteamAccount) -> T
    ): T {
        val prepared = sessionResolver.resolveOrKeep(current)
        if (account?.id == current.id && account?.steamId == current.steamId) {
            account = prepared
        }
        return block(prepared)
    }

    private fun isCurrent(current: SteamAccount, expectedGeneration: Long): Boolean =
        account?.id == current.id && account?.steamId == current.steamId &&
            accountGeneration == expectedGeneration

    private fun isRoomCurrent(
        current: SteamAccount,
        groupId: String,
        chatId: String,
        expectedAccountGeneration: Long,
        expectedRoomGeneration: Long
    ) = isCurrent(current, expectedAccountGeneration) &&
        roomGeneration == expectedRoomGeneration &&
        _state.value.selectedGroupId == groupId &&
        _state.value.selectedChatId == chatId

    private fun restartPolling() {
        pollingJob?.cancel()
        pollingJob = null
        if (!foreground || account == null) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(
                    if (_state.value.realtimeConnected) REALTIME_RECONCILIATION_MILLIS
                    else LEGACY_POLLING_MILLIS
                )
                if (_state.value.selectedChatId != null) refreshThread() else refreshGroups()
            }
        }
    }

    companion object {
        private const val LEGACY_POLLING_MILLIS = 15_000L
        private const val REALTIME_RECONCILIATION_MILLIS = 60_000L

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            val sourceRepository = takagi.ru.monica.steam.data.SteamAccountSourceRepository
                .get(appContext)
            val resolver = sourceRepository.sessionResolver()
            val accountKeyResolver = { account: SteamAccount ->
                sourceRepository.sessionHandle(account)?.stableKey
                    ?: takagi.ru.monica.steam.network.cm.steamCmAccountKey(account)
            }
            val cm = takagi.ru.monica.steam.network.cm.SteamCmClient(accountKeyResolver)
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SteamGroupChatViewModel(
                        gateway = SteamGroupChatService(cm = cm),
                        cache = SteamGroupChatPreferencesCache(appContext),
                        realtime = takagi.ru.monica.steam.friends.groupchat.data
                            .SteamGroupChatRealtimeService(cm, resolver),
                        sessionResolver = resolver,
                        avatarUploader = SteamGroupAvatarUploader(appContext)
                    ) as T
            }
        }
    }
}

private fun Throwable.groupChatMessage(): String = message?.takeIf(String::isNotBlank)?.take(220)
    ?: "Steam group chat is temporarily unavailable"

private suspend inline fun <T> runCatchingCancellable(
    crossinline block: suspend () -> T
): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}
