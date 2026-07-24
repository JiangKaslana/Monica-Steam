package takagi.ru.monica.steam.friends.chat.presentation

import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.domain.SteamChatGateway
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.network.SteamSessionRefreshService

/** Sends a chat message and performs one bounded recovery for an expired session. */
internal suspend fun sendSteamChatMessageWithSessionRecovery(
    gateway: SteamChatGateway,
    account: SteamAccount,
    partnerSteamId: String,
    pending: SteamChatMessage,
    sessionRefreshService: SteamSessionRefreshService?,
    forceSessionRefresh: ((SteamAccount) -> SteamAccount?)?,
    onSessionRefreshed: suspend (SteamAccount) -> Unit = {}
): Result<SteamChatMessage> {
    val preparedAccount = prepareSteamChatSession(account, sessionRefreshService)
    persistSessionIfChanged(account, preparedAccount, onSessionRefreshed)
    val firstAttempt = runCatching {
        gateway.sendMessage(
            account = preparedAccount,
            partnerSteamId = partnerSteamId,
            body = pending.body,
            clientMessageId = pending.clientMessageId
        )
    }
    val firstError = firstAttempt.exceptionOrNull()
    if (firstError == null || !firstError.requiresSteamChatSessionRefresh()) {
        if (firstError?.isTransientSteamChatNetworkFailure() != true) return firstAttempt
        // client_message_id makes this retry safe when the first request was
        // accepted but its response was lost in transit.
        logSteamChatFailure("send_network_retry", firstError)
        return runCatching {
            gateway.sendMessage(
                account = preparedAccount,
                partnerSteamId = partnerSteamId,
                body = pending.body,
                clientMessageId = pending.clientMessageId
            )
        }
    }

    logSteamChatFailure("send_session_refresh", firstError)
    val refreshedAccount = runCatching {
        forceSessionRefresh?.invoke(account) ?: refreshSteamChatSessionForRetry(
            account,
            sessionRefreshService
        )
    }.getOrNull() ?: return firstAttempt
    persistSessionIfChanged(account, refreshedAccount, onSessionRefreshed)

    return runCatching {
        gateway.sendMessage(
            account = refreshedAccount,
            partnerSteamId = partnerSteamId,
            body = pending.body,
            clientMessageId = pending.clientMessageId
        )
    }
}

private suspend fun persistSessionIfChanged(
    previous: SteamAccount,
    current: SteamAccount,
    persist: suspend (SteamAccount) -> Unit
) {
    if (previous.accessToken == current.accessToken &&
        previous.refreshToken == current.refreshToken &&
        previous.steamLoginSecure == current.steamLoginSecure
    ) {
        return
    }
    runCatching { persist(current) }
        .onFailure { logSteamChatFailure("session_persist", it) }
}

private fun refreshSteamChatSessionForRetry(
    account: SteamAccount,
    service: SteamSessionRefreshService?
): SteamAccount? {
    val refreshService = service ?: return null
    val refreshToken = account.refreshToken?.takeIf(String::isNotBlank) ?: return null
    val result = refreshService.refresh(account.steamId, refreshToken) ?: return null
    return account.copy(
        accessToken = result.accessToken,
        refreshToken = result.refreshToken ?: account.refreshToken,
        steamLoginSecure = "${account.steamId}||${result.accessToken}"
    )
}
