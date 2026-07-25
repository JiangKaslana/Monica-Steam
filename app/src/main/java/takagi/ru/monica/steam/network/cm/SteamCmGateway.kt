package takagi.ru.monica.steam.network.cm

import takagi.ru.monica.steam.data.SteamAccount

/** Small boundary for Steam services that are available only through a CM session. */
interface SteamCmGateway {
    fun callService(
        account: SteamAccount,
        method: String,
        request: ByteArray
    ): ByteArray

    fun exchangeClientMessage(
        account: SteamAccount,
        requestEMsg: Int,
        responseEMsg: Int,
        request: ByteArray = ByteArray(0)
    ): ByteArray
}
