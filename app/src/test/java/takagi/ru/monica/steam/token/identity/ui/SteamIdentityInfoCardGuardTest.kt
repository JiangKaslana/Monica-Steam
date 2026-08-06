package takagi.ru.monica.steam.token.identity.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamIdentityInfoCardGuardTest {
    @Test
    fun identityCardKeepsCopyProfileAndM3eBehaviorInItsOwnModule() {
        val cardFile = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/identity/ui/SteamIdentityInfoCard.kt"
        )
        val card = cardFile.readText()

        assertTrue("SteamIdentityInfoCard.kt is too large", cardFile.readLines().size <= 300)
        assertTrue(card.contains("Card("))
        assertTrue(card.contains("MaterialTheme.colorScheme.surfaceContainerLow"))
        assertTrue(card.contains("SteamIdentityConverter.fromSteamId64"))
        assertTrue(card.contains("ClipboardUtils.copyToClipboard("))
        assertTrue(card.contains("sensitive = false"))
        assertTrue(card.contains("LocalUriHandler.current"))
        assertTrue(card.contains("Modifier.fillMaxWidth().heightIn(min = 48.dp)"))
        assertTrue(card.contains("FontFamily.Monospace"))
    }

    @Test
    fun accountDetailUsesTheIdentityCardWithoutDuplicatingItsImplementation() {
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()

        assertTrue(screen.contains("import takagi.ru.monica.steam.token.identity.ui.SteamIdentityInfoCard"))
        assertTrue(screen.contains("SteamIdentityInfoCard(steamId64 = account.steamId)"))
        assertFalse(screen.contains("STEAM_0:"))
        assertFalse(screen.contains("[U:1:"))
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
