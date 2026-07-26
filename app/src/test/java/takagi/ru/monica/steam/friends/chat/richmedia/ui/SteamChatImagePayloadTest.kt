package takagi.ru.monica.steam.friends.chat.richmedia.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatImagePayloadTest {
    @Test
    fun detectsAnimatedPngFromItsActlChunk() {
        assertTrue(isAnimatedPng(pngWithChunk("acTL")))
    }

    @Test
    fun doesNotTreatOrdinaryPngAsAnimated() {
        assertFalse(isAnimatedPng(pngWithChunk("IDAT")))
    }

    @Test
    fun rejectsActlTextOutsideAPngContainer() {
        assertFalse(isAnimatedPng("not-a-png-acTL".encodeToByteArray()))
    }

    private fun pngWithChunk(chunkName: String): ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x00,
        chunkName[0].code.toByte(),
        chunkName[1].code.toByte(),
        chunkName[2].code.toByte(),
        chunkName[3].code.toByte(),
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00
    )
}
