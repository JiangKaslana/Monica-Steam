package takagi.ru.monica.steam

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamAccountSwitcherIntegrationGuardTest {
    @Test
    fun publicAccountSwitchersExposeTheExistingAddAccountFlow() {
        val switcher = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/ui/SteamAccountSwitcherSheet.kt"
        ).readText()
        val token = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val switcherHosts = listOf(
            "app/src/main/java/takagi/ru/monica/steam/community/ui/SteamCommunityScreen.kt",
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatScreenDialogs.kt",
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt",
            "app/src/main/java/takagi/ru/monica/steam/store/freebie/ui/SteamFreebieScreen.kt",
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).map { projectFile(it).readText() }
        val addAccountRow = switcher.substringAfterLast("HorizontalDivider()")

        assertTrue(switcher.contains("onAddAccount: () -> Unit"))
        assertTrue(addAccountRow.contains("Icons.Default.PersonAdd"))
        assertTrue(addAccountRow.contains("R.string.steam_add_account_title"))
        assertTrue(addAccountRow.contains("heightIn(min = 64.dp)"))
        assertTrue(addAccountRow.indexOf("onDismiss()") < addAccountRow.indexOf("onAddAccount()"))
        switcherHosts.forEach { source ->
            assertTrue(source.contains("onAddSteamAccount"))
            assertTrue(source.contains("onAddAccount = onAddSteamAccount"))
        }

        assertTrue(token.contains("openAddAccountOnEntry: Boolean = false"))
        assertTrue(token.contains("LaunchedEffect(openAddAccountOnEntry)"))
        assertTrue(token.contains("onAddAccountEntryConsumed()"))
        assertTrue(activity.contains("pendingAddSteamAccount"))
        assertTrue(activity.contains("openAddAccountOnEntry = pendingAddSteamAccount"))
        assertTrue(activity.contains("onAddAccountEntryConsumed = {"))
        assertEquals(
            4,
            Regex("onAddSteamAccount = ::openSteamAccountAddition")
                .findAll(activity)
                .count()
        )
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!
        }
        return File(directory, path)
    }
}
