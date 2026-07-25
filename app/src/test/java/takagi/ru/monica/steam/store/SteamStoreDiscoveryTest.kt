package takagi.ru.monica.steam.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.data.SteamStoreParser
import takagi.ru.monica.steam.store.data.SteamStoreNavigationPolicy
import takagi.ru.monica.steam.store.domain.SteamStoreBrowseFilter
import takagi.ru.monica.steam.store.domain.SteamStoreHome
import takagi.ru.monica.steam.store.domain.SteamStoreItem
import takagi.ru.monica.steam.store.domain.visibleStoreCollections

class SteamStoreDiscoveryTest {
    @Test
    fun parsesCurrentSaleEventsFromOfficialHomepageMarkup() {
        val html = """
            <div class="home_page_takeunder" style="background: url('desktop.jpg')">
              <a href="https://store.steampowered.com/sale/SimFest2026?snr=1_4"
                 aria-label="模拟游戏节，由独立工作室主办"></a>
            </div>
            <div class="home_area_spotlight" data-ds-appid="2922620">
              <a href="https://store.steampowered.com/sale/SimFest2026?snr=1_4">
                <img data-image-url="https://cdn.example/event.jpg" alt="模拟游戏节">
                <div class="home_capsule_banner">周末特惠</div>
              </a>
            </div>
        """.trimIndent()

        val event = SteamStoreParser.parseDiscoveryEvents(html).single()

        assertEquals("模拟游戏节，由独立工作室主办", event.title)
        assertEquals("周末特惠", event.badge)
        assertEquals("https://cdn.example/event.jpg", event.imageUrl)
        assertEquals("https://store.steampowered.com/sale/SimFest2026", event.url)
    }

    @Test
    fun browseFiltersExposeOnlyRelevantCollections() {
        val game = SteamStoreItem(10, "Game", finalPriceCents = 0)
        val home = SteamStoreHome(
            specials = listOf(game.copy(finalPriceCents = 990)),
            topSellers = listOf(game),
            newReleases = listOf(game),
            comingSoon = listOf(game)
        )

        assertEquals(4, visibleStoreCollections(home, SteamStoreBrowseFilter.ALL).size)
        assertEquals(listOf("specials"), visibleStoreCollections(home, SteamStoreBrowseFilter.SPECIALS).map { it.id })
        assertTrue(visibleStoreCollections(home, SteamStoreBrowseFilter.FREE).single().items.all { it.isFree })
    }

    @Test
    fun eventAndPointsShopLinksStayInsideTrustedSteamWebSurface() {
        assertTrue(
            SteamStoreNavigationPolicy.isAllowed(
                "https://store.steampowered.com/sale/SimFest2026"
            )
        )
        assertTrue(
            SteamStoreNavigationPolicy.isAllowed(
                "https://store.steampowered.com/points/shop/"
            )
        )
    }
}
