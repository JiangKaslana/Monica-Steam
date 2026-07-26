package takagi.ru.monica.steam.friends.chat.ui

import java.io.File
import androidx.compose.ui.graphics.FilterQuality
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRemoteImageMode
import takagi.ru.monica.steam.friends.chat.richmedia.ui.staticSteamImageFilterQuality

class SteamChatEmoticonSizingTest {
    @Test
    fun remoteSteamImagesUseNearestNeighborForPixelEmoticons() {
        assertEquals(
            FilterQuality.None,
            staticSteamImageFilterQuality(SteamChatRemoteImageMode.EMOTICON)
        )
        assertEquals(
            FilterQuality.High,
            staticSteamImageFilterQuality(SteamChatRemoteImageMode.CONTENT)
        )
    }

    @Test
    fun remoteSteamImagesKeepAnimationAndStickerSizingPolicies() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/richmedia/ui/SteamChatRemoteImage.kt"
        ).readText()

        assertTrue(source.contains("ImageView.ScaleType.FIT_CENTER"))
        assertTrue(source.contains("ImageView.ScaleType.CENTER_INSIDE"))
        assertTrue(source.contains("ContentScale.Inside"))
        assertTrue(source.contains("setAutoPlay(true)"))
        assertTrue(source.contains("setVisible(true, true)"))
        assertTrue(source.contains("doOnLayout"))
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
