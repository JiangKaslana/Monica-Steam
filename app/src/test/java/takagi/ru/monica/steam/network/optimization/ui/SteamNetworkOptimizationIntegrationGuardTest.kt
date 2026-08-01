package takagi.ru.monica.steam.network.optimization.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamNetworkOptimizationIntegrationGuardTest {
    @Test
    fun applicationSettingsAndCoreSteamClientsShareTheCustomHostsRuntime() {
        val application = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamApplication.kt"
        ).readText()
        val settings = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSettingsScreen.kt"
        ).readText()
        val settingsHost = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSharedSettingsHost.kt"
        ).readText()
        val provider = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/SteamHttpClientProvider.kt"
        ).readText()
        val apiClient = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/SteamApiClient.kt"
        ).readText()
        val runtime = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/SteamNetworkOptimizationRuntime.kt"
        ).readText()
        val optimizationScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/SteamNetworkOptimizationSettingsScreen.kt"
        ).readText()

        assertTrue(application.contains("SteamNetworkOptimizationRuntime.initialize(this)"))
        assertTrue(settings.contains("SteamSettingsChild.NETWORK_OPTIMIZATION"))
        assertTrue(settings.contains("SteamNetworkOptimizationSettingsScreen("))
        assertTrue(settingsHost.contains("SteamNetworkOptimizationSettingsEntry("))
        assertTrue(provider.contains("SteamCustomHostsDns()"))
        assertFalse(apiClient.contains("SteamCommunityDns"))
        assertTrue(runtime.contains("KEY_CUSTOM_HOSTS"))
        assertTrue(runtime.contains("saveHosts("))
        assertTrue(optimizationScreen.contains("OutlinedTextField("))
        assertTrue(optimizationScreen.contains("SteamHostsRuleParser.parse("))
        assertTrue(optimizationScreen.contains("SteamNetworkOptimizationRuntime.saveHosts("))
        assertFalse(
            projectFile(
                "app/src/main/java/takagi/ru/monica/steam/network/optimization/SteamOptimizedDns.kt"
            ).exists()
        )
        assertFalse(
            projectFile(
                "app/src/main/java/takagi/ru/monica/steam/network/SteamCommunityDns.kt"
            ).exists()
        )

        listOf(
            "app/src/main/java/takagi/ru/monica/steam/network/SteamApiClient.kt",
            "app/src/main/java/takagi/ru/monica/steam/store/data/SteamStoreService.kt",
            "app/src/main/java/takagi/ru/monica/steam/token/data/SteamLoginImportService.kt",
            "app/src/main/java/takagi/ru/monica/steam/foundation/media/SteamImageDownloader.kt",
            "app/src/main/java/takagi/ru/monica/steam/profile/SteamRemoteImageCache.kt",
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/richmedia/data/SteamChatAttachmentUploader.kt",
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/avatar/data/SteamGroupAvatarUploader.kt"
        ).forEach { path ->
            assertTrue(path, projectFile(path).readText().contains("SteamHttpClientProvider"))
        }
    }

    @Test
    fun applyingCustomHostsNeverClosesHttpsSocketsOnTheUiThread() {
        val provider = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/SteamHttpClientProvider.kt"
        ).readText()
        val toggleHandler = provider
            .substringAfter("internal fun onCustomHostsChanged()")
            .substringBefore("private fun logConnectionPoolCleanupFailure")

        assertTrue(toggleHandler.contains("dispatcher.executorService.execute"))
        assertTrue(provider.contains("connection_pool_cleanup_failed"))
        assertFalse(provider.contains("DnsOverHttps"))
        assertFalse(provider.contains("dns.alidns.com"))
        assertFalse(provider.contains("doh.pub"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = requireNotNull(directory.parentFile)
        }
        return File(directory, path)
    }
}
