package takagi.ru.monica.steam.network.optimization.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamNetworkOptimizationHostPolicyTest {
    @Test
    fun onlyExplicitSteamApiStoreCommunityAndMediaHostsAreOptimized() {
        listOf(
            "api.steampowered.com",
            "store.steampowered.com",
            "steamcommunity.com",
            "chat.steamcommunity.com",
            "cdn.cloudflare.steamstatic.com",
            "community.akamai.steamstatic.com",
            "media.steampowered.com"
        ).forEach { hostname ->
            assertTrue(hostname, SteamNetworkOptimizationHostPolicy.shouldOptimize(hostname))
        }
    }

    @Test
    fun authenticationPaymentAndUnrelatedHostsStayOnSystemDns() {
        listOf(
            "login.steampowered.com",
            "checkout.steampowered.com",
            "help.steampowered.com",
            "api.github.com",
            "example.com",
            "steampowered.com.evil.example"
        ).forEach { hostname ->
            assertFalse(hostname, SteamNetworkOptimizationHostPolicy.shouldOptimize(hostname))
        }
    }
}
