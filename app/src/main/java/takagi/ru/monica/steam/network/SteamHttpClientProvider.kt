package takagi.ru.monica.steam.network

import okhttp3.OkHttpClient
import takagi.ru.monica.steam.network.optimization.SteamOptimizedDns

object SteamHttpClientProvider {
    private val baseClientDelegate = lazy { OkHttpClient.Builder().build() }
    private val optimizedDnsDelegate = lazy {
        SteamOptimizedDns.create(baseClientDelegate.value)
    }
    private val clientDelegate = lazy {
        baseClientDelegate.value.newBuilder()
            .dns(optimizedDnsDelegate.value)
            .build()
    }

    val client: OkHttpClient get() = clientDelegate.value

    fun newBuilder(): OkHttpClient.Builder = client.newBuilder()

    internal fun onOptimizationChanged() {
        clearDnsCache()
        if (clientDelegate.isInitialized()) {
            clientDelegate.value.connectionPool.evictAll()
        }
    }

    internal fun clearDnsCache() {
        if (optimizedDnsDelegate.isInitialized()) {
            optimizedDnsDelegate.value.clearCache()
        }
    }
}
