package takagi.ru.monica.steam.store.share.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.friends.chat.data.SteamChatPreferencesCache
import takagi.ru.monica.steam.friends.chat.data.SteamChatRoomOutbox
import takagi.ru.monica.steam.friends.chat.data.SteamFriendChatService
import takagi.ru.monica.steam.friends.chat.domain.SteamChatDeliveryState
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSession
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSessionsSnapshot
import takagi.ru.monica.steam.friends.chat.domain.SteamChatThreadSnapshot
import takagi.ru.monica.steam.friends.chat.domain.mergeSteamChatMessages
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatOutgoingCoordinator
import takagi.ru.monica.steam.friends.chat.presentation.newPendingSteamChatMessage
import takagi.ru.monica.steam.network.cm.SteamCmClient
import takagi.ru.monica.steam.network.cm.steamCmAccountKey
import takagi.ru.monica.steam.store.share.domain.SteamStoreGameShare

enum class SteamStoreGameShareError {
    ACCOUNT_REQUIRED,
    SEND_FAILED
}

data class SteamStoreGameShareUiState(
    val sendingToSteamId: String? = null,
    val sentToSteamId: String? = null,
    val error: SteamStoreGameShareError? = null
)

class SteamStoreGameShareViewModel internal constructor(
    private val context: Context,
    private val sourceRepository: SteamAccountSourceRepository
) : ViewModel() {
    private val cache = SteamChatPreferencesCache(context)
    private val outbox = SteamChatRoomOutbox.from(context)
    private val sessionResolver = sourceRepository.sessionResolver()
    private val accountKeyResolver = { account: takagi.ru.monica.steam.data.SteamAccount ->
        sourceRepository.sessionHandle(account)?.stableKey ?: steamCmAccountKey(account)
    }
    private val gateway = SteamFriendChatService(cm = SteamCmClient(accountKeyResolver))
    private val outgoingCoordinator = SteamChatOutgoingCoordinator(
        scope = viewModelScope,
        gateway = gateway,
        sessionResolver = sessionResolver,
        ioDispatcher = Dispatchers.IO,
        outbox = outbox
    )
    private val _uiState = MutableStateFlow(SteamStoreGameShareUiState())
    val uiState: StateFlow<SteamStoreGameShareUiState> = _uiState.asStateFlow()

    fun sendToFriend(friendSteamId: String, share: SteamStoreGameShare) {
        if (_uiState.value.sendingToSteamId != null) return
        val sourceState = sourceRepository.state.value
        val account = sourceState.accounts.firstOrNull { it.id == sourceState.selectedAccountId }
            ?: sourceState.accounts.firstOrNull()
        if (account == null) {
            _uiState.value = SteamStoreGameShareUiState(
                error = SteamStoreGameShareError.ACCOUNT_REQUIRED
            )
            return
        }
        _uiState.value = SteamStoreGameShareUiState(sendingToSteamId = friendSteamId)
        val nowMillis = System.currentTimeMillis()
        val pending = newPendingSteamChatMessage(
            accountSteamId = account.steamId,
            partnerSteamId = friendSteamId,
            body = share.messageBody,
            timestamp = nowMillis / 1_000L,
            clientMessageId = UUID.randomUUID().toString()
        )
        persistMessageAsync(account.steamId, friendSteamId, pending)
        outgoingCoordinator.dispatch(
            account = account,
            partnerSteamId = friendSteamId,
            accountKey = accountKeyResolver(account),
            pending = pending,
            verifyBeforeSend = false,
            isCurrent = { true },
            onSessionRefreshed = {},
            onUpdate = { message ->
                persistMessageAsync(account.steamId, friendSteamId, message)
                _uiState.value = when (message.deliveryState) {
                    SteamChatDeliveryState.SENT -> SteamStoreGameShareUiState(
                        sentToSteamId = friendSteamId
                    )
                    SteamChatDeliveryState.FAILED_RETRYABLE,
                    SteamChatDeliveryState.FAILED_PERMANENT -> SteamStoreGameShareUiState(
                        error = SteamStoreGameShareError.SEND_FAILED
                    )
                    else -> SteamStoreGameShareUiState(sendingToSteamId = friendSteamId)
                }
            }
        )
    }

    private fun persistMessageAsync(
        accountSteamId: String,
        partnerSteamId: String,
        message: takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            persistMessage(accountSteamId, partnerSteamId, message)
        }
    }

    fun consumeResult() {
        _uiState.value = SteamStoreGameShareUiState()
    }

    private fun persistMessage(
        accountSteamId: String,
        partnerSteamId: String,
        message: takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
    ) {
        val nowMillis = System.currentTimeMillis()
        val previousThread = cache.loadThread(accountSteamId, partnerSteamId)
        cache.saveThread(
            accountSteamId,
            partnerSteamId,
            (previousThread ?: SteamChatThreadSnapshot(
                accountSteamId = accountSteamId,
                partnerSteamId = partnerSteamId,
                messages = emptyList(),
                moreAvailable = false,
                fetchedAt = nowMillis
            )).copy(
                messages = mergeSteamChatMessages(previousThread?.messages.orEmpty(), listOf(message)),
                fetchedAt = nowMillis
            )
        )
        val previousSessions = cache.loadSessions(accountSteamId)
        val existing = previousSessions?.sessions?.firstOrNull {
            it.partnerSteamId == partnerSteamId
        }
        val updated = (existing ?: SteamChatSession(partnerSteamId)).copy(
            lastMessageTimestamp = maxOf(existing?.lastMessageTimestamp ?: 0L, message.timestamp)
        )
        cache.saveSessions(
            accountSteamId,
            (previousSessions ?: SteamChatSessionsSnapshot(accountSteamId, emptyList(), nowMillis)).copy(
                sessions = (previousSessions?.sessions.orEmpty().filterNot {
                    it.partnerSteamId == partnerSteamId
                } + updated).sortedByDescending(SteamChatSession::lastMessageTimestamp),
                fetchedAt = nowMillis
            )
        )
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SteamStoreGameShareViewModel(
                        context = appContext,
                        sourceRepository = SteamAccountSourceRepository.get(appContext)
                    ) as T
            }
        }
    }
}
