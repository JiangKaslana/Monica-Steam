package takagi.ru.monica.steam.friends.groupchat.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatChannelCreateRequest
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmGateway

class SteamGroupChatServiceTest {
    @Test
    fun updatesOfficialGroupAvatarWithGroupIdAndSha() {
        val cm = RecordingCmGateway()
        val sha = ByteArray(20) { it.toByte() }

        SteamGroupChatService(cm).updateGroupAvatar(account(), "8001", sha)

        assertEquals("ChatRoom.SetChatRoomGroupAvatar#1", cm.method)
        val fields = SteamProtoReader(cm.request).parse()
        assertEquals("8001", java.lang.Long.toUnsignedString(fields.getValue(1).asLong))
        assertArrayEquals(sha, fields.getValue(2).bytes)
    }

    @Test
    fun createsTextAndVoiceChannelsWithOfficialFields() {
        val cm = RecordingCmGateway(
            response = SteamProtoWriter().apply {
                writeMessage(1, SteamProtoWriter().apply {
                    writeUint64(1, "9002")
                    writeString(2, "Voice")
                    writeBool(3, true)
                    writeVarint(6, 2L)
                })
            }.toByteArray()
        )

        val room = SteamGroupChatService(cm).createChannel(
            account(),
            "8001",
            SteamGroupChatChannelCreateRequest("Voice", allowVoice = true)
        )

        assertEquals("ChatRoom.CreateChatRoom#1", cm.method)
        val fields = SteamProtoReader(cm.request).parse()
        assertEquals("8001", java.lang.Long.toUnsignedString(fields.getValue(1).asLong))
        assertEquals("Voice", fields.getValue(2).asString)
        assertEquals(true, fields.getValue(3).asBool)
        assertEquals("9002", room.chatId)
        assertTrue(room.voiceAllowed)
    }

    @Test
    fun renamesReordersAndDeletesChannelWithOfficialMethods() {
        val cm = RecordingCmGateway()
        val service = SteamGroupChatService(cm)

        service.renameChannel(account(), "8001", "9001", "Lobby")
        assertEquals("ChatRoom.RenameChatRoom#1", cm.method)
        assertEquals("Lobby", SteamProtoReader(cm.request).parse().getValue(3).asString)

        service.reorderChannel(account(), "8001", "9001", "9002")
        assertEquals("ChatRoom.ReorderChatRoom#1", cm.method)
        assertEquals("9002", java.lang.Long.toUnsignedString(SteamProtoReader(cm.request).parse().getValue(3).asLong))

        service.deleteChannel(account(), "8001", "9001")
        assertEquals("ChatRoom.DeleteChatRoom#1", cm.method)
    }

    @Test
    fun joinsAndLeavesOfficialVoiceChat() {
        val cm = RecordingCmGateway(
            response = SteamProtoWriter().apply { writeUint64(1, "7001") }.toByteArray()
        )
        val service = SteamGroupChatService(cm)

        val session = service.joinVoiceChat(account(), "8001", "9002")
        assertEquals("ChatRoom.JoinVoiceChat#1", cm.method)
        assertEquals("7001", session.voiceChatId)

        service.leaveVoiceChat(account(), "8001", "9002")
        assertEquals("ChatRoom.LeaveVoiceChat#1", cm.method)
    }

    private class RecordingCmGateway(
        private val response: ByteArray = byteArrayOf()
    ) : SteamCmGateway {
        var method = ""
        var request = byteArrayOf()
        override fun callService(account: SteamAccount, method: String, request: ByteArray): ByteArray {
            this.method = method
            this.request = request
            return response
        }
        override fun exchangeClientMessage(
            account: SteamAccount,
            requestEMsg: Int,
            responseEMsg: Int,
            request: ByteArray
        ) = byteArrayOf()
    }

    private fun account() = SteamAccount(
        id = 1L,
        steamId = "76561198000000001",
        accountName = "account",
        displayName = "Account",
        deviceId = "device",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "token",
        refreshToken = null,
        steamLoginSecure = "secure",
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 1,
        createdAt = 0L,
        updatedAt = 0L
    )
}
