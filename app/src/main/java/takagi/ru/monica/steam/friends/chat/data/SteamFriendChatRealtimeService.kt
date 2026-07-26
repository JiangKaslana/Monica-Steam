package takagi.ru.monica.steam.friends.chat.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.domain.SteamChatRealtimeEvent
import takagi.ru.monica.steam.friends.chat.domain.SteamChatRealtimeGateway
import takagi.ru.monica.steam.network.cm.SteamCmClient
import takagi.ru.monica.steam.network.cm.SteamCmEnvelope
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver
import takagi.ru.monica.steam.session.domain.resolveOrKeep

internal interface SteamFriendChatRealtimeTransport {
    fun events(account: SteamAccount): Flow<SteamCmEnvelope>
    fun connect(account: SteamAccount)
    fun isConnected(account: SteamAccount): Boolean
}

internal class SteamCmFriendChatRealtimeTransport(
    private val cm: SteamCmClient
) : SteamFriendChatRealtimeTransport {
    override fun events(account: SteamAccount): Flow<SteamCmEnvelope> = cm.eventsFor(account)

    override fun connect(account: SteamAccount) = cm.connect(account)

    override fun isConnected(account: SteamAccount): Boolean = cm.isConnected(account)
}

/**
 * Adapter from the shared account CM connection to friend-chat domain events.
 * It keeps the socket authenticated while collected and retries without
 * creating a second feature-owned WebSocket.
 */
internal class SteamFriendChatRealtimeService(
    private val transport: SteamFriendChatRealtimeTransport =
        SteamCmFriendChatRealtimeTransport(SteamCmClient()),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val healthyCheckMillis: Long = HEALTHY_CHECK_MILLIS,
    private val initialRetryMillis: Long = INITIAL_RETRY_MILLIS,
    private val maximumRetryMillis: Long = MAXIMUM_RETRY_MILLIS,
    private val sessionResolver: SteamAccountSessionResolver? = null
) : SteamChatRealtimeGateway {
    internal constructor(
        cm: SteamCmClient,
        sessionResolver: SteamAccountSessionResolver? = null
    ) : this(
        transport = SteamCmFriendChatRealtimeTransport(cm),
        sessionResolver = sessionResolver
    )

    override fun events(account: SteamAccount): Flow<SteamChatRealtimeEvent> =
        channelFlow {
            val sessionAccount = sessionResolver.resolveOrKeep(account)
            val eventCollector = launch {
                transport.events(sessionAccount).collect { envelope ->
                    runCatching {
                        SteamFriendChatRealtimeParser.parse(envelope, sessionAccount.steamId)
                    }.getOrNull()
                        ?.let { send(it) }
                }
            }
            var retryMillis = initialRetryMillis.coerceAtLeast(1L)
            var announcedConnected = false
            try {
                while (isActive) {
                    val connected = runCatching {
                        withContext(ioDispatcher) {
                            if (!transport.isConnected(sessionAccount)) transport.connect(sessionAccount)
                            transport.isConnected(sessionAccount)
                        }
                    }.getOrDefault(false)
                    if (connected != announcedConnected) {
                        announcedConnected = connected
                        send(SteamChatRealtimeEvent.ConnectionChanged(connected))
                    }
                    if (connected) {
                        retryMillis = initialRetryMillis.coerceAtLeast(1L)
                        delay(healthyCheckMillis.coerceAtLeast(1L))
                    } else {
                        delay(retryMillis)
                        retryMillis = (retryMillis * 2L).coerceAtMost(maximumRetryMillis)
                    }
                }
            } finally {
                eventCollector.cancel()
            }
        }

    private companion object {
        const val HEALTHY_CHECK_MILLIS = 15_000L
        const val INITIAL_RETRY_MILLIS = 1_000L
        const val MAXIMUM_RETRY_MILLIS = 30_000L
    }
}
