package takagi.ru.monica.steam.friends.groupchat.data

import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatCreateRequest
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatGateway
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatHistoryBoundary
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessagePage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmClient
import takagi.ru.monica.steam.network.cm.SteamCmGateway

class SteamGroupChatService(
    private val cm: SteamCmGateway = SteamCmClient()
) : SteamGroupChatGateway {
    override fun getMyGroups(account: SteamAccount): List<SteamGroupChatSummary> =
        SteamGroupChatParser.parseGroups(call(account, "GetMyChatRoomGroups", SteamProtoWriter()))

    override fun getHistory(
        account: SteamAccount,
        groupId: String,
        chatId: String,
        before: SteamGroupChatHistoryBoundary?
    ): SteamGroupChatMessagePage = SteamGroupChatParser.parseHistory(
        payload = call(account, "GetMessageHistory", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeUint64(2, chatId.requireUnsignedId("chat"))
            before?.let {
                writeVarint(3, it.timestamp)
                writeVarint(4, it.ordinal.toLong())
            }
            writeVarint(7, 50L)
        }),
        groupId = groupId,
        chatId = chatId
    )

    override fun sendMessage(
        account: SteamAccount,
        groupId: String,
        chatId: String,
        body: String
    ): SteamGroupChatMessage {
        val normalized = body.trim()
        require(normalized.isNotBlank()) { "Steam group message is empty" }
        val response = call(account, "SendChatMessage", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeUint64(2, chatId.requireUnsignedId("chat"))
            writeString(3, normalized)
            writeBool(4, true)
        })
        return SteamGroupChatParser.parseSentMessage(response, groupId, chatId, account.steamId, normalized)
    }

    override fun createGroup(account: SteamAccount, request: SteamGroupChatCreateRequest): String {
        val name = request.name.trim()
        require(name.length in 1..64) { "Steam group name must contain 1-64 characters" }
        val response = call(account, "CreateChatRoomGroup", SteamProtoWriter().apply {
            writeString(3, name)
            request.inviteeSteamIds.distinct().forEach { writeFixed64(4, it.requireSteamId64()) }
        })
        return SteamGroupChatParser.parseCreatedGroupId(response)
            .takeIf(String::isNotBlank) ?: error("Steam did not return the created group ID")
    }

    override fun inviteFriend(account: SteamAccount, groupId: String, chatId: String, steamId: String) {
        call(account, "InviteFriendToChatRoomGroup", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeFixed64(2, steamId.requireSteamId64())
            writeUint64(3, chatId.requireUnsignedId("chat"))
        })
    }

    override fun updateGroup(
        account: SteamAccount,
        groupId: String,
        name: String,
        tagline: String
    ) {
        val normalizedName = name.trim()
        require(normalizedName.length in 1..64) { "Steam group name must contain 1-64 characters" }
        call(account, "RenameChatRoomGroup", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeString(2, normalizedName)
        })
        call(account, "SetChatRoomGroupTagline", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeString(2, tagline.trim().take(128))
        })
    }

    override fun updateGroupAvatar(account: SteamAccount, groupId: String, avatarSha: ByteArray) {
        require(avatarSha.size == 20) { "Steam group avatar SHA must contain 20 bytes" }
        call(account, "SetChatRoomGroupAvatar", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeBytes(2, avatarSha)
        })
    }

    override fun acknowledge(account: SteamAccount, groupId: String, chatId: String, timestamp: Long) {
        if (timestamp <= 0L) return
        call(account, "AckChatMessage", SteamProtoWriter().apply {
            writeUint64(1, groupId.requireUnsignedId("group"))
            writeUint64(2, chatId.requireUnsignedId("chat"))
            writeVarint(3, timestamp)
        })
    }

    private fun call(account: SteamAccount, method: String, request: SteamProtoWriter): ByteArray =
        cm.callService(account, "ChatRoom.$method#1", request.toByteArray())

    private fun String.requireSteamId64(): Long {
        require(matches(Regex("7656119\\d{10}"))) { "Valid Steam ID required" }
        return toLong()
    }

    private fun String.requireUnsignedId(label: String): String = apply {
        require(toBigIntegerOrNull()?.signum()?.let { it >= 0 } == true) { "Valid Steam $label ID required" }
    }
}
