package takagi.ru.monica.steam.friends.groupchat.domain

import kotlinx.serialization.Serializable

@Serializable
data class SteamGroupChatSummary(
    val groupId: String,
    val name: String,
    val tagline: String = "",
    val ownerAccountId: Long = 0L,
    val activeMemberCount: Int = 0,
    val defaultChatId: String,
    val rooms: List<SteamGroupChatRoom> = emptyList(),
    val rank: Int = 0,
    val avatarUrl: String = "",
    val unreadCount: Int = 0
)

@Serializable
data class SteamGroupChatRoom(
    val chatId: String,
    val name: String,
    val sortOrder: Int = 0,
    val lastMessageTimestamp: Long = 0L,
    val lastMessage: String = "",
    val lastSenderSteamId: String = "",
    val lastAcknowledgedTimestamp: Long = 0L,
    val unread: Boolean = false
)

@Serializable
data class SteamGroupChatMessage(
    val groupId: String,
    val chatId: String,
    val senderSteamId: String,
    val timestamp: Long,
    val ordinal: Int,
    val body: String,
    val deleted: Boolean = false,
    val serverEventType: Int = 0,
    val clientMessageId: String = ""
) {
    val stableId: String get() = if (clientMessageId.isNotBlank()) {
        "client:$clientMessageId"
    } else "$groupId:$chatId:$timestamp:$ordinal:$senderSteamId"
}

data class SteamGroupChatMessagePage(
    val messages: List<SteamGroupChatMessage>,
    val moreAvailable: Boolean
)

data class SteamGroupChatCreateRequest(
    val name: String,
    val inviteeSteamIds: List<String>
)

data class SteamGroupChatHistoryBoundary(val timestamp: Long, val ordinal: Int)
