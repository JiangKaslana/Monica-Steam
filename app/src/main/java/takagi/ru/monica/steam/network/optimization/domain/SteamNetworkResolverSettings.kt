package takagi.ru.monica.steam.network.optimization.domain

import java.net.InetAddress
import java.net.IDN
import java.net.URI

data class SteamNetworkResolverSettings(
    val useSystemDns: Boolean = true,
    val useBuiltInDoh: Boolean = true,
    val customDnsServers: List<String> = emptyList(),
    val customDohEndpoints: List<String> = emptyList(),
    val preferredProviderIds: List<String> = emptyList(),
    val dynamicDnsEnabled: Boolean = false,
    val disabledBuiltInProviderIds: Set<String> = emptySet(),
    val preferIpv6: Boolean = false
) {
    val configuredProviders: List<SteamDnsProvider>
        get() = buildList {
            if (useSystemDns) add(SteamDnsProvider.SYSTEM)
            if (useBuiltInDoh) addAll(SteamDnsProvider.DEFAULTS.filterNot { it.isSystem })
            addAll(customDnsServers.map(SteamDnsProvider::customDns))
            addAll(customDohEndpoints.map(SteamDnsProvider::customDoh))
        }.distinctBy(SteamDnsProvider::id)

    val activeProviders: List<SteamDnsProvider>
        get() {
            val available = configuredProviders.filterNot { provider ->
                provider.isDoh && provider.id in disabledBuiltInProviderIds &&
                    SteamDnsProvider.DEFAULTS.any { it.id == provider.id }
            }
            if (preferredProviderIds.isEmpty()) return available

            val byId = available.associateBy(SteamDnsProvider::id)
            return buildList {
                preferredProviderIds.forEach { providerId ->
                    byId[providerId]?.let { provider ->
                        if (none { it.id == provider.id }) add(provider)
                    }
                }
                available.forEach { provider ->
                    if (none { it.id == provider.id }) add(provider)
                }
            }
        }

    val hasResolver: Boolean get() = activeProviders.isNotEmpty()
    val hasPreferredProviders: Boolean get() = preferredProviderIds.isNotEmpty()

    fun isProviderEnabled(provider: SteamDnsProvider): Boolean =
        activeProviders.any { it.id == provider.id }

    companion object {
        const val MAX_CUSTOM_DNS = 8
        const val MAX_CUSTOM_DOH = 8
    }
}

object SteamResolverInputValidator {
    fun normalizeDnsServer(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > 253 || trimmed.any(Char::isWhitespace)) {
            return null
        }
        if ('/' in trimmed || '?' in trimmed || '#' in trimmed || '@' in trimmed) return null
        val unwrapped = trimmed.removePrefix("[").removeSuffix("]")
        if ('[' in unwrapped || ']' in unwrapped) return null
        if (isIpv4(unwrapped) || isIpv6(unwrapped)) return unwrapped.lowercase()
        if (':' in unwrapped) return null
        val normalized = runCatching {
            IDN.toASCII(SteamHostsRuleParser.normalizeHostname(unwrapped))
        }.getOrNull() ?: return null
        return normalized.takeIf(::isValidResolverHostname)
    }

    fun normalizeDohEndpoint(raw: String): String? {
        val value = raw.trim().takeIf { it.length in 1..512 } ?: return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null) return null
        if (uri.port !in listOf(-1, 443)) return null
        return uri.normalize().toASCIIString()
    }

    private fun isIpv4(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) &&
            part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    private fun isIpv6(value: String): Boolean {
        if (':' !in value || !value.matches(IPV6_LITERAL)) return false
        return runCatching { InetAddress.getByName(value).hostAddress }.isSuccess
    }

    private fun isValidResolverHostname(hostname: String): Boolean =
        hostname.length in 1..253 && hostname.contains('.') && hostname.split('.').all { label ->
            label.length in 1..63 &&
                label.first().isLetterOrDigit() &&
                label.last().isLetterOrDigit() &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }

    private val IPV6_LITERAL = Regex("[0-9a-fA-F:.]+")
}
