package takagi.ru.monica.steam.network

import okhttp3.OkHttpClient
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.network.optimization.SteamCustomHostsDns

object SteamHttpClientProvider {
    private val baseClientDelegate = lazy { OkHttpClient.Builder().build() }
    private val customHostsDnsDelegate = lazy { SteamCustomHostsDns() }
    private val clientDelegate = lazy {
        baseClientDelegate.value.newBuilder()
            .dns(customHostsDnsDelegate.value)
            .build()
    }

    val client: OkHttpClient get() = clientDelegate.value

    fun newBuilder(): OkHttpClient.Builder = client.newBuilder()

    internal fun onCustomHostsChanged() {
        if (!clientDelegate.isInitialized()) return
        val initializedClient = clientDelegate.value
        runCatching {
            initializedClient.dispatcher.executorService.execute {
                runCatching {
                    initializedClient.connectionPool.evictAll()
                }.onFailure(::logConnectionPoolCleanupFailure)
            }
        }.onFailure(::logConnectionPoolCleanupFailure)
    }

    private fun logConnectionPoolCleanupFailure(error: Throwable) {
        runCatching {
            SteamDiagLogger.append(
                "network_optimization connection_pool_cleanup_failed " +
                    "type=${error::class.java.simpleName}"
            )
        }
    }
}
