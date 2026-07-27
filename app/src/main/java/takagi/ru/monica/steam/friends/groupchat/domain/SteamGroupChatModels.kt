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
    val unreadCount: Int = 0,
    val topMemberSteamIds: List<String> = emptyList()
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

fun steamGroupAvatarUrl(sha: ByteArray): String = sha
    .takeIf { it.size == 20 }
    ?.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    ?.let { "https://avatars.steamstatic.com/${it}_full.jpg" }
    .orEmpty()

internal fun mergeSteamGroupMessages(
    current: List<SteamGroupChatMessage>,
    incoming: List<SteamGroupChatMessage>
): List<SteamGroupChatMessage> {
    val merged = linkedMapOf<String, SteamGroupChatMessage>()
    (current + incoming).forEach { message ->
        val serverKey = "${message.timestamp}:${message.ordinal}:${message.senderSteamId}"
        val existing = merged.values
            .filter { it.stableId == message.stableId || sameServerMessage(it, message) }
            .minByOrNull { candidate ->
                kotlin.math.abs(candidate.timestamp - message.timestamp)
            }
        if (existing != null) merged.remove(existing.stableId)
        val replacement = when {
            message.clientMessageId.isBlank() && existing?.clientMessageId?.isNotBlank() == true ->
                message.copy(
                    clientMessageId = existing.clientMessageId,
                    localCreatedAtMillis = existing.localCreatedAtMillis,
                    deliveryState = SteamGroupChatDeliveryState.SENT
                )
            message.clientMessageId.isNotBlank() && existing?.clientMessageId.isNullOrBlank() &&
                existing != null && sameServerMessage(existing, message) ->
                existing.copy(
                    clientMessageId = message.clientMessageId,
                    localCreatedAtMillis = message.localCreatedAtMillis,
                    deliveryState = message.deliveryState
                )
            else -> message
        }
        merged[replacement.stableId.ifBlank { serverKey }] = replacement
    }
    return merged.values.sortedWith(compareBy<SteamGroupChatMessage> { it.timestamp }.thenBy { it.ordinal })
}

private fun sameServerMessage(
    first: SteamGroupChatMessage,
    second: SteamGroupChatMessage
): Boolean {
    if (first.groupId != second.groupId || first.chatId != second.chatId ||
        first.senderSteamId != second.senderSteamId
    ) return false
    if (first.ordinal != Int.MAX_VALUE && second.ordinal != Int.MAX_VALUE) {
        return first.timestamp == second.timestamp && first.ordinal == second.ordinal
    }

    val local = if (first.ordinal == Int.MAX_VALUE) first else second
    val server = if (first.ordinal == Int.MAX_VALUE) second else first
    if (server.ordinal == Int.MAX_VALUE) return false
    // A server row already bound to another local send belongs to that send, so
    // repeating the same text within the echo window must stay two messages.
    if (server.clientMessageId.isNotBlank() &&
        server.clientMessageId != local.clientMessageId
    ) return false
    if (local.body.trim() != server.body.trim()) return false
    val localTimestamp = local.localCreatedAtMillis
        .takeIf { it > 0L }
        ?.div(1_000L)
        ?: local.timestamp
    if (localTimestamp <= 0L || server.timestamp <= 0L) return false
    return kotlin.math.abs(localTimestamp - server.timestamp) <= OPTIMISTIC_ECHO_WINDOW_SECONDS
}

private const val OPTIMISTIC_ECHO_WINDOW_SECONDS = 90L
