package takagi.ru.monica.steam.friends.chat.presentation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.domain.SteamChatDeliveryState
import takagi.ru.monica.steam.friends.chat.domain.SteamChatGateway
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.mergeSteamChatMessages
import takagi.ru.monica.steam.network.SteamSessionRefreshService

internal class SteamChatOutgoingCoordinator(
    private val scope: CoroutineScope,
    private val gateway: SteamChatGateway,
    private val sessionRefreshService: SteamSessionRefreshService?,
    private val forceSessionRefresh: ((SteamAccount) -> SteamAccount?)?,
    private val persistSession: suspend (SteamAccount) -> Unit,
    private val ioDispatcher: CoroutineDispatcher
) {
    private val jobs = mutableMapOf<String, Job>()

    fun dispatch(
        account: SteamAccount,
        partnerSteamId: String,
        pending: SteamChatMessage,
        verifyBeforeSend: Boolean,
        isCurrent: () -> Boolean,
        onSessionRefreshed: (SteamAccount) -> Unit,
        onUpdate: (SteamChatMessage) -> Unit
    ) {
        if (jobs[pending.clientMessageId]?.isActive == true) return
        val job = scope.launch {
            if (verifyBeforeSend) {
                verify(account, partnerSteamId, pending)?.let {
                    if (isCurrent()) onUpdate(it)
                    return@launch
                }
            }
            if (isCurrent()) onUpdate(pending.copy(deliveryState = SteamChatDeliveryState.SENDING))
            val result = withContext(ioDispatcher) {
                sendSteamChatMessageWithSessionRecovery(
                    gateway = gateway,
                    account = account,
                    partnerSteamId = partnerSteamId,
                    pending = pending,
                    sessionRefreshService = sessionRefreshService,
                    forceSessionRefresh = forceSessionRefresh,
                    onSessionRefreshed = { refreshed ->
                        onSessionRefreshed(refreshed)
                        persistSession(refreshed)
                    }
                )
            }
            if (!isCurrent()) return@launch
            result.getOrNull()?.let { response ->
                onUpdate(response.asConfirmedEchoOf(pending))
                return@launch
            }
            val error = result.exceptionOrNull() ?: return@launch
            logSteamChatFailure("send", error)
            if (error.isTransientSteamChatNetworkFailure()) {
                val verifying = pending.copy(deliveryState = SteamChatDeliveryState.VERIFYING)
                onUpdate(verifying)
                onUpdate(
                    verify(account, partnerSteamId, verifying)
                        ?: verifying.copy(deliveryState = SteamChatDeliveryState.FAILED_RETRYABLE)
                )
            } else {
                onUpdate(pending.copy(deliveryState = SteamChatDeliveryState.FAILED_PERMANENT))
            }
        }
        jobs[pending.clientMessageId] = job
        job.invokeOnCompletion { jobs.remove(pending.clientMessageId, job) }
    }

    private suspend fun verify(
        account: SteamAccount,
        partnerSteamId: String,
        pending: SteamChatMessage
    ): SteamChatMessage? {
        val page = runCatching {
            withContext(ioDispatcher) {
                gateway.fetchMessages(prepareSteamChatSession(account, sessionRefreshService), partnerSteamId)
            }
        }.onFailure { logSteamChatFailure("send_verify", it) }.getOrNull() ?: return null
        return mergeSteamChatMessages(listOf(pending), page.messages)
            .firstOrNull { it.clientMessageId == pending.clientMessageId && it.ordinal != Int.MAX_VALUE }
            ?.copy(deliveryState = SteamChatDeliveryState.SENT)
    }
}

private fun SteamChatMessage.asConfirmedEchoOf(pending: SteamChatMessage): SteamChatMessage = copy(
    deliveryState = SteamChatDeliveryState.SENT,
    clientMessageId = pending.clientMessageId,
    localCreatedAtMillis = pending.localCreatedAtMillis,
    contentSignature = pending.contentSignature,
    replyToStableId = pending.replyToStableId
)
