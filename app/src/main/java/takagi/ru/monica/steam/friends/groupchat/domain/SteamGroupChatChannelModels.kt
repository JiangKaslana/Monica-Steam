package takagi.ru.monica.steam.friends.groupchat.domain

/** Request used by Steam's CreateChatRoom endpoint. */
data class SteamGroupChatChannelCreateRequest(
    val name: String,
    val allowVoice: Boolean
)

data class SteamGroupChatVoiceSession(
    val groupId: String,
    val chatId: String,
    val voiceChatId: String
)
