package takagi.ru.monica.steam.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamSettingsChildPagesTest {
    @Test
    fun sharedSettingsSupportsCompactHomeAndDedicatedChildModes() {
        val mode = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/SettingsScreenMode.kt"
        ).readText()
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/SettingsScreen.kt"
        ).readText()

        listOf(
            "COMPACT_HOME",
            "DATA_MANAGEMENT",
            "APPEARANCE",
            "ADDITIONAL"
        ).forEach { expected -> assertTrue(mode.contains(expected)) }
        assertTrue(screen.contains("screenMode: SettingsScreenMode = SettingsScreenMode.FULL"))
        assertTrue(screen.contains("val showDataManagementEntry = isCompactHomeMode"))
        assertTrue(screen.contains("val dataManagementEntryMatches = matchesSettingsItem("))
        assertTrue(screen.contains("val showAppearanceEntry = isCompactHomeMode"))
        assertTrue(screen.contains("val appearanceEntryMatches = matchesSettingsItem("))
        assertTrue(screen.contains("if (showAdditionalDetails)"))
        assertTrue(screen.contains("settings_data_management_entry_title"))
        assertTrue(screen.contains("settings_appearance_entry_title"))
    }

    @Test
    fun steamSettingsHomeRoutesCategoriesToIndependentLists() {
        val navigation = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSettingsScreen.kt"
        ).readText()

        assertTrue(navigation.contains("mode = SettingsScreenMode.COMPACT_HOME"))
        assertTrue(navigation.contains("SteamSettingsChild.DATA_MANAGEMENT"))
        assertTrue(navigation.contains("mode = SettingsScreenMode.DATA_MANAGEMENT"))
        assertTrue(navigation.contains("SteamSettingsChild.APPEARANCE"))
        assertTrue(navigation.contains("mode = SettingsScreenMode.APPEARANCE"))
        assertTrue(navigation.contains("SteamSettingsChild.STEAM_FEATURES"))
        assertTrue(navigation.contains("mode = SettingsScreenMode.ADDITIONAL"))
        assertTrue(navigation.contains("SteamSettingsChild.NOTIFICATIONS,"))
        assertTrue(navigation.contains("SteamSettingsChild.STEAM_FEATURES"))
        assertTrue(navigation.contains("onNavigateBack = { child = SteamSettingsChild.STEAM_FEATURES }"))
    }

    @Test
    fun dataAndFeatureChildPagesReuseExistingSteamEntries() {
        val host = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSharedSettingsHost.kt"
        ).readText()

        assertTrue(host.contains("onNavigateToDataManagement = onOpenDataManagement"))
        assertTrue(host.contains("onNavigateToAppearance = onOpenAppearance"))
        assertTrue(host.contains("onNavigateToAdditionalSettings = onOpenSteamFeatures"))
        assertTrue(host.contains("onNavigateToSteamBackup = onOpenMaFileTransfer"))
        assertTrue(host.contains("onNavigateToWebDavBackup = onOpenWebDavBackup"))
        assertTrue(host.contains("onNavigateToMdbx = onOpenMdbx"))
        assertTrue(host.contains("SteamStoreHintSettingsEntry(onClick = onOpenStoreHints)"))
        assertTrue(host.contains("SteamNotificationSettingsEntry(onClick = onOpenNotifications)"))
        assertTrue(
            host.contains("SteamNetworkOptimizationSettingsEntry(onClick = onOpenNetworkOptimization)")
        )
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
