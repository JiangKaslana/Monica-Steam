package takagi.ru.monica.steam.network.optimization.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamNetworkOptimizationIntegrationGuardTest {
    @Test
    fun applicationSettingsAndCoreSteamClientsShareTheOptimizationRuntime() {
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

        assertTrue(application.contains("SteamNetworkOptimizationRuntime.initialize(this)"))
        assertTrue(settings.contains("SteamSettingsChild.NETWORK_OPTIMIZATION"))
        assertTrue(settings.contains("SteamNetworkOptimizationSettingsScreen("))
        assertTrue(settingsHost.contains("SteamNetworkOptimizationSettingsEntry("))
        assertTrue(provider.contains("SteamOptimizedDns.create"))

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
    fun togglingOptimizationNeverClosesHttpsSocketsOnTheUiThread() {
        val provider = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/SteamHttpClientProvider.kt"
        ).readText()
        val toggleHandler = provider
            .substringAfter("internal fun onOptimizationChanged()")
            .substringBefore("internal fun clearDnsCache()")

        assertTrue(toggleHandler.contains("dispatcher.executorService.execute"))
        assertTrue(provider.contains("connection_pool_cleanup_failed"))
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
