package takagi.ru.monica.steam.network.optimization.domain

/**
 * Conservative Steam Hosts seed for users on networks where the default route is unreliable.
 *
 * The preset only selects destination IPs. It never changes HTTPS hostname/SNI, certificate
 * verification, request encryption, or WebView TLS policy. Normal DNS/DoH fallback stays enabled
 * when the preset is applied, so an aging CDN address does not permanently block a hostname.
 *
 * Seed source: gamehublite/gamehub_api game/getSteamHost/index, reviewed for this preset.
 */
object SteamBuiltInHostsPreset {
    const val VERSION = 1
    const val SOURCE_REVISION = "2025-06-30"

    val hostsText: String = """
        # Monica Steam built-in safe Hosts preset v1
        # TLS/SNI/certificate verification remains unchanged; DNS fallback remains enabled.
        23.47.27.74 steamcommunity.com
        104.94.121.98 www.steamcommunity.com
        23.45.149.185 store.steampowered.com
        23.47.27.74 api.steampowered.com
        23.47.27.74 help.steampowered.com
        23.53.35.201 store.akamai.steamstatic.com
        23.215.0.49 steamcdn-a.akamaihd.net
        23.53.35.199 cdn.akamai.steamstatic.com
        104.94.121.98 steam-chat.com
        23.53.35.197 community.akamai.steamstatic.com
    """.trimIndent()

    val parsed: SteamHostsParseResult by lazy { SteamHostsRuleParser.parse(hostsText) }
}
