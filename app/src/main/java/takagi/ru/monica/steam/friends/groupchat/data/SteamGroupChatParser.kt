package takagi.ru.monica.steam.friends.groupchat.data

import takagi.ru.monica.steam.friends.chat.domain.steamId64FromAccountId
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessagePage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRoom
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.friends.groupchat.domain.steamGroupAvatarUrl
import takagi.ru.monica.steam.friends.groupchat.domain.steamGroupEventText
import takagi.ru.monica.steam.network.SteamProtoField
import takagi.ru.monica.steam.network.SteamProtoReader

internal object SteamGroupChatParser {
    fun parseGroups(payload: ByteArray): List<SteamGroupChatSummary> =
        SteamProtoReader(payload).parseAll()
            .filter { it.number == 1 && it.bytes != null }
            .mapNotNull { parseSummaryPair(it.bytes ?: return@mapNotNull null) }
            .sortedByDescending { group -> group.rooms.maxOfOrNull(SteamGroupChatRoom::lastMessageTimestamp) ?: 0L }

    fun parseHistory(payload: ByteArray, groupId: String, chatId: String): SteamGroupChatMessagePage {
        val fields = SteamProtoReader(payload).parseAll()
        val messages = fields.filter { it.number == 1 && it.bytes != null }.mapNotNull { field ->
            val values = SteamProtoReader(field.bytes ?: return@mapNotNull null).parse()
            val sender = values[1]?.asLong?.takeIf { it > 0 } ?: 0L
            val serverMessage = values[5]?.bytes?.let { SteamProtoReader(it).parse() }
            val eventType = serverMessage?.get(1)?.asInt ?: 0
            val eventText = serverMessage?.get(2)?.asString.orEmpty()
            val body = values[3]?.asString.orEmpty().ifBlank {
                if (eventType > 0) steamGroupEventText(eventType, eventText) else ""
            }
            if (body.isBlank()) return@mapNotNull null
            SteamGroupChatMessage(
                groupId = groupId,
                chatId = chatId,
                senderSteamId = sender.takeIf { it > 0 }?.let(::steamId64FromAccountId).orEmpty(),
                timestamp = values[2]?.asLong?.coerceAtLeast(0L) ?: 0L,
                ordinal = values[4]?.asInt ?: 0,
                body = body,
                deleted = values[6]?.asBool == true,
                serverEventType = eventType
            )
        }.distinctBy(SteamGroupChatMessage::stableId)
            .sortedWith(compareBy<SteamGroupChatMessage> { it.timestamp }.thenBy { it.ordinal })
        return SteamGroupChatMessagePage(messages, fields.firstOrNull { it.number == 4 }?.asBool == true)
    }

    fun parseSentMessage(
        payload: ByteArray,
        groupId: String,
        chatId: String,
        senderSteamId: String,
        requestedBody: String
    ): SteamGroupChatMessage {
        val fields = SteamProtoReader(payload).parse()
        return SteamGroupChatMessage(
            groupId = groupId,
            chatId = chatId,
            senderSteamId = senderSteamId,
            timestamp = fields[2]?.asLong?.coerceAtLeast(0L) ?: 0L,
            ordinal = fields[3]?.asInt ?: 0,
            body = fields[1]?.asString.orEmpty().ifBlank { fields[4]?.asString.orEmpty() }.ifBlank { requestedBody }
        )
    }

    fun parseCreatedGroupId(payload: ByteArray): String =
        SteamProtoReader(payload).parse()[1]?.asUnsignedVarintString().orEmpty()

