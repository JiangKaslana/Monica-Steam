package takagi.ru.monica.steam.friends.voice.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceRealtimeEvent
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceRealtimeGateway
import takagi.ru.monica.steam.network.cm.SteamCmClient
import takagi.ru.monica.steam.network.cm.SteamCmEnvelope
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver
import takagi.ru.monica.steam.session.domain.resolveOrKeep

internal interface SteamVoiceRealtimeTransport {
    fun events(account: SteamAccount): Flow<SteamCmEnvelope>
    fun connect(account: SteamAccount)
    fun isConnected(account: SteamAccount): Boolean
}

private class SteamCmVoiceRealtimeTransport(
    private val cm: SteamCmClient
) : SteamVoiceRealtimeTransport {
    override fun events(account: SteamAccount): Flow<SteamCmEnvelope> = cm.eventsFor(account)
    override fun connect(account: SteamAccount) = cm.connect(account)
    override fun isConnected(account: SteamAccount): Boolean = cm.isConnected(account)
}

internal class SteamVoiceRealtimeService(
    private val transport: SteamVoiceRealtimeTransport =
        SteamCmVoiceRealtimeTransport(SteamCmClient()),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val healthyCheckMillis: Long = HEALTHY_CHECK_MILLIS,
    private val initialRetryMillis: Long = INITIAL_RETRY_MILLIS,
    private val maximumRetryMillis: Long = MAXIMUM_RETRY_MILLIS,
    private val sessionResolver: SteamAccountSessionResolver? = null
) : SteamVoiceRealtimeGateway {
    internal constructor(
        cm: SteamCmClient,
        sessionResolver: SteamAccountSessionResolver? = null
    ) : this(
        transport = SteamCmVoiceRealtimeTransport(cm),
        sessionResolver = sessionResolver
    )

    override fun events(account: SteamAccount): Flow<SteamVoiceRealtimeEvent> = channelFlow {
        var sessionAccount = account
        val eventCollector = launch {
            transport.events(account).collect { envelope ->
                runCatching { SteamVoiceRealtimeParser.parse(envelope) }
                    .getOrNull()
                    ?.let { send(it) }
            }
        }
        var retryMillis = initialRetryMillis.coerceAtLeast(1L)
        var announcedConnected = false
        try {
            while (isActive) {
                sessionAccount = sessionResolver.resolveOrKeep(account)
                val connected = try {
                    withContext(ioDispatcher) {
                        if (!transport.isConnected(sessionAccount)) transport.connect(sessionAccount)
                        transport.isConnected(sessionAccount)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    false
                }
                if (connected != announcedConnected) {
                    announcedConnected = connected
                    send(SteamVoiceRealtimeEvent.ConnectionChanged(connected))
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
