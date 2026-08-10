package takagi.ru.monica.steam.network.optimization.diagnostics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsOptimizationScanResult
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsResolutionResult
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsScanProgress
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsScanStage
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsSelectedRoute
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeResult
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeStatus
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeTarget

internal class SteamDnsOptimizationScanner(
    private val resolver: SteamDnsResolver = OkHttpSteamDnsResolver(),
    private val probe: SteamHostProbe = OkHttpSteamHostProbe(),
    private val providers: List<SteamDnsProvider> = SteamDnsProvider.DEFAULTS,
    private val targetHostnames: List<String> = DEFAULT_TARGET_HOSTNAMES,
    private val maxCandidatesPerHost: Int = 8,
    private val maxConcurrentProbes: Int = 4
) {
    suspend fun scan(
        onProgress: (SteamDnsScanProgress) -> Unit = {}
    ): SteamDnsOptimizationScanResult = coroutineScope {
        val resolutionTasks = providers.flatMap { provider ->
            targetHostnames.map { hostname -> provider to hostname }
        }
        val resolutionDeferred = resolutionTasks.map { (provider, hostname) ->
            async(Dispatchers.IO) { resolveSafely(provider, hostname) }
        }
        val resolutions = mutableListOf<SteamDnsResolutionResult>()
        resolutionDeferred.forEachIndexed { index, deferred ->
            val result = deferred.await()
            resolutions += result
            onProgress(
                SteamDnsScanProgress(
                    stage = SteamDnsScanStage.RESOLVING,
                    completed = index + 1,
                    total = resolutionDeferred.size,
                    currentSource = result.provider.displayName
                )
            )
        }

        val candidates = buildCandidates(resolutions)
        val probeSemaphore = Semaphore(maxConcurrentProbes.coerceAtLeast(1))
        val probeDeferred = candidates.map { candidate ->
            async(Dispatchers.IO) {
                probeSemaphore.withPermit {
                    probeSafely(candidate.hostname, candidate.address)
                }
            }
        }
        val probeResults = mutableListOf<SteamHostProbeResult>()
        probeDeferred.forEachIndexed { index, deferred ->
            val result = deferred.await()
            probeResults += result
            onProgress(
                SteamDnsScanProgress(
                    stage = SteamDnsScanStage.VERIFYING,
                    completed = index + 1,
                    total = probeDeferred.size,
                    currentSource = result.target.hostname
                )
            )
        }

        val candidateSources = candidates.associate { candidate ->
            candidate.key to candidate.providerIds
        }
        val selectedRoutes = targetHostnames.mapNotNull { hostname ->
            probeResults
                .asSequence()
                .filter { it.target.hostname == hostname && it.isAvailable }
                .minWithOrNull(
                    compareBy<SteamHostProbeResult> { it.latencyMillis ?: Long.MAX_VALUE }
                        .thenBy { it.target.address }
                )
                ?.let { result ->
                    SteamDnsSelectedRoute(
                        hostname = hostname,
                        address = result.target.address,
                        providerIds = candidateSources[result.target.key].orEmpty(),
                        latencyMillis = result.latencyMillis ?: 0L,
                        httpStatusCode = result.httpStatusCode
                    )
                }
        }

        SteamDnsOptimizationScanResult(
            targetHostnames = targetHostnames,
            resolutions = resolutions,
            probeResults = probeResults,
            selectedRoutes = selectedRoutes
        )
    }

    private suspend fun resolveSafely(
        provider: SteamDnsProvider,
        hostname: String
    ): SteamDnsResolutionResult = try {
        resolver.resolve(provider, hostname)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        SteamDnsResolutionResult(
            provider = provider,
            hostname = hostname,
            errorType = error::class.java.simpleName
        )
    }

    private suspend fun probeSafely(hostname: String, address: String): SteamHostProbeResult = try {
        probe.probe(SteamHostProbeTarget(hostname, address))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        SteamHostProbeResult(
            target = SteamHostProbeTarget(hostname, address),
            status = SteamHostProbeStatus.CONNECTION_ERROR,
            errorType = error::class.java.simpleName
        )
    }

    private fun buildCandidates(
        resolutions: List<SteamDnsResolutionResult>
    ): List<SteamDnsCandidate> = targetHostnames.flatMap { hostname ->
        val hostResolutions = resolutions
            .asSequence()
            .filter { it.hostname == hostname && it.isAvailable }
            .sortedBy { it.latencyMillis ?: Long.MAX_VALUE }
            .toList()
        val providerIdsByAddress = linkedMapOf<String, MutableSet<String>>()
        hostResolutions.forEach { resolution ->
            resolution.addresses.forEach { address ->
                providerIdsByAddress.getOrPut(address) { linkedSetOf() }
                    .add(resolution.provider.id)
            }
        }

        val roundRobinAddresses = buildList {
            val largestAnswer = hostResolutions.maxOfOrNull { it.addresses.size } ?: 0
            repeat(largestAnswer) { addressIndex ->
                hostResolutions.forEach { resolution ->
                    resolution.addresses.getOrNull(addressIndex)?.let { address ->
                        if (address !in this) add(address)
                    }
                }
            }
        }

        roundRobinAddresses
            .take(maxCandidatesPerHost.coerceAtLeast(1))
            .map { address ->
                SteamDnsCandidate(
                    hostname = hostname,
                    address = address,
                    providerIds = providerIdsByAddress[address].orEmpty().toList()
                )
            }
    }

    private data class SteamDnsCandidate(
        val hostname: String,
        val address: String,
        val providerIds: List<String>
    ) {
        val key: String get() = "$hostname|$address"
    }

    companion object {
        val DEFAULT_TARGET_HOSTNAMES: List<String> = listOf(
            "store.steampowered.com",
            "steamcommunity.com",
            "api.steampowered.com"
        )
    }
}
