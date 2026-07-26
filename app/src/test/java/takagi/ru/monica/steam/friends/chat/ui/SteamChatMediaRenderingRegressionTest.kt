package takagi.ru.monica.steam.friends.chat.ui

import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.friends.chat.richmedia.ui.isAnimatedPng

class SteamChatMediaRenderingRegressionTest {
    @Test
    fun apngPayloadIsRecognizedBeforeStaticBitmapFallback() {
        val apng = png(
            chunk("IHDR", ByteArray(13)),
            chunk("acTL", ByteArray(8)),
            chunk("IDAT", ByteArray(0)),
            chunk("IEND", ByteArray(0))
        )
        val pngWithoutAnimationControl = png(
            chunk("IHDR", ByteArray(13)),
            chunk("IDAT", ByteArray(0)),
            chunk("IEND", ByteArray(0))
        )

        assertTrue(isAnimatedPng(apng))
        assertFalse(isAnimatedPng(pngWithoutAnimationControl))
    }

    @Test
    fun animationStartsOnlyAfterDrawableAttachmentAndPixelArtDisablesCanvasFiltering() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/richmedia/ui/SteamChatRemoteImage.kt"
        ).readText()

        assertTrue(source.contains("setAutoPlay(false)"))
        assertTrue(source.contains("setImageDrawable(currentDrawable)"))
        assertTrue(source.contains("startSteamAnimation(currentDrawable, restart = drawableChanged)"))
        assertTrue(source.contains("BitmapDrawable"))
        assertTrue(source.contains("isFilterBitmap = false"))
        assertTrue(source.contains("isAntiAlias = false"))
        assertFalse(source.contains("setAutoPlay(true)"))
    }

    private fun png(vararg chunks: ByteArray): ByteArray =
        PNG_SIGNATURE + chunks.fold(ByteArray(0)) { result, chunk -> result + chunk }

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val bytes = ByteArray(12 + data.size)
        ByteBuffer.wrap(bytes).putInt(data.size)
        type.encodeToByteArray().copyInto(bytes, destinationOffset = 4)
        data.copyInto(bytes, destinationOffset = 8)
        return bytes
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

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
    }
}
