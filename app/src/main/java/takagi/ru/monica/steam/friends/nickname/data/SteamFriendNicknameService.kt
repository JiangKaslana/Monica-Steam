package takagi.ru.monica.steam.friends.nickname.data

import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.nickname.domain.SteamFriendNicknameGateway
import takagi.ru.monica.steam.network.cm.SteamCmClient
import takagi.ru.monica.steam.network.cm.SteamCmGateway

class SteamFriendNicknameService(
    private val cm: SteamCmGateway = SteamCmClient()
) : SteamFriendNicknameGateway {
    override fun fetch(account: SteamAccount): Map<String, String> {
        require(account.hasRealSteamId) { "real Steam ID required" }
        require(!account.accessToken.isNullOrBlank()) { "Steam access token required" }
        return SteamFriendNicknameParser.parse(
            cm.callService(
                account = account,
                method = GET_NICKNAME_LIST_METHOD,
                request = ByteArray(0)
            )
        )
    }

    private companion object {
        const val GET_NICKNAME_LIST_METHOD = "Player.GetNicknameList#1"
    }
}
