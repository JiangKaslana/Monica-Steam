package takagi.ru.monica.steam.friends.groupchat.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatCache
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatPreferencesCache
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatService
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatCreateRequest
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatDeliveryState
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatGateway
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatGroupsSnapshot
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatHistoryBoundary
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatThreadSnapshot
import takagi.ru.monica.steam.friends.groupchat.domain.mergeSteamGroupMessages

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
    val createdGroupId: String? = null,
    val failure: String? = null
)

class SteamGroupChatViewModel(
    private val gateway: SteamGroupChatGateway,
    private val cache: SteamGroupChatCache,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newClientId: () -> String = { UUID.randomUUID().toString() }
) : ViewModel() {
    private val _state = MutableStateFlow(SteamGroupChatUiState())
    val state: StateFlow<SteamGroupChatUiState> = _state.asStateFlow()
    private var account: SteamAccount? = null
    private var generation = 0L
    private var foreground = false
    private var pollingJob: Job? = null

    fun selectAccount(account: SteamAccount?) {
        if (this.account?.id == account?.id && this.account?.steamId == account?.steamId) {
            this.account = account
            restartPolling()
            return
        }
        this.account = account
        restartPolling()
        generation++
        if (account == null) {
            _state.value = SteamGroupChatUiState(failure = "Steam account required")
            return
        }
        val currentGeneration = generation
        _state.value = SteamGroupChatUiState(accountSteamId = account.steamId, groupsLoading = true)
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
        fetchGroups(current, generation)
    }

    fun openRoom(groupId: String, chatId: String) {
        val current = account ?: return
        if (groupId.isBlank() || chatId.isBlank()) return
        val currentGeneration = ++generation
        _state.value = _state.value.copy(
            selectedGroupId = groupId,
            selectedChatId = chatId,
            thread = null,
            threadLoading = true,
            failure = null
        )
        viewModelScope.launch {
            val cached = withContext(ioDispatcher) { cache.loadThread(current.steamId, groupId, chatId) }
            if (!isRoomCurrent(current, groupId, chatId, currentGeneration)) return@launch
            _state.value = _state.value.copy(thread = cached, threadLoading = cached == null)
            fetchThread(current, groupId, chatId, currentGeneration)
        }
    }

    fun closeRoom() {
        generation++
        _state.value = _state.value.copy(selectedGroupId = null, selectedChatId = null, thread = null, threadLoading = false)
    }

    fun refreshThread() {
        val current = account ?: return
        val groupId = _state.value.selectedGroupId ?: return
        val chatId = _state.value.selectedChatId ?: return
        _state.value = _state.value.copy(threadLoading = _state.value.thread == null, failure = null)
        viewModelScope.launch { fetchThread(current, groupId, chatId, generation) }
    }

    fun loadOlder() {
        val current = account ?: return
        val thread = _state.value.thread ?: return
        if (!thread.moreAvailable || _state.value.loadingOlder) return
        val oldest = thread.messages.firstOrNull() ?: return
        val currentGeneration = generation
        _state.value = _state.value.copy(loadingOlder = true)
        viewModelScope.launch {
            val result = runCatching { withContext(ioDispatcher) {
                gateway.getHistory(
                    current,
                    thread.groupId,
                    thread.chatId,
                    SteamGroupChatHistoryBoundary(oldest.timestamp, oldest.ordinal)
                )
            } }
            if (!isRoomCurrent(current, thread.groupId, thread.chatId, currentGeneration)) return@launch
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
        val currentGeneration = generation
        viewModelScope.launch {
            updateMessage(optimistic.copy(deliveryState = SteamGroupChatDeliveryState.SENDING))
            val result = runCatching { withContext(ioDispatcher) {
                gateway.sendMessage(current, thread.groupId, thread.chatId, normalized)
            } }
            if (!isRoomCurrent(current, thread.groupId, thread.chatId, currentGeneration)) return@launch
            result.fold(
                onSuccess = { sent -> updateMessage(sent.copy(
                    clientMessageId = optimistic.clientMessageId,
                    localCreatedAtMillis = optimistic.localCreatedAtMillis,
                    deliveryState = SteamGroupChatDeliveryState.SENT
                )) },
                onFailure = { error -> recoverTimedOutSend(current, optimistic, error, currentGeneration) }
            )
        }
    }

    fun createGroup(name: String, inviteeSteamIds: List<String>) {
        val current = account ?: return
        if (_state.value.creatingGroup) return
        _state.value = _state.value.copy(creatingGroup = true, createdGroupId = null, failure = null)
        viewModelScope.launch {
            val result = runCatching { withContext(ioDispatcher) {
                gateway.createGroup(current, SteamGroupChatCreateRequest(name, inviteeSteamIds))
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
            runCatching { withContext(ioDispatcher) { gateway.inviteFriend(current, groupId, chatId, steamId) } }
                .onFailure { _state.value = _state.value.copy(failure = it.groupChatMessage()) }
        }
    }

    fun clearCreatedGroup() { _state.value = _state.value.copy(createdGroupId = null) }
    fun clearFailure() { _state.value = _state.value.copy(failure = null) }

    fun setForeground(active: Boolean) {
        if (foreground == active) return
        foreground = active
        restartPolling()
    }

    private fun fetchGroups(current: SteamAccount, currentGeneration: Long) {
        viewModelScope.launch {
            val result = runCatching { withContext(ioDispatcher) { gateway.getMyGroups(current) } }
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

    private suspend fun fetchThread(current: SteamAccount, groupId: String, chatId: String, currentGeneration: Long) {
        val result = runCatching { withContext(ioDispatcher) { gateway.getHistory(current, groupId, chatId) } }
        if (!isRoomCurrent(current, groupId, chatId, currentGeneration)) return
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
        currentGeneration: Long
    ) {
        if (error !is IOException) {
            updateMessage(optimistic.copy(deliveryState = SteamGroupChatDeliveryState.FAILED))
            return
        }
        updateMessage(optimistic.copy(deliveryState = SteamGroupChatDeliveryState.VERIFYING))
        val history = runCatching { withContext(ioDispatcher) {
            gateway.getHistory(current, optimistic.groupId, optimistic.chatId)
        } }.getOrNull()
        if (!isRoomCurrent(current, optimistic.groupId, optimistic.chatId, currentGeneration)) return
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
            runCatching { gateway.acknowledge(current, snapshot.groupId, snapshot.chatId, timestamp) }
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

    private fun isCurrent(current: SteamAccount, expectedGeneration: Long): Boolean =
        account?.id == current.id && generation == expectedGeneration

    private fun isRoomCurrent(current: SteamAccount, groupId: String, chatId: String, expectedGeneration: Long) =
        isCurrent(current, expectedGeneration) && _state.value.selectedGroupId == groupId && _state.value.selectedChatId == chatId

    private fun restartPolling() {
        pollingJob?.cancel()
        pollingJob = null
        if (!foreground || account == null) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                if (_state.value.selectedChatId != null) refreshThread() else refreshGroups()
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SteamGroupChatViewModel(
                gateway = SteamGroupChatService(),
                cache = SteamGroupChatPreferencesCache(context.applicationContext)
            ) as T
        }
    }
}

private fun Throwable.groupChatMessage(): String = message?.takeIf(String::isNotBlank)?.take(220)
    ?: "Steam group chat is temporarily unavailable"
