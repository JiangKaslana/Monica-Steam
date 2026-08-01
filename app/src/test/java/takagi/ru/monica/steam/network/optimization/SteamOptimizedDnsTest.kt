package takagi.ru.monica.steam.network.optimization

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamOptimizedDnsTest {
    @Test
    fun disabledOptimizationUsesSystemDns() {
        val secure = RecordingDns(listOf(InetAddress.getByName("1.1.1.1")))
        val system = RecordingDns(listOf(InetAddress.getByName("8.8.8.8")))
        val dns = SteamOptimizedDns(
            secureResolvers = listOf(secure),
            systemDns = system,
            optimizationEnabled = { false },
            logger = {}
        )

        val result = dns.lookup("api.steampowered.com")

        assertEquals(listOf("8.8.8.8"), result.map(InetAddress::getHostAddress))
        assertFalse(secure.called)
        assertTrue(system.called)
    }

    @Test
    fun enabledOptimizationUsesSecureDnsForAllowlistedSteamHosts() {
        val secure = RecordingDns(listOf(InetAddress.getByName("23.45.67.89")))
        val system = RecordingDns(listOf(InetAddress.getByName("8.8.8.8")))
        val dns = SteamOptimizedDns(
            secureResolvers = listOf(secure),
            systemDns = system,
            optimizationEnabled = { true },
            logger = {}
        )

        val result = dns.lookup("store.steampowered.com")

        assertEquals(listOf("23.45.67.89"), result.map(InetAddress::getHostAddress))
        assertTrue(secure.called)
        assertFalse(system.called)
    }

    @Test
    fun secureFailureFallsBackToSystemDns() {
        val secure = RecordingDns(error = UnknownHostException("doh unavailable"))
        val system = RecordingDns(listOf(InetAddress.getByName("8.8.4.4")))
        val dns = SteamOptimizedDns(
            secureResolvers = listOf(secure),
            systemDns = system,
            optimizationEnabled = { true },
            logger = {}
        )

        val result = dns.lookup("api.steampowered.com")

        assertEquals(listOf("8.8.4.4"), result.map(InetAddress::getHostAddress))
        assertTrue(secure.called)
        assertTrue(system.called)
    }

    @Test
    fun unrelatedHostsNeverUseSecureDns() {
        val secure = RecordingDns(listOf(InetAddress.getByName("1.1.1.1")))
        val system = RecordingDns(listOf(InetAddress.getByName("8.8.8.8")))
        val dns = SteamOptimizedDns(
            secureResolvers = listOf(secure),
            systemDns = system,
            optimizationEnabled = { true },
            logger = {}
        )

        dns.lookup("api.github.com")

        assertFalse(secure.called)
        assertTrue(system.called)
    }

    @Test
    fun successfulSteamResolutionIsCachedUntilCleared() {
        val secure = RecordingDns(listOf(InetAddress.getByName("23.45.67.89")))
        val dns = SteamOptimizedDns(
            secureResolvers = listOf(secure),
            systemDns = RecordingDns(listOf(InetAddress.getByName("8.8.8.8"))),
            optimizationEnabled = { true },
            logger = {}
        )

        dns.lookup("cdn.cloudflare.steamstatic.com")
        dns.lookup("cdn.cloudflare.steamstatic.com")
        assertEquals(1, secure.callCount)

        dns.clearCache()
        dns.lookup("cdn.cloudflare.steamstatic.com")
        assertEquals(2, secure.callCount)
    }

    private class RecordingDns(
        private val result: List<InetAddress> = emptyList(),
        private val error: UnknownHostException? = null
    ) : Dns {
        var callCount: Int = 0
            private set
        val called: Boolean get() = callCount > 0

        override fun lookup(hostname: String): List<InetAddress> {
            callCount++
            error?.let { throw it }
            return result
        }
    }
}
