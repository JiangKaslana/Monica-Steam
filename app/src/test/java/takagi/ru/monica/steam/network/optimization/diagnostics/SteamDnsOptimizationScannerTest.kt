package takagi.ru.monica.steam.network.optimization.diagnostics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsResolutionResult
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsScanProgress
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsSelectedRoute
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
            minimumProbeAttemptsPerCandidate = 1,
            minimumProbeAttemptsPerHost = 1,
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
            recoveryProbe = SteamHostProbe { target ->
                SteamHostProbeResult(
                    target = target,
                    status = SteamHostProbeStatus.TIMEOUT,
                    latencyMillis = 5_000L
                )
            },
            providers = listOf(providerA),
            targetHostnames = listOf(hostA, hostB),
            minimumProbeAttemptsPerCandidate = 1,
            minimumProbeAttemptsPerHost = 1
        )

        val result = scanner.scan()

        assertFalse(result.isComplete)
        assertTrue(result.isApplicable)
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
            maxCandidatesPerHost = 2,
            minimumProbeAttemptsPerCandidate = 1,
            minimumProbeAttemptsPerHost = 1
        )

        val result = scanner.scan()

        assertTrue(result.isComplete)
        assertEquals("20.0.0.1", result.selectedRoutes.single().address)
        assertEquals(2, result.probeResults.size)
    }

    @Test
    fun repeatedVerificationRejectsFlakyLowLatencyOutlierAndUsesMedian() = runBlocking {
        val attempts = ConcurrentHashMap<String, AtomicInteger>()
        val scanner = SteamDnsOptimizationScanner(
            resolver = SteamDnsResolver { provider, hostname ->
                SteamDnsResolutionResult(
                    provider = provider,
                    hostname = hostname,
                    addresses = listOf("10.0.0.1", "10.0.0.2")
                )
            },
            probe = SteamHostProbe { target ->
                val attempt = attempts
                    .computeIfAbsent(target.address) { AtomicInteger() }
                    .incrementAndGet()
                when (target.address) {
                    "10.0.0.1" -> SteamHostProbeResult(
                        target = target,
                        status = if (attempt == 1) {
                            SteamHostProbeStatus.AVAILABLE
                        } else {
                            SteamHostProbeStatus.TIMEOUT
                        },
                        latencyMillis = if (attempt == 1) 5L else 5_000L,
                        httpStatusCode = if (attempt == 1) 200 else null
                    )
                    else -> SteamHostProbeResult(
                        target = target,
                        status = SteamHostProbeStatus.AVAILABLE,
                        latencyMillis = listOf(20L, 22L, 200L, 24L, 25L)[attempt - 1],
                        httpStatusCode = 200
                    )
                }
            },
            providers = listOf(providerA),
            targetHostnames = listOf(hostA),
            minimumProbeAttemptsPerCandidate = 5,
            minimumProbeAttemptsPerHost = 5,
            maxConcurrentProbes = 2
        )

        val result = scanner.scan()

        assertTrue(result.isComplete)
        assertEquals(10, result.probeResults.size)
        assertEquals("10.0.0.2", result.selectedRoutes.single().address)
        assertEquals(24L, result.selectedRoutes.single().latencyMillis)
    }

    @Test
    fun majorityOfSuccessfulChecksKeepsAMobileCandidateUsable() = runBlocking {
        val attempts = AtomicInteger()
        val scanner = SteamDnsOptimizationScanner(
            resolver = SteamDnsResolver { provider, hostname ->
                SteamDnsResolutionResult(
                    provider = provider,
                    hostname = hostname,
                    addresses = listOf("10.0.0.1")
                )
            },
            probe = SteamHostProbe { target ->
                val attempt = attempts.incrementAndGet()
                SteamHostProbeResult(
                    target = target,
                    status = if (attempt <= 3) {
                        SteamHostProbeStatus.AVAILABLE
                    } else {
                        SteamHostProbeStatus.TIMEOUT
                    },
                    latencyMillis = if (attempt <= 3) 40L else 5_000L,
                    httpStatusCode = if (attempt <= 3) 200 else null
                )
            },
            providers = listOf(providerA),
            targetHostnames = listOf(hostA),
            minimumProbeAttemptsPerCandidate = 5,
            minimumProbeAttemptsPerHost = 5,
            maxConcurrentProbes = 1
        )

        val result = scanner.scan()

        assertTrue(result.isComplete)
        assertEquals("10.0.0.1", result.selectedRoutes.single().address)
    }

    @Test
    fun defaultBudgetRunsAtLeastOneHundredHttpsChecks() = runBlocking {
        val scanner = SteamDnsOptimizationScanner(
            resolver = SteamDnsResolver { provider, hostname ->
                val hostIndex = SteamDnsOptimizationScanner.DEFAULT_TARGET_HOSTNAMES
                    .indexOf(hostname)
                SteamDnsResolutionResult(
                    provider = provider,
                    hostname = hostname,
                    addresses = listOf("10.0.0.${hostIndex + 1}")
                )
            },
            probe = SteamHostProbe { target ->
                SteamHostProbeResult(
                    target = target,
                    status = SteamHostProbeStatus.AVAILABLE,
                    latencyMillis = 30L,
                    httpStatusCode = 200
                )
            },
            providers = listOf(providerA)
        )

        val result = scanner.scan()

        assertTrue(result.isComplete)
        assertEquals(180, result.probeResults.size)
    }

    @Test
    fun missingHostIsResolvedAndVerifiedAgainBeforeFinishing() = runBlocking {
        val resolveCalls = AtomicInteger()
        val scanner = SteamDnsOptimizationScanner(
            resolver = SteamDnsResolver { provider, hostname ->
                resolveCalls.incrementAndGet()
                SteamDnsResolutionResult(
                    provider = provider,
                    hostname = hostname,
                    addresses = listOf("10.0.0.1")
                )
            },
            probe = SteamHostProbe { target ->
                SteamHostProbeResult(
                    target = target,
                    status = SteamHostProbeStatus.TIMEOUT,
                    latencyMillis = 5_000L
                )
            },
            recoveryProbe = SteamHostProbe { target ->
                SteamHostProbeResult(
                    target = target,
                    status = SteamHostProbeStatus.AVAILABLE,
                    latencyMillis = 45L,
                    httpStatusCode = 200
                )
            },
            providers = listOf(providerA),
            targetHostnames = listOf(hostA),
            minimumProbeAttemptsPerCandidate = 1,
            minimumProbeAttemptsPerHost = 1,
            minimumRecoveryProbeAttemptsPerCandidate = 2,
            minimumRecoveryProbeAttemptsPerHost = 2
        )

        val result = scanner.scan()

        assertTrue(result.isComplete)
        assertEquals(2, resolveCalls.get())
        assertEquals(3, result.probeResults.size)
        assertEquals(45L, result.selectedRoutes.single().latencyMillis)
    }

    @Test
    fun verifiedExistingRouteIsKeptWhenItBeatsNewResolverResults() = runBlocking {
        val scanner = SteamDnsOptimizationScanner(
            resolver = SteamDnsResolver { provider, hostname ->
                SteamDnsResolutionResult(
                    provider = provider,
                    hostname = hostname,
                    addresses = listOf("10.0.0.2")
                )
            },
            probe = SteamHostProbe { target ->
                SteamHostProbeResult(
                    target = target,
                    status = SteamHostProbeStatus.AVAILABLE,
                    latencyMillis = if (target.address == "10.0.0.1") 18L else 60L,
                    httpStatusCode = 200
                )
            },
            providers = listOf(providerA),
            targetHostnames = listOf(hostA),
            minimumProbeAttemptsPerCandidate = 1,
            minimumProbeAttemptsPerHost = 1
        )

        val result = scanner.scan(
            preferredRoutes = listOf(
                SteamDnsSelectedRoute(
                    hostname = hostA,
                    address = "10.0.0.1",
                    providerIds = listOf("system"),
                    latencyMillis = 25L,
                    httpStatusCode = 200
                )
            )
        )

        assertEquals("10.0.0.1", result.selectedRoutes.single().address)
        assertEquals(18L, result.selectedRoutes.single().latencyMillis)
        assertEquals(2, result.probeResults.size)
    }
}