    private fun parseSummaryPair(payload: ByteArray): SteamGroupChatSummary? {
        val pair = SteamProtoReader(payload).parse()
        val userState = pair[1]?.bytes?.let { SteamProtoReader(it).parseAll() }.orEmpty()
        val summary = pair[2]?.bytes?.let { SteamProtoReader(it).parseAll() } ?: return null
        val summaryByField = summary.associateBy(SteamProtoField::number)
        val groupId = summaryByField[1]?.asUnsignedVarintString().orEmpty().takeIf(String::isNotBlank) ?: return null
        val acknowledgements = parseAcknowledgements(userState)
        val rooms = summary.filter { it.number == 6 && it.bytes != null }.mapNotNull { roomField ->
            parseRoom(roomField.bytes ?: return@mapNotNull null, acknowledgements)
        }.sortedBy(SteamGroupChatRoom::sortOrder)
        val defaultChatId = summaryByField[5]?.asUnsignedVarintString().orEmpty()
            .ifBlank { rooms.firstOrNull()?.chatId.orEmpty() }
        if (defaultChatId.isBlank()) return null
        return SteamGroupChatSummary(
            groupId = groupId,
            name = summaryByField[2]?.asString.orEmpty().ifBlank { "Steam group" },
            tagline = summaryByField[8]?.asString.orEmpty(),
            ownerAccountId = summaryByField[9]?.asLong ?: 0L,
            activeMemberCount = summaryByField[3]?.asInt ?: 0,
            defaultChatId = defaultChatId,
            rooms = rooms,
            rank = summaryByField[12]?.asInt ?: 0,
            avatarUrl = summaryByField[21]?.asString.orEmpty().ifBlank {
                summaryByField[11]?.bytes?.let(::steamGroupAvatarUrl).orEmpty()
            },
            unreadCount = rooms.count(SteamGroupChatRoom::unread),
            topMemberSteamIds = parseTopMembers(summary)
        )
    }

    private fun parseTopMembers(summary: List<SteamProtoField>): List<String> =
        summary.filter { it.number == 10 }.flatMap { field ->
            when (field.wireType) {
                0 -> listOf(field.asLong)
                2 -> decodePackedVarints(field.bytes ?: byteArrayOf())
                else -> emptyList()
            }
        }.filter { it > 0L }.distinct().map(::steamId64FromAccountId)

    private fun decodePackedVarints(bytes: ByteArray): List<Long> {
        val values = mutableListOf<Long>()
        var index = 0
        while (index < bytes.size) {
            var value = 0L
            var shift = 0
            while (index < bytes.size && shift < 64) {
                val byte = bytes[index++].toInt() and 0xff
                value = value or ((byte and 0x7f).toLong() shl shift)
                if (byte and 0x80 == 0) break
                shift += 7
            }
            values += value
        }
        return values
    }

    private fun parseAcknowledgements(userState: List<SteamProtoField>): Map<String, Long> =
        userState.filter { it.number == 3 && it.bytes != null }.mapNotNull { roomState ->
            val fields = SteamProtoReader(roomState.bytes ?: return@mapNotNull null).parse()
            val chatId = fields[1]?.asUnsignedVarintString().orEmpty().takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            chatId to (fields[3]?.asLong?.coerceAtLeast(0L) ?: 0L)
        }.toMap()

    private fun parseRoom(payload: ByteArray, acknowledgements: Map<String, Long>): SteamGroupChatRoom? {
        val fields = SteamProtoReader(payload).parse()
        val chatId = fields[1]?.asUnsignedVarintString().orEmpty().takeIf(String::isNotBlank) ?: return null
        val lastTimestamp = fields[5]?.asLong?.coerceAtLeast(0L) ?: 0L
        val ack = acknowledgements[chatId] ?: 0L
        return SteamGroupChatRoom(
            chatId = chatId,
            name = fields[2]?.asString.orEmpty().ifBlank { "Chat" },
            sortOrder = fields[6]?.asInt ?: 0,
            lastMessageTimestamp = lastTimestamp,
            lastMessage = fields[7]?.asString.orEmpty(),
            lastSenderSteamId = fields[8]?.asLong?.takeIf { it > 0 }?.let(::steamId64FromAccountId).orEmpty(),
            lastAcknowledgedTimestamp = ack,
            unread = lastTimestamp > ack
        )
    }

    private fun SteamProtoField?.asUnsignedVarintString(): String = this?.let {
        java.lang.Long.toUnsignedString(it.asLong)
    }.orEmpty()
}
