package takagi.ru.monica.steam.network

import okhttp3.OkHttpClient
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.network.optimization.SteamCustomHostsDns
import takagi.ru.monica.steam.network.optimization.SteamDynamicDns

object SteamHttpClientProvider {
    private val baseClientDelegate = lazy { OkHttpClient.Builder().build() }
    private val dynamicDnsDelegate = lazy { SteamDynamicDns() }
    private val customHostsDnsDelegate = lazy {
        SteamCustomHostsDns(systemDns = dynamicDnsDelegate.value)
    }
    private val clientDelegate = lazy {
        baseClientDelegate.value.newBuilder()
            .dns(customHostsDnsDelegate.value)
            .build()
    }

    val client: OkHttpClient get() = clientDelegate.value

    fun newBuilder(): OkHttpClient.Builder = client.newBuilder()

    internal fun onCustomHostsChanged() {
        evictInitializedConnections("custom_hosts")
    }

    internal fun onResolverSettingsChanged() {
        if (dynamicDnsDelegate.isInitialized()) {
            runCatching { dynamicDnsDelegate.value.clearCache() }
                .onFailure { error -> logCleanupFailure("resolver_cache", error) }
        }
        evictInitializedConnections("resolver_settings")
    }

    internal fun clearDynamicDnsCache() {
        if (dynamicDnsDelegate.isInitialized()) {
            runCatching { dynamicDnsDelegate.value.clearCache() }
                .onFailure { error -> logCleanupFailure("resolver_cache", error) }
        }
        evictInitializedConnections("resolver_cache")
    }

    internal fun dynamicDnsCacheSize(): Int =
        if (dynamicDnsDelegate.isInitialized()) {
            runCatching { dynamicDnsDelegate.value.cacheSize() }.getOrDefault(0)
        } else {
            0
        }

    private fun evictInitializedConnections(reason: String) {
        if (!clientDelegate.isInitialized()) return
        val initializedClient = clientDelegate.value
        runCatching {
            initializedClient.dispatcher.executorService.execute {
                runCatching {
                    initializedClient.connectionPool.evictAll()
                }.onFailure { error -> logCleanupFailure(reason, error) }
            }
        }.onFailure { error -> logCleanupFailure(reason, error) }
    }

    private fun logCleanupFailure(reason: String, error: Throwable) {
        runCatching {
            SteamDiagLogger.append(
                "network_optimization cleanup_failed reason=$reason " +
                    "type=${error::class.java.simpleName}"
            )
        }
    }
}
