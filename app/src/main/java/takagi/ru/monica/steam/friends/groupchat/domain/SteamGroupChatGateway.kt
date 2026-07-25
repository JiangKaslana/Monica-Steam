package takagi.ru.monica.steam.friends.groupchat.domain

import takagi.ru.monica.steam.data.SteamAccount

interface SteamGroupChatGateway {
    fun getMyGroups(account: SteamAccount): List<SteamGroupChatSummary>
    fun getHistory(
        account: SteamAccount,
        groupId: String,
        chatId: String,
        before: SteamGroupChatHistoryBoundary? = null
    ): SteamGroupChatMessagePage
    fun sendMessage(account: SteamAccount, groupId: String, chatId: String, body: String): SteamGroupChatMessage
    fun createGroup(account: SteamAccount, request: SteamGroupChatCreateRequest): String
    fun inviteFriend(account: SteamAccount, groupId: String, chatId: String, steamId: String)
    fun acknowledge(account: SteamAccount, groupId: String, chatId: String, timestamp: Long)
}
