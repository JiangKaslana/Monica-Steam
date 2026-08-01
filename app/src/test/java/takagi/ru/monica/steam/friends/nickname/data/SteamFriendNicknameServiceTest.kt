package takagi.ru.monica.steam.friends.nickname.data

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmGateway

class SteamFriendNicknameServiceTest {
    @Test
    fun parsesOfficialAccountIdsIntoSteamIds() {
        val response = SteamProtoWriter().apply {
            writeMessage(1, nickname(accountId = 39_734_274L, value = "Alyx note"))
            writeMessage(1, nickname(accountId = 39_734_275L, value = "Gordon note"))
            writeMessage(1, nickname(accountId = 39_734_276L, value = "   "))
        }.toByteArray()

        assertEquals(
            linkedMapOf(
                "76561198000000002" to "Alyx note",
                "76561198000000003" to "Gordon note"
            ),
            SteamFriendNicknameParser.parse(response)
        )
    }

    @Test
    fun requestsTheOfficialPlayerNicknameService() {
        val cm = RecordingNicknameCm(
            SteamProtoWriter().apply {
                writeMessage(1, nickname(accountId = 39_734_274L, value = "Official note"))
            }.toByteArray()
        )

        val result = SteamFriendNicknameService(cm).fetch(account())

        assertEquals("Player.GetNicknameList#1", cm.method)
        assertEquals(0, cm.request.size)
        assertEquals("Official note", result["76561198000000002"])
    }

    private fun nickname(accountId: Long, value: String) = SteamProtoWriter().apply {
        writeFixed32(1, accountId)
        writeString(2, value)
    }

    private fun account() = SteamAccount(
        id = 1L,
        steamId = "76561198000000001",
        accountName = "steam_user",
        displayName = "steam_user",
        deviceId = "android:test",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "access-token",
        refreshToken = "refresh-token",
        steamLoginSecure = "76561198000000001||access-token",
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = 1L
    )
}

private class RecordingNicknameCm(private val response: ByteArray) : SteamCmGateway {
    var method: String = ""
    var request: ByteArray = ByteArray(0)

    override fun callService(
        account: SteamAccount,
        method: String,
        request: ByteArray
    ): ByteArray {
        this.method = method
        this.request = request
        return response
    }

    override fun exchangeClientMessage(
        account: SteamAccount,
        requestEMsg: Int,
        responseEMsg: Int,
        request: ByteArray
    ): ByteArray = error("Unexpected client message")
}
