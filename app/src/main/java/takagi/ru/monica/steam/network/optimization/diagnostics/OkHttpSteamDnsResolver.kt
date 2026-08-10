package takagi.ru.monica.steam.network.optimization.diagnostics

import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsResolutionResult
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsRuleParser

internal class OkHttpSteamDnsResolver(
    private val systemDns: Dns = Dns.SYSTEM,
    timeoutMillis: Long = 4_000L,
    private val clockNanos: () -> Long = System::nanoTime
) : SteamDnsResolver {
    private val client = OkHttpClient.Builder()
        .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .callTimeout(timeoutMillis * 2L, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()
    private val dohResolvers = mutableMapOf<String, Dns>()

    override suspend fun resolve(
        provider: SteamDnsProvider,
        hostname: String
    ): SteamDnsResolutionResult = withContext(Dispatchers.IO) {
        val startedAt = clockNanos()
        try {
            val addresses = resolverFor(provider)
                .lookup(hostname)
                .asSequence()
                .filterIsInstance<Inet4Address>()
                .filter(SteamHostsRuleParser::isUsableAddress)
                .mapNotNull { address -> address.hostAddress }
                .distinct()
                .take(MAX_ADDRESSES_PER_RESOLUTION)
                .toList()
            SteamDnsResolutionResult(
                provider = provider,
                hostname = hostname,
                addresses = addresses,
                latencyMillis = elapsedMillis(startedAt)
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            SteamDnsResolutionResult(
                provider = provider,
                hostname = hostname,
                latencyMillis = elapsedMillis(startedAt),
                errorType = error::class.java.simpleName
            )
        }
    }

    private fun resolverFor(provider: SteamDnsProvider): Dns {
        if (provider.isSystem) return systemDns
        return synchronized(dohResolvers) {
            dohResolvers.getOrPut(provider.id) {
                val bootstrapHosts = provider.bootstrapAddresses.map(InetAddress::getByName)
                DnsOverHttps.Builder()
                    .client(client)
                    .url(requireNotNull(provider.dohUrl).toHttpUrl())
                    .bootstrapDnsHosts(bootstrapHosts)
                    .includeIPv6(false)
                    .post(true)
                    .resolvePrivateAddresses(false)
                    .resolvePublicAddresses(true)
                    .build()
            }
        }
    }

    private fun elapsedMillis(startedAt: Long): Long =
        ((clockNanos() - startedAt) / 1_000_000L).coerceAtLeast(0L)

    private companion object {
        const val MAX_ADDRESSES_PER_RESOLUTION = 8
    }
}
