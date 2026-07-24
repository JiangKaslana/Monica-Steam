package takagi.ru.monica.steam.library

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamLibraryProgressSpacingGuardTest {
    @Test
    fun overviewLoadingIndicatorReservesBreathingRoomBeforeHeroCard() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()
        val overview = screen
            .substringAfter("SteamLibraryDestination.Overview ->")
            .substringBefore("if (showAccountSheet")

        assertTrue(overview.contains("Column("))
        assertTrue(overview.contains("Spacer(Modifier.height(8.dp))"))
        assertTrue(overview.contains(".weight(1f)"))
        assertFalse(overview.contains("align(Alignment.TopCenter)"))
    }

    private fun projectFile(path: String): File {
        var dir = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            dir.parentFile != null &&
            !File(dir, "settings.gradle").exists() &&
            !File(dir, "settings.gradle.kts").exists()
        ) {
            dir = dir.parentFile!!.canonicalFile
        }
        return File(dir, path)
    }
}
