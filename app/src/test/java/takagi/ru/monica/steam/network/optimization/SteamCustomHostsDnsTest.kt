package takagi.ru.monica.steam.network.optimization

import java.net.InetAddress
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamCustomHostsDnsTest {
    @Test
    fun exactUserMappingOverridesSystemDns() {
        val system = RecordingDns(listOf(InetAddress.getByName("8.8.8.8")))
        val dns = SteamCustomHostsDns(
            systemDns = system,
            customAddresses = { hostname ->
                if (hostname == "store.steampowered.com") {
                    listOf(InetAddress.getByName("23.45.67.89"))
                } else {
                    emptyList()
                }
            },
            fallbackToSystemDns = { false },
            logger = {}
        )

        val result = dns.lookup("STORE.STEAMPOWERED.COM.")

        assertEquals(listOf("23.45.67.89"), result.map(InetAddress::getHostAddress))
        assertFalse(system.called)
    }

    @Test
    fun enabledFallbackAppendsSystemAddressesAndRecordsTheCustomHit() {
        val system = RecordingDns(listOf(InetAddress.getByName("8.8.8.8")))
        val hits = mutableListOf<String>()
        val dns = SteamCustomHostsDns(
            systemDns = system,
            customAddresses = {
                listOf(InetAddress.getByName("23.45.67.89"))
            },
            fallbackToSystemDns = { true },
            onCustomHostsUsed = { hostname -> hits += hostname },
            logger = {}
        )

        val result = dns.lookup("store.steampowered.com")

        assertEquals(
            listOf("23.45.67.89", "8.8.8.8"),
            result.map(InetAddress::getHostAddress)
        )
        assertEquals(listOf("store.steampowered.com"), hits)
        assertTrue(system.called)
    }

    @Test
    fun missingUserMappingAlwaysUsesSystemDns() {
        val system = RecordingDns(listOf(InetAddress.getByName("8.8.4.4")))
        val requestedHosts = mutableListOf<String>()
        val dns = SteamCustomHostsDns(
            systemDns = system,
            customAddresses = { hostname ->
                requestedHosts += hostname
                emptyList()
            },
            logger = {}
        )

        val result = dns.lookup("api.steampowered.com")

        assertEquals(listOf("8.8.4.4"), result.map(InetAddress::getHostAddress))
        assertEquals(listOf("api.steampowered.com"), requestedHosts)
        assertTrue(system.called)
    }

    @Test
    fun unusableUserAddressFallsBackToSystemDns() {
        val system = RecordingDns(listOf(InetAddress.getByName("1.1.1.1")))
        val dns = SteamCustomHostsDns(
            systemDns = system,
            customAddresses = { listOf(InetAddress.getByName("127.0.0.1")) },
            logger = {}
        )

        val result = dns.lookup("steamcommunity.com")

        assertEquals(listOf("1.1.1.1"), result.map(InetAddress::getHostAddress))
        assertTrue(system.called)
    }

    private class RecordingDns(
        private val result: List<InetAddress>
    ) : Dns {
        var callCount: Int = 0
            private set
        val called: Boolean get() = callCount > 0

        override fun lookup(hostname: String): List<InetAddress> {
            callCount++
            return result
        }
    }
}
