package takagi.ru.monica.steam.network.optimization

import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.network.optimization.diagnostics.OkHttpSteamDnsResolver
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsRuleParser
import takagi.ru.monica.steam.network.optimization.domain.SteamNetworkTargetCatalog

/**
 * App-scoped dynamic DNS for Steam traffic.
 *
 * Unlike the optional Hosts override, this resolver never persists an address for a Steam
 * hostname. It races the enabled secure/custom resolvers, keeps a short in-memory cache, and
 * re-resolves after the cache expires. Non-Steam traffic is always delegated to Android's
 * system resolver.
 */
internal class SteamDynamicDns(
    private val systemDns: Dns = Dns.SYSTEM,
    private val resolver: OkHttpSteamDnsResolver = OkHttpSteamDnsResolver(
        systemDns = systemDns,
        timeoutMillis = RESOLVER_TIMEOUT_MILLIS
    ),
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val logger: (String) -> Unit = SteamDiagLogger::append
) : Dns {
    private data class CacheEntry(
        val addresses: List<InetAddress>,
        val expiresAtMillis: Long,
        val staleUntilMillis: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val executor = Executors.newFixedThreadPool(
        MAX_PARALLEL_RESOLVERS,
        ResolverThreadFactory()
    )

    override fun lookup(hostname: String): List<InetAddress> {
        val normalized = SteamHostsRuleParser.normalizeHostname(hostname)
        if (!SteamNetworkTargetCatalog.isSteamHostname(normalized)) {
            return systemDns.lookup(hostname)
        }

        val settings = SteamNetworkResolverSettingsRuntime.settings.value
        if (!settings.dynamicDnsEnabled) {
            return systemDns.lookup(hostname)
        }

        val providers = settings.activeProviders
        val cacheKey = buildCacheKey(normalized, providers)
        val now = clockMillis()
        val cached = cache[cacheKey]
        if (cached != null && now < cached.expiresAtMillis) {
            logSafely("dynamic_dns cache_hit host=$normalized addresses=${cached.addresses.size}")
            return cached.addresses
        }

        val secureProviders = providers.filterNot(SteamDnsProvider::isSystem)
        val preferFirst = settings.preferredProviderIds.isNotEmpty() &&
            secureProviders.firstOrNull()?.id in settings.preferredProviderIds
        val resolved = raceResolvers(
            providers = secureProviders,
            hostname = normalized,
            preferFirst = preferFirst
        )
        if (resolved.isNotEmpty()) {
            cache[cacheKey] = CacheEntry(
                addresses = resolved,
                expiresAtMillis = now + CACHE_TTL_MILLIS,
                staleUntilMillis = now + STALE_TTL_MILLIS
            )
            pruneExpired(now)
            logSafely(
                "dynamic_dns resolved host=$normalized addresses=${resolved.size} " +
                    "preferred_head_start=$preferFirst"
            )
            return resolved
        }

        if (cached != null && now < cached.staleUntilMillis) {
            logSafely("dynamic_dns stale_cache host=$normalized addresses=${cached.addresses.size}")
            return cached.addresses
        }

        if (settings.useSystemDns) {
            val fallback = runCatching { systemDns.lookup(hostname) }
                .getOrDefault(emptyList())
                .filter(SteamHostsRuleParser::isUsableAddress)
                .distinctBy(InetAddress::getHostAddress)
            if (fallback.isNotEmpty()) {
                logSafely("dynamic_dns system_fallback host=$normalized addresses=${fallback.size}")
                return fallback
            }
        }

        logSafely("dynamic_dns failure host=$normalized providers=${secureProviders.size}")
        throw UnknownHostException("Unable to resolve Steam host dynamically: $normalized")
    }

    fun clearCache() {
        cache.clear()
        logSafely("dynamic_dns cache_cleared")
    }

    fun cacheSize(): Int = cache.size

    private fun raceResolvers(
        providers: List<SteamDnsProvider>,
        hostname: String,
        preferFirst: Boolean
    ): List<InetAddress> {
        if (providers.isEmpty()) return emptyList()

        val candidates = providers.take(MAX_RACE_PROVIDERS)
        val completion = ExecutorCompletionService<List<InetAddress>>(executor)
        val futures = mutableListOf<Future<List<InetAddress>>>()
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RACE_TIMEOUT_MILLIS)
        var completed = 0
        var answer: List<InetAddress> = emptyList()

        fun submit(provider: SteamDnsProvider) {
            futures += completion.submit(Callable { resolveProvider(provider, hostname) })
        }

        try {
            if (preferFirst && candidates.size > 1) {
                submit(candidates.first())
                val early = completion.poll(
                    PREFERRED_HEAD_START_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                if (early != null) {
                    completed += 1
                    answer = runCatching { early.get() }.getOrDefault(emptyList())
                }
                if (answer.isEmpty()) {
                    candidates.drop(1).forEach(::submit)
                }
            } else {
                candidates.forEach(::submit)
            }

            while (completed < futures.size && answer.isEmpty()) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0L) break
                val future = completion.poll(remaining, TimeUnit.NANOSECONDS) ?: break
                completed += 1
                val addresses = runCatching { future.get() }.getOrDefault(emptyList())
                if (addresses.isNotEmpty()) answer = addresses
            }
        } finally {
            futures.forEach { future ->
                if (!future.isDone) future.cancel(true)
            }
        }
        return answer
    }

    private fun resolveProvider(
        provider: SteamDnsProvider,
        hostname: String
    ): List<InetAddress> {
        val result = runBlocking { resolver.resolve(provider, hostname) }
        if (!result.isAvailable) return emptyList()
        return result.addresses
            .mapNotNull { raw -> runCatching { InetAddress.getByName(raw) }.getOrNull() }
            .filter(SteamHostsRuleParser::isUsableAddress)
            .distinctBy(InetAddress::getHostAddress)
    }

    private fun buildCacheKey(hostname: String, providers: List<SteamDnsProvider>): String {
        val resolverSignature = providers.joinToString(";") { provider ->
            buildString {
                append(provider.id)
                append('|')
                append(provider.dohUrl.orEmpty())
                append('|')
                append(provider.udpServer.orEmpty())
                append('|')
                append(provider.bootstrapAddresses.joinToString(","))
            }
        }
        return "$hostname#$resolverSignature"
    }

    private fun pruneExpired(now: Long) {
        if (cache.size <= MAX_CACHE_ENTRIES) return
        cache.entries.removeIf { (_, entry) -> now >= entry.staleUntilMillis }
        if (cache.size <= MAX_CACHE_ENTRIES) return
        cache.entries
            .sortedBy { it.value.expiresAtMillis }
            .take(cache.size - MAX_CACHE_ENTRIES)
            .forEach { cache.remove(it.key) }
    }

    private fun logSafely(message: String) {
        runCatching { logger(message) }
    }

    private class ResolverThreadFactory : ThreadFactory {
        override fun newThread(runnable: Runnable): Thread = Thread(
            runnable,
            "Monica-Steam-DNS-${threadIds.incrementAndGet()}"
        ).apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
    }

    private companion object {
        const val MAX_PARALLEL_RESOLVERS = 6
        const val MAX_RACE_PROVIDERS = 8
        const val MAX_CACHE_ENTRIES = 256
        const val RESOLVER_TIMEOUT_MILLIS = 2_500L
        const val RACE_TIMEOUT_MILLIS = 3_000L
        const val PREFERRED_HEAD_START_MILLIS = 150L
        const val CACHE_TTL_MILLIS = 5 * 60 * 1_000L
        const val STALE_TTL_MILLIS = 30 * 60 * 1_000L
        val threadIds = AtomicInteger(0)
    }
}
