package takagi.ru.monica.steam.network.optimization

import java.net.InetAddress
import okhttp3.Dns
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsRuleParser

internal class SteamCustomHostsDns(
    private val systemDns: Dns = Dns.SYSTEM,
    private val customAddresses: (String) -> List<InetAddress> =
        SteamNetworkOptimizationRuntime::addressesForHost,
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
            logSafely("custom_hosts applied host=$normalized addresses=${overrides.size}")
            return overrides
        }
        return systemDns.lookup(hostname)
    }

    private fun logSafely(message: String) {
        runCatching { logger(message) }
    }
}
