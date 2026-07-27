package takagi.ru.monica.steam.friends.groupchat.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamProtoReader
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

    private class RecordingCmGateway : SteamCmGateway {
        var method = ""
        var request = byteArrayOf()
        override fun callService(account: SteamAccount, method: String, request: ByteArray): ByteArray {
            this.method = method
            this.request = request
            return byteArrayOf()
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
