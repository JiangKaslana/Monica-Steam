package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreFilterUiGuardTest {
    @Test
    fun advancedFiltersUseProgressiveDisclosureAndAccessibleM3Controls() {
        val sheet = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/filters/ui/SteamStoreAdvancedFilterSheet.kt"
        ).readText()
        val menu = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreBrowseMenu.kt"
        ).readText()
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()

        assertTrue(sheet.contains("ModalBottomSheet("))
        assertTrue(sheet.contains("tagsExpanded"))
        assertTrue(sheet.contains("OutlinedTextField("))
        assertTrue(sheet.contains("take(DEFAULT_VISIBLE_TAGS)"))
        assertTrue(sheet.contains("FilterChip("))
        assertTrue(sheet.contains("heightIn(min = 48.dp)"))
        assertTrue(sheet.contains("SteamStoreActiveFilterSummary("))
        assertTrue(sheet.contains("SteamStoreTagBadges("))
        assertTrue(menu.contains("activeFilterCount"))
        assertTrue(menu.contains("BadgedBox("))
        assertTrue(menu.contains("steam_store_advanced_filters"))
        assertTrue(screen.contains("state.storeFilters.isActive"))
        assertTrue(screen.contains("hintSettings.storeTagsEnabled"))
        assertTrue(screen.contains("viewModel.applyStoreFilters(selection)"))
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
