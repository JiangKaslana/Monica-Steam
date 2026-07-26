package takagi.ru.monica.steam.friends.chat.data

import takagi.ru.monica.steam.friends.chat.domain.SteamChatDeliveryState
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.domain.SteamChatRealtimeEvent
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.cm.SteamCmEnvelope
import takagi.ru.monica.steam.network.cm.SteamCmProtocol

internal object SteamFriendChatRealtimeParser {
    fun parse(
        envelope: SteamCmEnvelope,
        accountSteamId: String
    ): SteamChatRealtimeEvent? {
        if (envelope.eMsg !in SUPPORTED_SERVICE_MESSAGES) return null
        return when (envelope.header.targetJobName?.substringBefore('#')) {
            INCOMING_MESSAGE_METHOD -> parseIncoming(envelope, accountSteamId)
            ACK_MESSAGE_METHOD -> parseAcknowledged(envelope)
            else -> null
        }
    }

    private fun parseIncoming(
        envelope: SteamCmEnvelope,
        accountSteamId: String
    ): SteamChatRealtimeEvent? {
        val fields = runCatching { SteamProtoReader(envelope.body).parse() }.getOrNull()
            ?: return null
        val partnerSteamId = fields[1]?.asFixed64UnsignedString
            ?.takeIf(::isSteamId64) ?: return null
        val localEcho = fields[7]?.asBool == true
        return when (fields[2]?.asInt ?: CHAT_ENTRY_TYPE_INVALID) {
            in MESSAGE_ENTRY_TYPES -> {
                val body = fields[4]?.asString
                    ?.takeIf(String::isNotBlank)
                    ?: fields[8]?.asString?.takeIf(String::isNotBlank)
                    ?: return null
                SteamChatRealtimeEvent.Message(
                    SteamChatMessage(
                        partnerSteamId = partnerSteamId,
                        senderSteamId = if (localEcho) accountSteamId else partnerSteamId,
                        timestamp = fields[5]?.asFixed32UnsignedLong ?: 0L,
                        ordinal = fields[6]?.asInt ?: 0,
                        body = body,
                        deliveryState = SteamChatDeliveryState.SENT
                    )
                )
            }
            CHAT_ENTRY_TYPE_TYPING -> SteamChatRealtimeEvent.Typing(partnerSteamId, localEcho)
            CHAT_ENTRY_TYPE_LEFT_CONVERSATION ->
                SteamChatRealtimeEvent.ConversationLeft(partnerSteamId, localEcho)
            else -> null
        }
    }

    private fun parseAcknowledged(envelope: SteamCmEnvelope): SteamChatRealtimeEvent? {
        val fields = runCatching { SteamProtoReader(envelope.body).parse() }.getOrNull()
            ?: return null
        val partnerSteamId = fields[1]?.asFixed64UnsignedString
            ?.takeIf(::isSteamId64) ?: return null
        val timestamp = fields[2]?.asLong?.coerceAtLeast(0L) ?: 0L
        return SteamChatRealtimeEvent.Acknowledged(partnerSteamId, timestamp)
    }

    private fun isSteamId64(value: String): Boolean = value.matches(STEAM_ID_PATTERN)

    private const val INCOMING_MESSAGE_METHOD = "FriendMessagesClient.IncomingMessage"
    private const val ACK_MESSAGE_METHOD = "FriendMessagesClient.NotifyAckMessageEcho"
    private const val CHAT_ENTRY_TYPE_INVALID = 0
    private const val CHAT_ENTRY_TYPE_MESSAGE = 1
    private const val CHAT_ENTRY_TYPE_TYPING = 2
    private const val CHAT_ENTRY_TYPE_INVITE_GAME = 3
    private const val CHAT_ENTRY_TYPE_LEFT_CONVERSATION = 6
    private const val CHAT_ENTRY_TYPE_HISTORICAL_CHAT = 11
    private const val CHAT_ENTRY_TYPE_LINK_BLOCKED = 14
    private val MESSAGE_ENTRY_TYPES = setOf(
        CHAT_ENTRY_TYPE_MESSAGE,
        CHAT_ENTRY_TYPE_INVITE_GAME,
        CHAT_ENTRY_TYPE_HISTORICAL_CHAT,
        CHAT_ENTRY_TYPE_LINK_BLOCKED
    )
    private val SUPPORTED_SERVICE_MESSAGES = setOf(
        SteamCmProtocol.EMSG_SERVICE_METHOD,
        SteamCmProtocol.EMSG_SERVICE_METHOD_SEND_TO_CLIENT
    )
    private val STEAM_ID_PATTERN = Regex("7656119\\d{10}")
}
