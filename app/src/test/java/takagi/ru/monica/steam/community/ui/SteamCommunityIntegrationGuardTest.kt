package takagi.ru.monica.steam.community.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamCommunityIntegrationGuardTest {
    @Test
    fun communityOpensAsAnIndependentSecondaryPageFromTheCapsuleMenu() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val tokenScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/community/ui/SteamCommunityScreen.kt"
        ).readText()
        val dockPages = activity
            .substringAfter("private fun MonicaSteamPage.isDockPage()")
            .substringBefore("private fun MonicaSteamPage.toDockTab()")

        assertTrue(activity.contains("MonicaSteamPage.COMMUNITY"))
        assertTrue(activity.contains("SteamCommunityScreen("))
        assertTrue(activity.contains("pendingCommunitySteamId"))
        assertTrue(dockPages.contains("MonicaSteamPage.COMMUNITY"))
        assertTrue(tokenScreen.contains("onOpenCommunity"))
        assertTrue(tokenScreen.contains("R.string.steam_community_title"))
        assertTrue(screen.contains("ExpressiveTopBar("))
        assertTrue(screen.contains("SteamExpressivePullToRefresh("))
        assertTrue(screen.contains("SteamAccountSwitcherSheet("))
        assertTrue(screen.contains("initialSteamId"))
        assertTrue(screen.contains("accountSource.selectAccount(requestedAccount.id)"))
        assertTrue(screen.contains("statusBarsPadding()"))
        assertFalse(screen.contains("TopAppBar("))
    }

    @Test
    fun communityKeepsDataPresentationAndSmallUiFilesSeparated() {
        val root = projectFile("app/src/main/java/takagi/ru/monica/steam/community")
        assertTrue(root.resolve("domain").isDirectory)
        assertTrue(root.resolve("data").isDirectory)
        assertTrue(root.resolve("presentation").isDirectory)
        assertTrue(root.resolve("ui").isDirectory)
        assertTrue(root.listFiles().orEmpty().none { it.extension == "kt" })

        root.resolve("ui").listFiles().orEmpty()
            .filter { it.extension == "kt" }
            .forEach { file ->
                assertTrue("${file.name} is too large", file.readLines().size <= 300)
            }
    }

    @Test
    fun communityUsesCorrectOAuthProfileQueryAndPerSteamIdCache() {
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/community/data/SteamCommunityService.kt"
        ).readText()
        val cache = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/community/data/SteamCommunityCache.kt"
        ).readText()
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/community/presentation/SteamCommunityViewModel.kt"
        ).readText()

        assertTrue(service.contains("\"steamids\" to account.steamId"))
        assertTrue(service.contains("SteamCommunitySection.entries.size"))
        assertTrue(cache.contains("key(snapshot.accountSteamId)"))
        assertTrue(cache.contains("it.accountSteamId == accountSteamId"))
        assertTrue(viewModel.contains("activeAccount?.steamId == account.steamId"))
        assertTrue(viewModel.contains("requestGeneration == generation"))
        assertTrue(viewModel.contains("sessionResolver.resolveOrKeep"))
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
