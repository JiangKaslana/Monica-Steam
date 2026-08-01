package takagi.ru.monica.steam.network.optimization.domain

data class SteamNetworkOptimizationSettings(
    val enabled: Boolean = false
)

object SteamNetworkOptimizationHostPolicy {
    private val exactHosts = setOf(
        "api.steampowered.com",
        "store.steampowered.com",
        "media.steampowered.com",
        "steamcommunity.com"
    )

    private val allowedSuffixes = setOf(
        ".steamcommunity.com",
        ".steamstatic.com",
        ".steamusercontent.com",
        ".steamcontent.com"
    )

    fun shouldOptimize(hostname: String): Boolean {
        val normalized = hostname.trim().trimEnd('.').lowercase()
        if (normalized in exactHosts) return true
        return allowedSuffixes.any(normalized::endsWith)
    }
}
