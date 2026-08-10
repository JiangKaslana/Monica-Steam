package takagi.ru.monica.steam.network.optimization.diagnostics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsResolutionResult
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsScanProgress
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeResult
import takagi.ru.monica.steam.network.optimization.domain.SteamHostProbeStatus

class SteamDnsOptimizationScannerTest {
    private val providerA = SteamDnsProvider("a", "Provider A")
    private val providerB = SteamDnsProvider("b", "Provider B")
    private val hostA = "store.steampowered.com"
    private val hostB = "steamcommunity.com"

    @Test
    fun selectsFastestVerifiedAddressAndKeepsAllSourcesForDuplicates() = runBlocking {
        val progress = mutableListOf<SteamDnsScanProgress>()
        val scanner = SteamDnsOptimizationScanner(
            resolver = SteamDnsResolver { provider, hostname ->
                val addresses = when (provider.id to hostname) {
                    "a" to hostA -> listOf("10.0.0.1")
                    "b" to hostA -> listOf("10.0.0.2")
                    "a" to hostB,
                    "b" to hostB -> listOf("20.0.0.1")
                    else -> emptyList()
                }
                SteamDnsResolutionResult(
                    provider = provider,
                    hostname = hostname,
                    addresses = addresses,
                    latencyMillis = if (provider.id == "a") 20L else 30L
                )
            },
            probe = SteamHostProbe { target ->
                val latency = when (target.address) {
                    "10.0.0.1" -> 90L
                    "10.0.0.2" -> 25L
                    else -> 40L
                }
                SteamHostProbeResult(
                    target = target,
                    status = SteamHostProbeStatus.AVAILABLE,
                    latencyMillis = latency,
                    httpStatusCode = 200
                )
            },
            providers = listOf(providerA, providerB),
            targetHostnames = listOf(hostA, hostB),
            maxConcurrentProbes = 2
        )

        val result = scanner.scan(progress::add)

        assertTrue(result.isComplete)
        assertEquals("10.0.0.2", result.selectedRoutes.first { it.hostname == hostA }.address)
        assertEquals(
            listOf("a", "b"),
            result.selectedRoutes.first { it.hostname == hostB }.providerIds
        )
        assertEquals(32L, result.averageLatencyMillis)
        assertEquals(4, progress.count { it.stage.name == "RESOLVING" })
        assertEquals(3, progress.count { it.stage.name == "VERIFYING" })
    }

    @Test
    fun incompleteVerificationNeverReportsACompleteOptimization() = runBlocking {
        val scanner = SteamDnsOptimizationScanner(
            resolver = SteamDnsResolver { provider, hostname ->
                SteamDnsResolutionResult(
                    provider = provider,
                    hostname = hostname,
                    addresses = listOf(if (hostname == hostA) "10.0.0.1" else "20.0.0.1")
                )
            },
            probe = SteamHostProbe { target ->
                SteamHostProbeResult(
                    target = target,
                    status = if (target.hostname == hostA) {
                        SteamHostProbeStatus.AVAILABLE
                    } else {
                        SteamHostProbeStatus.TIMEOUT
                    },
                    latencyMillis = 50L
                )
            },
            providers = listOf(providerA),
            targetHostnames = listOf(hostA, hostB)
        )

        val result = scanner.scan()

        assertFalse(result.isComplete)
        assertEquals(1, result.availableHostCount)
        assertEquals(listOf(hostA), result.selectedRoutes.map { it.hostname })
    }

    @Test
    fun candidateLimitStillSamplesEveryResolverBeforeExtraAddresses() = runBlocking {
        val scanner = SteamDnsOptimizationScanner(
            resolver = SteamDnsResolver { provider, hostname ->
                SteamDnsResolutionResult(
                    provider = provider,
                    hostname = hostname,
                    addresses = if (provider.id == "a") {
                        listOf("10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.4")
                    } else {
                        listOf("20.0.0.1")
                    },
                    latencyMillis = if (provider.id == "a") 10L else 20L
                )
            },
            probe = SteamHostProbe { target ->
                SteamHostProbeResult(
                    target = target,
                    status = SteamHostProbeStatus.AVAILABLE,
                    latencyMillis = if (target.address == "20.0.0.1") 5L else 50L,
                    httpStatusCode = 200
                )
            },
            providers = listOf(providerA, providerB),
            targetHostnames = listOf(hostA),
            maxCandidatesPerHost = 2
        )

        val result = scanner.scan()

        assertTrue(result.isComplete)
        assertEquals("20.0.0.1", result.selectedRoutes.single().address)
        assertEquals(2, result.probeResults.size)
    }
}
