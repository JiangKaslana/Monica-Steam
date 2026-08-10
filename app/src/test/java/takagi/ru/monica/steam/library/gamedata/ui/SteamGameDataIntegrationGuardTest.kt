package takagi.ru.monica.steam.library.gamedata.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamGameDataIntegrationGuardTest {
    @Test
    fun libraryDetailNavigatesToAuthenticatedGameDataAndDelegatesDownloads() {
        val library = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()
        val web = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/web/ui/SteamWebBrowserScreen.kt"
        ).readText()
        val gameDataWeb = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/gamedata/ui/SteamGameDataWebScreen.kt"
        ).readText()
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()

        assertTrue(library.contains("SteamLibraryDestination.GameData"))
        assertTrue(library.contains("SteamGameDataEntry"))
        assertTrue(library.contains("SteamGameDataWebScreen"))
        assertTrue(web.contains("setDownloadListener"))
        assertTrue(web.contains("onDownloadRequested"))
        assertTrue(gameDataWeb.contains("Intent(Intent.ACTION_VIEW, uri)"))
        assertTrue(gameDataWeb.contains("selector = Intent(Intent.ACTION_MAIN)"))
        assertTrue(gameDataWeb.contains("Intent.CATEGORY_APP_BROWSER"))
        assertTrue(activity.contains("onPlatformViewVisibilityChanged = onPlatformViewVisibilityChanged"))
    }

    private fun projectFile(relativePath: String): File {
        val root = generateSequence(File(System.getProperty("user.dir").orEmpty())) {
            it.parentFile
        }.firstOrNull { File(it, "settings.gradle").isFile }
            ?: error("Project root not found")
        return File(root, relativePath)
    }
}
