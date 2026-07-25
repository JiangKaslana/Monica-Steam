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
    val clientMessageId: String = "",
    val localCreatedAtMillis: Long = 0L,
    val deliveryState: SteamGroupChatDeliveryState = SteamGroupChatDeliveryState.SENT
) {
    val stableId: String get() = if (clientMessageId.isNotBlank()) {
        "client:$clientMessageId"
    } else "$groupId:$chatId:$timestamp:$ordinal:$senderSteamId"
}

@Serializable
enum class SteamGroupChatDeliveryState { QUEUED, SENDING, VERIFYING, SENT, FAILED }

@Serializable
data class SteamGroupChatGroupsSnapshot(
    val accountSteamId: String,
    val groups: List<SteamGroupChatSummary>,
    val fetchedAt: Long
)

@Serializable
data class SteamGroupChatThreadSnapshot(
    val accountSteamId: String,
    val groupId: String,
    val chatId: String,
    val messages: List<SteamGroupChatMessage>,
    val moreAvailable: Boolean,
    val fetchedAt: Long
)

data class SteamGroupChatMessagePage(
    val messages: List<SteamGroupChatMessage>,
    val moreAvailable: Boolean
)

data class SteamGroupChatCreateRequest(
    val name: String,
    val inviteeSteamIds: List<String>
)

data class SteamGroupChatHistoryBoundary(val timestamp: Long, val ordinal: Int)

internal fun mergeSteamGroupMessages(
    current: List<SteamGroupChatMessage>,
    incoming: List<SteamGroupChatMessage>
): List<SteamGroupChatMessage> {
    val merged = linkedMapOf<String, SteamGroupChatMessage>()
    (current + incoming).forEach { message ->
        val serverKey = "${message.timestamp}:${message.ordinal}:${message.senderSteamId}"
        val existing = merged.values.firstOrNull {
            it.stableId == message.stableId ||
                (message.ordinal != Int.MAX_VALUE && it.ordinal != Int.MAX_VALUE &&
                    it.timestamp == message.timestamp && it.ordinal == message.ordinal &&
                    it.senderSteamId == message.senderSteamId)
        }
        if (existing != null) merged.remove(existing.stableId)
        val replacement = if (message.clientMessageId.isBlank() && existing?.clientMessageId?.isNotBlank() == true) {
            message.copy(
                clientMessageId = existing.clientMessageId,
                localCreatedAtMillis = existing.localCreatedAtMillis,
                deliveryState = SteamGroupChatDeliveryState.SENT
            )
        } else message
        merged[replacement.stableId.ifBlank { serverKey }] = replacement
    }
    return merged.values.sortedWith(compareBy<SteamGroupChatMessage> { it.timestamp }.thenBy { it.ordinal })
}
