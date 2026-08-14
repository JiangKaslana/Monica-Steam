package takagi.ru.monica.steam.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.network.optimization.SteamCustomHostsDns
import takagi.ru.monica.steam.network.optimization.SteamDynamicDns
import takagi.ru.monica.steam.network.optimization.SteamRouteHealthEventListener
import takagi.ru.monica.steam.network.optimization.SteamRouteHealthRuntime
import takagi.ru.monica.steam.network.optimization.domain.SteamNetworkTargetCatalog

object SteamHttpClientProvider {
    private val baseClientDelegate = lazy { OkHttpClient.Builder().build() }
    private val dynamicDnsDelegate = lazy { SteamDynamicDns() }
    private val customHostsDnsDelegate = lazy {
        SteamCustomHostsDns(systemDns = dynamicDnsDelegate.value)
    }
    private val clientDelegate = lazy {
        baseClientDelegate.value.newBuilder()
            .dns(customHostsDnsDelegate.value)
            .eventListenerFactory { SteamRouteHealthEventListener() }
            .build()
    }

    val client: OkHttpClient get() = clientDelegate.value

    fun newBuilder(): OkHttpClient.Builder = client.newBuilder()

    internal fun onCustomHostsChanged() {
        SteamRouteHealthRuntime.clear()
        evictInitializedConnections("custom_hosts")
    }

    internal fun onResolverSettingsChanged() {
        SteamRouteHealthRuntime.clear()
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

    internal suspend fun refreshDynamicDnsCache(): Int = withContext(Dispatchers.IO) {
        val dynamicDns = dynamicDnsDelegate.value
        dynamicDns.clearCache()
        SteamNetworkTargetCatalog.hostnames
            .take(4)
            .forEach { hostname ->
                runCatching { dynamicDns.lookup(hostname) }
                    .onFailure { error ->
                        runCatching {
                            SteamDiagLogger.append(
                                "dynamic_dns refresh_failed host=$hostname " +
                                    "type=${error::class.java.simpleName}"
                            )
                        }
                    }
            }
        evictInitializedConnections("resolver_force_refresh")
        dynamicDns.cacheSize()
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
