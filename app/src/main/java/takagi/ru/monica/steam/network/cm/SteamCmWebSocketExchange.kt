package takagi.ru.monica.steam.network.cm

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.network.SteamProtoReader

internal data class SteamCmOperation(
    val requestEMsg: Int,
    val responseEMsg: Int,
    val requestBody: ByteArray,
    val targetJobName: String? = null,
    val jobId: Long = if (targetJobName == null) SteamCmProtocol.JOB_ID_NONE else 1L
)

internal class SteamCmWebSocketExchange(
    private val socketFactory: (Request, WebSocketListener) -> WebSocket,
    private val endpoint: String,
    private val steamId: Long,
    private val webLogonToken: String,
    private val operation: SteamCmOperation,
    private val timeoutMillis: Long
) : WebSocketListener() {
    private val completed = AtomicBoolean(false)
    private val latch = CountDownLatch(1)
    private var outcome: Outcome? = null
    private var webSocket: WebSocket? = null
    private var sessionId: Int = 0
    private var sessionSteamId: Long = steamId
    private var loggedOn = false

    fun execute(): ByteArray {
        val request = Request.Builder()
            .url("wss://$endpoint/cmsocket/")
            .header("Origin", "https://steamcommunity.com")
            .header("User-Agent", "Mozilla/5.0 Monica-Steam/1.0")
            .build()
        webSocket = socketFactory(request, this)
        if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
            webSocket?.cancel()
            throw SocketTimeoutException("Steam CM request timed out")
        }
        val result = requireNotNull(outcome) { "Steam CM request completed without a result" }
        result.error?.let { throw it }
        return requireNotNull(result.value)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        runCatching {
            val login = SteamCmProtocol.encodeMessage(
                eMsg = SteamCmProtocol.EMSG_CLIENT_LOGON,
                steamId = steamId,
                sessionId = 0,
                body = SteamCmProtocol.webLogonBody(webLogonToken)
            )
            check(webSocket.send(ByteString.of(*login))) { "Steam CM logon send failed" }
        }.onFailure(::completeFailure)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        runCatching {
            SteamCmProtocol.decodeMessages(bytes.toByteArray()).forEach(::handleEnvelope)
        }.onFailure(::completeFailure)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        completeFailure(IOException("Steam CM returned an unexpected text message"))
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        completeFailure(t)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        if (!completed.get()) {
            completeFailure(IOException("Steam CM closed before completing the request"))
        }
    }

    private fun handleEnvelope(envelope: SteamCmEnvelope) {
        if (completed.get()) return
        if (!loggedOn) {
            if (envelope.eMsg != SteamCmProtocol.EMSG_CLIENT_LOGON_RESPONSE) return
            val eResult = SteamProtoReader(envelope.body).parse()[1]?.asInt ?: 2
            if (eResult != 1) {
                completeFailure(
                    SteamApiException(
                        message = "Steam CM logon failed (eresult=$eResult)",
                        eResult = eResult
                    )
                )
                return
            }
            sessionId = envelope.header.sessionId
            sessionSteamId = envelope.header.steamId.takeIf { it > 0L } ?: steamId
            if (sessionId == 0) {
                completeFailure(IOException("Steam CM logon returned no session ID"))
                return
            }
            loggedOn = true
            sendOperation()
            return
        }

        if (envelope.eMsg == SteamCmProtocol.EMSG_CLIENT_LOGGED_OFF) {
            val eResult = SteamProtoReader(envelope.body).parse()[1]?.asInt ?: 2
            completeFailure(
                SteamApiException(
                    message = "Steam CM logged off (eresult=$eResult)",
                    eResult = eResult
                )
            )
            return
        }
        if (envelope.eMsg != operation.responseEMsg) return
        if (operation.jobId != SteamCmProtocol.JOB_ID_NONE &&
            envelope.header.jobIdTarget != operation.jobId
        ) {
            return
        }
        envelope.header.transportError
            ?.takeIf { it != 1 }
            ?.let { error ->
                completeFailure(
                    SteamApiException(
                        message = envelope.header.errorMessage
                            ?: "Steam CM transport failed ($error)",
                        eResult = error
                    )
                )
                return
            }
        envelope.header.eResult
            ?.takeIf { it != 1 }
            ?.let { eResult ->
                completeFailure(
                    SteamApiException(
                        message = envelope.header.errorMessage
                            ?: "Steam CM service failed (eresult=$eResult)",
                        eResult = eResult
                    )
                )
                return
            }
        completeSuccess(envelope.body)
    }

    private fun sendOperation() {
        runCatching {
            val request = SteamCmProtocol.encodeMessage(
                eMsg = operation.requestEMsg,
                steamId = sessionSteamId,
                sessionId = sessionId,
                body = operation.requestBody,
                jobIdSource = operation.jobId,
                targetJobName = operation.targetJobName
            )
            check(webSocket?.send(ByteString.of(*request)) == true) {
                "Steam CM operation send failed"
            }
        }.onFailure(::completeFailure)
    }

    private fun completeSuccess(value: ByteArray) {
        if (!completed.compareAndSet(false, true)) return
        outcome = Outcome(value = value)
        webSocket?.close(1000, null)
        latch.countDown()
    }

    private fun completeFailure(error: Throwable) {
        if (!completed.compareAndSet(false, true)) return
        outcome = Outcome(error = error)
        webSocket?.cancel()
        latch.countDown()
    }

    private data class Outcome(
        val value: ByteArray? = null,
        val error: Throwable? = null
    )
}
