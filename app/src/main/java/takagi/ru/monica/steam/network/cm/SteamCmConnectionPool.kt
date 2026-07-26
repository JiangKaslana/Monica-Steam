package takagi.ru.monica.steam.network.cm

import java.io.Closeable
import java.io.IOException
import okhttp3.OkHttpClient
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamApiException

internal data class SteamCmEvent(
    val accountKey: String,
    val envelope: SteamCmEnvelope
)

/** Owns one persistent CM connection per account/storage identity. */
internal class SteamCmConnectionPool(
    private val bootstrap: SteamCmBootstrap,
    private val socketClient: OkHttpClient,
    private val timeoutMillis: Long,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val bootstrapTtlMillis: Long = DEFAULT_BOOTSTRAP_TTL_MILLIS,
    private val eventSink: (SteamCmEvent) -> Unit = {}
) : Closeable {
    private val lock = Any()
    private val connections = mutableMapOf<String, Entry>()
    private val bootstraps = mutableMapOf<String, CachedBootstrap>()

    fun execute(account: SteamAccount, operation: SteamCmOperation): ByteArray {
        val accountKey = accountKey(account)
        val session = loadBootstrap(account, accountKey)
        var lastFailure: Throwable? = null
        session.endpoints.take(MAX_ENDPOINT_ATTEMPTS).forEach { endpoint ->
            val connection = connectionFor(accountKey, session, endpoint)
            try {
                return connection.execute(operation)
            } catch (error: SteamApiException) {
                throw error
            } catch (error: Exception) {
                lastFailure = error
                remove(accountKey, connection)
                invalidateBootstrap(accountKey)
            }
        }
        throw IOException("Steam CM is unavailable", lastFailure)
    }

    override fun close() {
        val entries = synchronized(lock) {
            val current = connections.values.toList()
            connections.clear()
            bootstraps.clear()
            current
        }
        entries.forEach { it.connection.close() }
    }

    fun closeAccount(account: SteamAccount) {
        val key = accountKey(account)
        val entry = synchronized(lock) {
            bootstraps.remove(key)
            connections.remove(key)
        }
        entry?.connection?.close()
    }

    private fun loadBootstrap(account: SteamAccount, accountKey: String): SteamCmBootstrapData {
        val fingerprint = accountFingerprint(account)
        synchronized(lock) {
            bootstraps[accountKey]
                ?.takeIf { it.fingerprint == fingerprint && it.expiresAtMillis > nowMillis() }
                ?.let { return it.data }
        }
        val loaded = bootstrap.load(account)
        synchronized(lock) {
            bootstraps[accountKey] = CachedBootstrap(
                fingerprint = fingerprint,
                data = loaded,
                expiresAtMillis = nowMillis() + bootstrapTtlMillis
            )
        }
        return loaded
    }

    private fun connectionFor(
        accountKey: String,
        session: SteamCmBootstrapData,
        endpoint: String
    ): SteamCmPersistentConnection {
        synchronized(lock) {
            val current = connections[accountKey]
            if (current != null &&
                current.webLogonToken == session.webLogonToken &&
                current.endpoint == endpoint &&
                current.connection.isHealthy()
            ) {
                return current.connection
            }
            current?.connection?.close()
            val created = SteamCmPersistentConnection(
                socketFactory = socketClient::newWebSocket,
                endpoint = endpoint,
                steamId = session.steamId,
                webLogonToken = session.webLogonToken,
                timeoutMillis = timeoutMillis,
                eventSink = { envelope -> eventSink(SteamCmEvent(accountKey, envelope)) }
            )
            connections[accountKey] = Entry(
                endpoint = endpoint,
                webLogonToken = session.webLogonToken,
                connection = created
            )
            return created
        }
    }

    private fun remove(accountKey: String, connection: SteamCmPersistentConnection) {
        synchronized(lock) {
            if (connections[accountKey]?.connection === connection) {
                connections.remove(accountKey)
            }
        }
        connection.invalidate()
    }

    private fun invalidateBootstrap(accountKey: String) {
        synchronized(lock) { bootstraps.remove(accountKey) }
    }

    private fun accountKey(account: SteamAccount): String =
        "${account.id}|${account.steamId}"

    private fun accountFingerprint(account: SteamAccount): String =
        "${account.id}|${account.steamId}|${account.accessToken.orEmpty()}"

    private data class Entry(
        val endpoint: String,
        val webLogonToken: String,
        val connection: SteamCmPersistentConnection
    )

    private data class CachedBootstrap(
        val fingerprint: String,
        val data: SteamCmBootstrapData,
        val expiresAtMillis: Long
    )

    private companion object {
        const val DEFAULT_BOOTSTRAP_TTL_MILLIS = 120_000L
        const val MAX_ENDPOINT_ATTEMPTS = 3
    }
}
