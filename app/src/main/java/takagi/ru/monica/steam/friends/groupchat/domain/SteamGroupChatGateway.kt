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
    fun updateGroup(account: SteamAccount, groupId: String, name: String, tagline: String) {
        throw UnsupportedOperationException("Updating Steam group metadata is not supported")
    }
    fun updateGroupAvatar(account: SteamAccount, groupId: String, avatarSha: ByteArray) {
        throw UnsupportedOperationException("Updating Steam group avatar is not supported")
    }
    fun createChannel(
        account: SteamAccount,
        groupId: String,
        request: SteamGroupChatChannelCreateRequest
    ): SteamGroupChatRoom {
        throw UnsupportedOperationException("Creating Steam group channels is not supported")
    }
    fun deleteChannel(account: SteamAccount, groupId: String, chatId: String) {
        throw UnsupportedOperationException("Deleting Steam group channels is not supported")
    }
    fun renameChannel(account: SteamAccount, groupId: String, chatId: String, name: String) {
        throw UnsupportedOperationException("Renaming Steam group channels is not supported")
    }
    fun reorderChannel(account: SteamAccount, groupId: String, chatId: String, moveAfterChatId: String?) {
        throw UnsupportedOperationException("Reordering Steam group channels is not supported")
    }
    fun joinVoiceChat(account: SteamAccount, groupId: String, chatId: String): SteamGroupChatVoiceSession {
        throw UnsupportedOperationException("Joining Steam voice channels is not supported")
    }
    fun leaveVoiceChat(account: SteamAccount, groupId: String, chatId: String) {
        throw UnsupportedOperationException("Leaving Steam voice channels is not supported")
    }
    fun acknowledge(account: SteamAccount, groupId: String, chatId: String, timestamp: Long)
}
