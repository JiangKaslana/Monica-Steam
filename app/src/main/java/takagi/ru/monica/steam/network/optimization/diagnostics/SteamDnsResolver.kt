package takagi.ru.monica.steam.network.optimization.diagnostics

import takagi.ru.monica.steam.network.optimization.domain.SteamDnsProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsResolutionResult

fun interface SteamDnsResolver {
    suspend fun resolve(
        provider: SteamDnsProvider,
        hostname: String
    ): SteamDnsResolutionResult
}
