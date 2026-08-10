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
        val advancedEditor = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/components/SteamHostsAdvancedEditor.kt"
        ).readText()

        assertTrue(application.contains("SteamNetworkOptimizationRuntime.initialize(this)"))
        assertTrue(settings.contains("SteamSettingsChild.NETWORK_OPTIMIZATION"))
        assertTrue(settings.contains("SteamNetworkOptimizationAutoScreen("))
        assertTrue(settings.contains("SteamNetworkOptimizationSettingsScreen("))
        assertTrue(settingsHost.contains("SteamNetworkOptimizationPullCard("))
        assertTrue(provider.contains("SteamCustomHostsDns()"))
        assertFalse(apiClient.contains("SteamCommunityDns"))
        assertTrue(runtime.contains("KEY_CUSTOM_HOSTS"))
        assertTrue(runtime.contains("saveHosts("))
        assertTrue(runtime.contains("applyAutoOptimization("))
        assertTrue(advancedEditor.contains("OutlinedTextField("))
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
    fun automaticOptimizationUsesMultipleResolversAndHttpsVerification() {
        val settingsHost = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSharedSettingsHost.kt"
        ).readText()
        val automaticScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/SteamNetworkOptimizationAutoScreen.kt"
        ).readText()
        val models = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/domain/SteamDnsOptimizationModels.kt"
        ).readText()
        val resolver = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/diagnostics/OkHttpSteamDnsResolver.kt"
        ).readText()
        val scanner = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/diagnostics/SteamDnsOptimizationScanner.kt"
        ).readText()

        assertTrue(settingsHost.contains("homeHeaderContent ="))
        assertTrue(settingsHost.contains("SteamNetworkOptimizationPullCard("))
        assertTrue(automaticScreen.contains("SteamDnsOptimizationScanner()"))
        assertTrue(automaticScreen.contains("applyAutoOptimization(context, result)"))
        assertTrue(models.contains("val DEFAULTS: List<SteamDnsProvider>"))
        assertTrue(resolver.contains("DnsOverHttps.Builder()"))
        assertTrue(scanner.contains("SteamHostProbeTarget(hostname, address)"))
        assertTrue(scanner.contains("it.target.hostname == hostname && it.isAvailable"))
        assertFalse(
            projectFile(
                "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/components/SteamNetworkOptimizationHeroCard.kt"
            ).exists()
        )
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

    @Test
    fun v2UsesProgressiveM3eComponentsInsteadOfAButtonWall() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/SteamNetworkOptimizationSettingsScreen.kt"
        ).readText()
        val overview = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/components/SteamNetworkOverviewCard.kt"
        )
        val rules = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/components/SteamHostsRulesSection.kt"
        )
        val advanced = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/network/optimization/ui/components/SteamHostsAdvancedEditor.kt"
        )

        assertTrue(overview.exists())
        assertTrue(rules.exists())
        assertTrue(advanced.exists())
        assertTrue(screen.contains("SteamNetworkOverviewCard("))
        assertTrue(screen.contains("SteamHostsRulesSection("))
        assertTrue(screen.contains("SteamHostsAdvancedEditor("))
        assertTrue(screen.contains("SnackbarHost("))
        assertTrue(screen.contains("SteamHostsDiagnosticsRunner("))
        assertTrue(overview.readText().contains("LoadingIndicator("))
        assertTrue(overview.readText().contains("Switch("))
        assertTrue(rules.readText().contains("FilledTonalIconButton("))
        assertTrue(advanced.readText().contains("AnimatedVisibility("))
        assertTrue(advanced.readText().contains("LocalReduceAnimations"))
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
