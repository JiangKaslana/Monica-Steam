package takagi.ru.monica.steam.links.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamExternalLinkRouterTest {
    @Test
    fun storeAppLinkOpensNativeDetail() {
        assertEquals(
            SteamExternalLinkTarget.StoreApp(1091500),
            SteamExternalLinkRouter.route(
                "https://store.steampowered.com/app/1091500/Cyberpunk_2077/?snr=1"
            )
        )
    }

    @Test
    fun numericCommunityProfileOpensNativeProfile() {
        assertEquals(
            SteamExternalLinkTarget.CommunityProfile("76561198000000000"),
            SteamExternalLinkRouter.route(
                "https://steamcommunity.com/profiles/76561198000000000/"
            )
        )
    }

    @Test
    fun shortAndOtherSteamLinksOpenTrustedWebView() {
        assertEquals(
            SteamExternalLinkTarget.Web("https://s.team/p/example"),
            SteamExternalLinkRouter.route("http://s.team/p/example")
        )
        assertEquals(
            SteamExternalLinkTarget.Web("https://steamcommunity.com/id/joyin/"),
            SteamExternalLinkRouter.route("https://steamcommunity.com/id/joyin/")
        )
    }

    @Test
    fun lookalikeAndNonWebLinksAreRejected() {
        assertNull(SteamExternalLinkRouter.route("https://store.steampowered.com.evil.example/app/730"))
        assertNull(SteamExternalLinkRouter.route("javascript:alert(1)"))
    }

    @Test
    fun manifestRegistersTheThreeSupportedSteamHosts() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android.intent.action.VIEW"))
        assertTrue(manifest.contains("android:host=\"s.team\""))
        assertTrue(manifest.contains("android:host=\"steamcommunity.com\""))
        assertTrue(manifest.contains("android:host=\"store.steampowered.com\""))
    }

    private fun projectFile(relativePath: String): File {
        val root = generateSequence(File(System.getProperty("user.dir").orEmpty())) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle").isFile }
            ?: error("Project root not found")
        return File(root, relativePath)
    }
}
