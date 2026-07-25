package takagi.ru.monica.steam.network.cm

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiException

class SteamCmClient internal constructor(
    private val bootstrap: SteamCmBootstrap,
    private val socketClient: OkHttpClient,
    private val timeoutMillis: Long
) : SteamCmGateway {
    constructor() : this(
        bootstrap = SteamCmBootstrap(),
        socketClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build(),
        timeoutMillis = DEFAULT_TIMEOUT_MILLIS
    )

    override fun callService(
        account: SteamAccount,
        method: String,
        request: ByteArray
    ): ByteArray = execute(
        account = account,
        operation = SteamCmOperation(
            requestEMsg = SteamCmProtocol.EMSG_SERVICE_METHOD_CALL_FROM_CLIENT,
            responseEMsg = SteamCmProtocol.EMSG_SERVICE_METHOD_RESPONSE,
            requestBody = request,
            targetJobName = method
        )
    )

    override fun exchangeClientMessage(
        account: SteamAccount,
        requestEMsg: Int,
        responseEMsg: Int,
        request: ByteArray
    ): ByteArray = execute(
        account = account,
        operation = SteamCmOperation(
            requestEMsg = requestEMsg,
            responseEMsg = responseEMsg,
            requestBody = request
        )
    )

    private fun execute(account: SteamAccount, operation: SteamCmOperation): ByteArray {
        val session = bootstrap.load(account)
        var lastFailure: Throwable? = null
        session.endpoints.take(MAX_ENDPOINT_ATTEMPTS).forEach { endpoint ->
            try {
                return SteamCmWebSocketExchange(
                    socketFactory = socketClient::newWebSocket,
                    endpoint = endpoint,
                    steamId = session.steamId,
                    webLogonToken = session.webLogonToken,
                    operation = operation,
                    timeoutMillis = timeoutMillis
                ).execute()
            } catch (error: SteamApiException) {
                throw error
            } catch (error: Throwable) {
                lastFailure = error
            }
        }
        throw IOException("Steam CM is unavailable", lastFailure)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
        const val MAX_ENDPOINT_ATTEMPTS = 3
    }
}
