package takagi.ru.monica.steam.friends.chat.actions.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.actions.data.SteamChatMessageActionService
import takagi.ru.monica.steam.friends.chat.actions.domain.SteamChatMessageActionGateway
import takagi.ru.monica.steam.friends.chat.actions.domain.SteamChatReportReason
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.presentation.logSteamChatFailure

enum class SteamChatMessageActionResult { REACTION_ADDED, MESSAGE_REPORTED, FAILED }

class SteamChatMessageActionViewModel(
    private val gateway: SteamChatMessageActionGateway = SteamChatMessageActionService()
) : ViewModel() {
    private var account: SteamAccount? = null
    private val _results = MutableSharedFlow<SteamChatMessageActionResult>(extraBufferCapacity = 1)
    val results: SharedFlow<SteamChatMessageActionResult> = _results.asSharedFlow()

    fun selectAccount(account: SteamAccount?) {
        this.account = account
    }

    fun react(partnerSteamId: String, message: SteamChatMessage, emoticonName: String) {
        execute("reaction", SteamChatMessageActionResult.REACTION_ADDED) { account ->
            gateway.addEmoticonReaction(account, partnerSteamId, message, emoticonName)
        }
    }

    fun report(
        partnerSteamId: String,
        message: SteamChatMessage,
        reason: SteamChatReportReason
    ) {
        execute("report", SteamChatMessageActionResult.MESSAGE_REPORTED) { account ->
            gateway.reportMessage(account, partnerSteamId, message, reason)
        }
    }

    private fun execute(
        operation: String,
        success: SteamChatMessageActionResult,
        block: (SteamAccount) -> Unit
    ) {
        val current = account ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { block(current) } }.fold(
                onSuccess = { _results.emit(success) },
                onFailure = {
                    logSteamChatFailure("message_$operation", it)
                    _results.emit(SteamChatMessageActionResult.FAILED)
                }
            )
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SteamChatMessageActionViewModel() as T
        }
    }
}
