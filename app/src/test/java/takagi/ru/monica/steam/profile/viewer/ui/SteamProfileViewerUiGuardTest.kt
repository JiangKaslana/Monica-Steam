package takagi.ru.monica.steam.profile.viewer.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamProfileViewerUiGuardTest {
    @Test
    fun selfAndFriendEntriesUseSharedProfileViewer() {
        val library = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/library/ui/SteamLibraryScreen.kt"
        ).readText()
        val friends = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/ui/SteamFriendsScreen.kt"
        ).readText()
        val friendDetail = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/ui/SteamFriendDetailScreen.kt"
        ).readText()

        assertTrue(library.contains("SteamLibraryDestination.Profile"))
        assertTrue(library.contains("SteamProfileViewerScreen("))
        assertTrue(library.contains("steam_profile_open_game_profile"))
        assertTrue(friends.contains("SteamFriendsDestination.Profile"))
        assertTrue(friends.contains("SteamProfileViewerTarget("))
        assertTrue(friendDetail.contains("onOpenProfile"))
        assertTrue(friendDetail.contains("steam_profile_open_game_profile"))
    }

    @Test
    fun profileViewerKeepsM3eTouchAndSafeAreaRules() {
        val overview = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/profile/viewer/ui/SteamProfileViewerOverview.kt"
        ).readText()
        val comparison = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/profile/viewer/ui/SteamProfileAchievementComparison.kt"
        ).readText()
        val filter = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/profile/viewer/ui/SteamProfileAchievementFilterSplitButton.kt"
        ).readText()

        assertTrue(overview.contains("LocalSteamDockContentClearance"))
        assertTrue(overview.contains("statusBarsPadding()"))
        assertTrue(overview.contains("SingleChoiceSegmentedButtonRow"))
        assertTrue(overview.contains("heightIn(min = 48.dp)"))
        assertTrue(comparison.contains("LocalSteamDockContentClearance"))
        assertTrue(comparison.contains("statusBarsPadding()"))
        assertTrue(comparison.contains("heightIn(min = 48.dp)"))
        assertTrue(comparison.contains("selfProfile"))
        assertTrue(filter.contains("SplitButtonLayout("))
        assertTrue(filter.contains("heightIn(min = 48.dp)"))
    }

    @Test
    fun serviceLoadsGamesLazilyAndCacheKeysContainBothAccounts() {
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/profile/viewer/data/SteamProfileViewerService.kt"
        ).readText()
        val cache = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/profile/viewer/data/SteamProfileViewerCache.kt"
        ).readText()

        assertTrue(service.contains("GetAchievementsProgress").not())
        assertTrue(service.contains("fetchAchievementComparison("))
        assertTrue(service.contains("ACHIEVEMENT_PROGRESS_BATCH_SIZE = 100"))
        assertTrue(cache.contains("${'$'}viewerSteamId|${'$'}targetSteamId"))
        assertTrue(cache.contains("${'$'}viewerSteamId|${'$'}targetSteamId|${'$'}appId"))
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
