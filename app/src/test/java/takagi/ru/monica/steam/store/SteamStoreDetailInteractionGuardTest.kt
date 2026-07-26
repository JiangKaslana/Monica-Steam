package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreDetailInteractionGuardTest {
    @Test
    fun screenshotsOpenTheDedicatedFullscreenViewer() {
        val detailUi = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val viewerUi = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/gallery/SteamStoreScreenshotGallery.kt"
        )
        val sharedViewer = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/media/SteamFullscreenImageViewer.kt"
        )
        val sharedPage = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/media/SteamFullscreenImagePage.kt"
        )
        val downloader = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/media/SteamImageDownloader.kt"
        )

        assertTrue(viewerUi.exists())
        assertTrue(sharedViewer.exists())
        assertTrue(sharedPage.exists())
        assertTrue(downloader.exists())
        val viewerSource = viewerUi.readText()
        val sharedViewerSource = sharedViewer.readText()
        val sharedPageSource = sharedPage.readText()
        assertTrue(detailUi.contains("itemsIndexed(detail.screenshots"))
        assertTrue(detailUi.contains("SteamStoreScreenshotViewer("))
        assertTrue(detailUi.contains("selectedScreenshotIndex = index"))
        assertTrue(viewerSource.contains("SteamFullscreenImageViewer("))
        assertTrue(sharedViewerSource.contains("DialogProperties("))
        assertTrue(sharedViewerSource.contains("usePlatformDefaultWidth = false"))
        assertTrue(sharedViewerSource.contains("HorizontalPager("))
        assertTrue(sharedViewerSource.contains("rememberPagerState("))
        assertTrue(sharedViewerSource.contains("initialPage = initialIndex.coerceIn"))
        assertTrue(sharedViewerSource.contains("pagerState.animateScrollToPage"))
        assertTrue(sharedViewerSource.contains("background(Color.Black)"))
        assertTrue(sharedViewerSource.contains("SteamImageDownloader("))
        assertTrue(sharedPageSource.contains("ContentScale.Fit"))
        assertTrue(sharedPageSource.contains("canPan = { scale > 1f }"))
        assertTrue(viewerSource.contains("R.string.steam_store_screenshot_previous"))
        assertTrue(viewerSource.contains("R.string.steam_store_screenshot_next"))
        assertTrue(viewerSource.contains("R.string.steam_store_screenshot_download"))
    }

    @Test
    fun fullDetailsAndInformationValuesAreSelectable() {
        val detailUi = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()

        assertTrue(detailUi.contains("detail.about.ifBlank { detail.shortDescription }"))
        val detailSection = detailUi.substring(
            detailUi.indexOf("fun DetailTextSection"),
            detailUi.indexOf("fun DetailLine")
        )
        val detailLine = detailUi.substring(
            detailUi.indexOf("fun DetailLine"),
            detailUi.indexOf("fun PriceRow")
        )
        assertTrue(detailSection.contains("SelectionContainer"))
        assertTrue(detailLine.contains("SelectionContainer"))
    }

    @Test
    fun reviewExpansionActionFollowsThePreviewCards() {
        val reviewList = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreReviewList.kt"
        ).readText()
        val reviewSummary = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreReviewSummary.kt"
        ).readText()
        val zhStrings = projectFile("app/src/main/res/values-zh/strings.xml").readText()

        assertFalse(reviewSummary.contains("onOpenAll"))
        assertFalse(reviewSummary.contains("steam_store_reviews_view_all"))
        assertTrue(zhStrings.contains("<string name=\"steam_store_reviews_show_more\">查看更多</string>"))
        val reviewCardsIndex = reviewList.indexOf("visibleReviews.forEach")
        val moreButtonIndex = reviewList.indexOf("R.string.steam_store_reviews_show_more")
        assertTrue(reviewCardsIndex >= 0)
        assertTrue(moreButtonIndex > reviewCardsIndex)
        assertTrue(reviewList.contains("Modifier.fillMaxWidth().heightIn(min = 48.dp)"))
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
