package takagi.ru.monica.steam.network.optimization

import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.network.optimization.domain.SteamNetworkOptimizationHostPolicy

internal class SteamOptimizedDns(
    private val secureResolvers: List<Dns>,
    private val systemDns: Dns = Dns.SYSTEM,
    private val optimizationEnabled: () -> Boolean = SteamNetworkOptimizationRuntime::isEnabled,
    private val logger: (String) -> Unit = SteamDiagLogger::append,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val cacheTtlMillis: Long = DEFAULT_CACHE_TTL_MILLIS
) : Dns {
    private data class CacheEntry(
        val addresses: List<InetAddress>,
        val expiresAtMillis: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    override fun lookup(hostname: String): List<InetAddress> {
        val normalized = hostname.trim().trimEnd('.').lowercase()
        if (!optimizationEnabled() || !SteamNetworkOptimizationHostPolicy.shouldOptimize(normalized)) {
            return systemDns.lookup(hostname)
        }

        cache[normalized]?.takeIf { it.expiresAtMillis > nowMillis() }?.let { entry ->
            return entry.addresses
        }
        cache.remove(normalized)

        val errors = mutableListOf<String>()
        secureResolvers.forEachIndexed { index, resolver ->
            try {
                val addresses = resolver.lookup(normalized).filter(::isUsableAddress)
                if (addresses.isNotEmpty()) {
                    cache(normalized, addresses)
                    logSafely("optimized_dns host=$normalized source=secure${index + 1}")
                    return addresses
                }
                errors += "secure${index + 1}: no usable address"
            } catch (error: IOException) {
                errors += "secure${index + 1}: ${error.message.orEmpty()}"
            } catch (error: RuntimeException) {
                errors += "secure${index + 1}: ${error.message.orEmpty()}"
            }
        }

        try {
            val addresses = systemDns.lookup(normalized).filter(::isUsableAddress)
            if (addresses.isNotEmpty()) {
                cache(normalized, addresses)
                logSafely("optimized_dns host=$normalized source=system_fallback")
                return addresses
            }
            errors += "system: no usable address"
        } catch (error: IOException) {
            errors += "system: ${error.message.orEmpty()}"
        } catch (error: RuntimeException) {
            errors += "system: ${error.message.orEmpty()}"
        }

        throw UnknownHostException(
            "Unable to resolve optimized Steam host $normalized: ${errors.joinToString("; ")}"
        )
    }

    fun clearCache() {
        cache.clear()
    }

    private fun cache(hostname: String, addresses: List<InetAddress>) {
        cache[hostname] = CacheEntry(
            addresses = addresses,
            expiresAtMillis = nowMillis() + cacheTtlMillis
        )
    }

    private fun logSafely(message: String) {
        runCatching { logger(message) }
    }

    companion object {
        private const val DEFAULT_CACHE_TTL_MILLIS = 5L * 60L * 1_000L

        private data class DnsEndpoint(
            val url: String,
            val bootstrapAddresses: List<String>
        )

        private val endpoints = listOf(
            DnsEndpoint(
                url = "https://dns.alidns.com/dns-query",
                bootstrapAddresses = listOf("223.5.5.5", "223.6.6.6")
            ),
            DnsEndpoint(
                url = "https://doh.pub/dns-query",
                bootstrapAddresses = listOf("1.12.12.12", "120.53.53.53")
            )
        )

        fun create(baseClient: OkHttpClient): SteamOptimizedDns {
            val dohClient = baseClient.newBuilder()
                .dns(Dns.SYSTEM)
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .writeTimeout(3, TimeUnit.SECONDS)
                .callTimeout(4, TimeUnit.SECONDS)
                .build()
            val resolvers = endpoints.mapNotNull { endpoint ->
                runCatching {
                    DnsOverHttps.Builder()
                        .client(dohClient)
                        .url(endpoint.url.toHttpUrl())
                        .post(true)
                        .includeIPv6(true)
                        .resolvePrivateAddresses(false)
                        .bootstrapDnsHosts(
                            *endpoint.bootstrapAddresses
                                .map(InetAddress::getByName)
                                .toTypedArray()
                        )
                        .build()
                }.getOrNull()
            }
            return SteamOptimizedDns(secureResolvers = resolvers)
        }

        internal fun isUsableAddress(address: InetAddress): Boolean {
            if (
                address.isAnyLocalAddress ||
                address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress ||
                address.isMulticastAddress
            ) {
                return false
            }
            if (address is Inet4Address) {
                val bytes = address.address.map { it.toInt() and 0xff }
                if (bytes[0] == 108 && bytes[1] == 160 && bytes[2] in 160..175) {
                    return false
                }
            }
            return true
        }
    }
}
