package takagi.ru.monica.steam.library.screenshots.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamGameScreenshotsIntegrationGuardTest {
    @Test
    fun libraryDetailNavigatesToAuthenticatedGameScreenshotsAndReturns() {
        val library = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()
        val screenshots = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/screenshots/ui/SteamGameScreenshotsWebScreen.kt"
        )

        assertTrue(library.contains("SteamLibraryDestination.Screenshots"))
        assertTrue(library.contains("SteamGameScreenshotsEntry"))
        assertTrue(library.contains("SteamGameScreenshotsWebScreen"))
        assertTrue(screenshots.isFile)
        assertTrue(screenshots.readText().contains("requireAuthenticatedSession = true"))
        assertTrue(screenshots.readText().contains("SteamWebClientMode.COMMUNITY_DESKTOP"))
    }

    private fun projectFile(relativePath: String): File {
        val root = generateSequence(File(System.getProperty("user.dir").orEmpty())) {
            it.parentFile
        }.firstOrNull { File(it, "settings.gradle").isFile }
            ?: error("Project root not found")
        return File(root, relativePath)
    }
}
