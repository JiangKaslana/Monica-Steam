package takagi.ru.monica.steam.network.cm

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import takagi.ru.monica.steam.data.SteamAccount

/**
 * CM gateway backed by an account-scoped persistent connection pool.
 *
 * The default constructor uses the process-wide pool so the chat, group-chat,
 * reaction, and rich-media services do not each create their own socket.
 */
class SteamCmClient private constructor(
    private val pool: SteamCmConnectionPool,
    private val eventFlow: SharedFlow<SteamCmEvent>? = null
) : SteamCmGateway {
    constructor() : this(SteamCmRuntime.pool, SteamCmRuntime.events)

    internal constructor(
        bootstrap: SteamCmBootstrap,
        socketClient: OkHttpClient,
        timeoutMillis: Long,
        eventSink: (SteamCmEvent) -> Unit = {}
    ) : this(
        SteamCmConnectionPool(
            bootstrap = bootstrap,
            socketClient = socketClient,
            timeoutMillis = timeoutMillis,
            eventSink = eventSink
        )
    )

    /** Unsolicited CM envelopes from the shared process pool. */
    internal val events: SharedFlow<SteamCmEvent>
        get() = requireNotNull(eventFlow)

    override fun callService(
        account: SteamAccount,
        method: String,
        request: ByteArray
    ): ByteArray = pool.execute(
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
    ): ByteArray = pool.execute(
        account = account,
        operation = SteamCmOperation(
            requestEMsg = requestEMsg,
            responseEMsg = responseEMsg,
            requestBody = request
        )
    )
}

/** Process-wide lifecycle boundary; app shutdown can close this pool explicitly. */
internal object SteamCmRuntime {
    private val eventBus = MutableSharedFlow<SteamCmEvent>(extraBufferCapacity = 128)
    private val socketClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    val events: SharedFlow<SteamCmEvent> = eventBus.asSharedFlow()
    val pool: SteamCmConnectionPool = SteamCmConnectionPool(
        bootstrap = SteamCmBootstrap(),
        socketClient = socketClient,
        timeoutMillis = DEFAULT_TIMEOUT_MILLIS,
        eventSink = { eventBus.tryEmit(it) }
    )

    fun close() {
        pool.close()
        socketClient.dispatcher.executorService.shutdown()
        socketClient.connectionPool.evictAll()
    }

    private const val DEFAULT_TIMEOUT_MILLIS = 15_000L
}
