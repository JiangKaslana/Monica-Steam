package takagi.ru.monica.steam.network.optimization.domain

data class SteamNetworkOptimizationTarget(
    val hostname: String,
    val minimumProbeAttempts: Int
)

object SteamNetworkTargetCatalog {
    /**
     * Endpoints used directly by Monica Steam. Core API surfaces receive a larger
     * verification budget; image/CDN surfaces still participate without making a
     * normal scan excessively long.
     */
    val DEFAULTS: List<SteamNetworkOptimizationTarget> = listOf(
        SteamNetworkOptimizationTarget("store.steampowered.com", 36),
        SteamNetworkOptimizationTarget("steamcommunity.com", 36),
        SteamNetworkOptimizationTarget("api.steampowered.com", 36),
        SteamNetworkOptimizationTarget("help.steampowered.com", 12),
        SteamNetworkOptimizationTarget("media.steampowered.com", 12),
        SteamNetworkOptimizationTarget("shared.akamai.steamstatic.com", 12),
        SteamNetworkOptimizationTarget("shared.fastly.steamstatic.com", 12),
        SteamNetworkOptimizationTarget("community.akamai.steamstatic.com", 12),
        SteamNetworkOptimizationTarget("community.fastly.steamstatic.com", 12)
    )

    val hostnames: List<String> = DEFAULTS.map(SteamNetworkOptimizationTarget::hostname)
    val minimumProbeAttemptsByHost: Map<String, Int> = DEFAULTS.associate {
        it.hostname to it.minimumProbeAttempts
    }
}
