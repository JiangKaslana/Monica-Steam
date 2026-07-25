package takagi.ru.monica.steam.friends.chat.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatEmoticonSizingTest {
    @Test
    fun remoteSteamImagesUpscaleSmallOfficialAssets() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/richmedia/ui/SteamChatRemoteImage.kt"
        ).readText()

        assertTrue(source.contains("ImageView.ScaleType.FIT_CENTER"))
        assertFalse(source.contains("ImageView.ScaleType.CENTER_INSIDE"))
    }

    @Test
    fun emoticonPickerUsesReadableCellsAndMinimalInnerPadding() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/richmedia/ui/SteamChatRichMediaPicker.kt"
        ).readText()

        assertTrue(source.contains("GridCells.Adaptive(56.dp)"))
        assertTrue(source.contains("Modifier.padding(4.dp)"))
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
