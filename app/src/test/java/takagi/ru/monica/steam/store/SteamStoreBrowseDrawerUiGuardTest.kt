package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreBrowseDrawerUiGuardTest {
    @Test
    fun browseCategoriesUseAnM3SideDrawerInsteadOfADropdown() {
        val drawer = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreBrowseDrawer.kt"
        ).readText()
        val discovery = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreDiscoveryContent.kt"
        ).readText()
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()

        assertTrue(drawer.contains("ModalNavigationDrawer("))
        assertTrue(drawer.contains("ModalDrawerSheet("))
        assertTrue(drawer.contains("NavigationDrawerItem("))
        assertTrue(drawer.contains("rememberDrawerState("))
        assertTrue(drawer.contains("LayoutDirection.Rtl"))
        assertTrue(drawer.contains("onOpenPointsShop"))
        assertFalse(discovery.contains("DropdownMenu("))
        assertFalse(discovery.contains("DropdownMenuItem("))
        assertTrue(screen.contains("SteamStoreBrowseDrawer("))
        assertTrue(screen.contains("onOpenDrawer = openBrowseDrawer"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (directory.parentFile != null && !File(directory, "settings.gradle").exists()) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
