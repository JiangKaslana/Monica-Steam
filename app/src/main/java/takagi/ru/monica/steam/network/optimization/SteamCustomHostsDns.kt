package takagi.ru.monica.steam.network.optimization

import java.net.InetAddress
import okhttp3.Dns
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsRuleParser

internal class SteamCustomHostsDns(
    private val systemDns: Dns = Dns.SYSTEM,
    private val customAddresses: (String) -> List<InetAddress> =
        SteamNetworkOptimizationRuntime::addressesForHost,
    private val fallbackToSystemDns: () -> Boolean =
        SteamNetworkOptimizationRuntime::isSystemDnsFallbackEnabled,
    private val onCustomHostsUsed: (String) -> Unit =
        SteamNetworkOptimizationRuntime::recordHostHit,
    private val rankAddresses: (String, List<InetAddress>) -> List<InetAddress> =
        SteamRouteHealthRuntime::rank,
    private val logger: (String) -> Unit = SteamDiagLogger::append
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val normalized = SteamHostsRuleParser.normalizeHostname(hostname)
        val overrides = runCatching { customAddresses(normalized) }
            .onFailure { error ->
                logSafely(
                    "custom_hosts lookup_failed host=$normalized " +
                        "type=${error::class.java.simpleName}"
                )
            }
            .getOrDefault(emptyList())
            .filter(SteamHostsRuleParser::isUsableAddress)

        if (overrides.isNotEmpty()) {
            runCatching { onCustomHostsUsed(normalized) }
            val fallbackAddresses = if (runCatching(fallbackToSystemDns).getOrDefault(true)) {
                runCatching { systemDns.lookup(hostname) }
                    .onFailure { error ->
                        logSafely(
                            "custom_hosts fallback_lookup_failed host=$normalized " +
                                "type=${error::class.java.simpleName}"
                        )
                    }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val resolved = (overrides + fallbackAddresses)
                .distinctBy(InetAddress::getHostAddress)
            val ranked = runCatching { rankAddresses(normalized, resolved) }
                .getOrDefault(resolved)
            logSafely(
                "custom_hosts applied host=$normalized custom=${overrides.size} " +
                    "fallback=${fallbackAddresses.size}"
            )
            return ranked
        }
        return systemDns.lookup(hostname)
    }

    private fun logSafely(message: String) {
        runCatching { logger(message) }
    }
}
