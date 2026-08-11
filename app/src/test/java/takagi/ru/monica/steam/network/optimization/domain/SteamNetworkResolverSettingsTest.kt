package takagi.ru.monica.steam.network.optimization.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamNetworkResolverSettingsTest {
    @Test
    fun combinesEnabledDefaultsAndCustomSources() {
        val settings = SteamNetworkResolverSettings(
            useSystemDns = false,
            useBuiltInDoh = true,
            customDnsServers = listOf("1.1.1.1"),
            customDohEndpoints = listOf("https://resolver.example/dns-query")
        )

        assertTrue(settings.hasResolver)
        assertEquals(7, settings.activeProviders.size)
        assertFalse(settings.activeProviders.any(SteamDnsProvider::isSystem))
        assertTrue(settings.activeProviders.any { it.udpServer == "1.1.1.1" })
        assertTrue(settings.activeProviders.any { it.dohUrl?.contains("resolver.example") == true })
    }

    @Test
    fun builtInDefaultsArePublicDohProvidersBeyondSystemFallback() {
        val publicBuiltIns = SteamDnsProvider.DEFAULTS.filterNot(SteamDnsProvider::isSystem)

        assertTrue(publicBuiltIns.isNotEmpty())
        assertTrue(publicBuiltIns.all { it.isDoh })
        assertTrue(publicBuiltIns.all { it.dohUrl?.startsWith("https://") == true })
        assertFalse(publicBuiltIns.any { it.id.startsWith("custom_") })
    }

    @Test
    fun disabledBuiltInProviderIsRemovedFromActiveProviders() {
        val settings = SteamNetworkResolverSettings(
            useSystemDns = false,
            useBuiltInDoh = true,
            disabledBuiltInProviderIds = setOf(SteamDnsProvider.CLOUDFLARE.id)
        )

        assertFalse(settings.activeProviders.any { it.id == SteamDnsProvider.CLOUDFLARE.id })
        assertTrue(settings.activeProviders.any { it.id == SteamDnsProvider.DNSPOD.id })
    }

    @Test
    fun customDohRemainsCustomAndNeverBecomesABuiltInDefault() {
        val customEndpoint = "https://resolver.example/dns-query"
        val settings = SteamNetworkResolverSettings(
            customDohEndpoints = listOf(customEndpoint)
        )

        assertTrue(settings.configuredProviders.any { it.dohUrl == customEndpoint })
        assertFalse(SteamDnsProvider.DEFAULTS.any { it.dohUrl == customEndpoint })
    }

    @Test
    fun validatesDnsAndDohWithoutAcceptingPortsOrUnsafeSchemes() {
        assertEquals("1.1.1.1", SteamResolverInputValidator.normalizeDnsServer(" 1.1.1.1 "))
        assertEquals(
            "dns.example.com",
            SteamResolverInputValidator.normalizeDnsServer("DNS.Example.Com")
        )
        assertTrue(
            SteamResolverInputValidator.normalizeDnsServer("[2606:4700:4700::1111]")
                ?.contains(':') == true
        )
        assertNull(SteamResolverInputValidator.normalizeDnsServer("1.1.1.1:53"))
        assertNull(SteamResolverInputValidator.normalizeDnsServer("dns server.example"))

        assertEquals(
            "https://resolver.example/dns-query",
            SteamResolverInputValidator.normalizeDohEndpoint(
                "https://resolver.example/dns-query"
            )
        )
        assertNull(
            SteamResolverInputValidator.normalizeDohEndpoint(
                "http://resolver.example/dns-query"
            )
        )
        assertNull(
            SteamResolverInputValidator.normalizeDohEndpoint(
                "https://user:pass@resolver.example/dns-query"
            )
        )
        assertNull(
            SteamResolverInputValidator.normalizeDohEndpoint(
                "https://resolver.example:8443/dns-query"
            )
        )
    }
}
