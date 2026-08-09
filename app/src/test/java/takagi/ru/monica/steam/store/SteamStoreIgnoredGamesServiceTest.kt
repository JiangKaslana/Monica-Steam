package takagi.ru.monica.steam.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.store.data.SteamStoreIgnoredGamesService
import takagi.ru.monica.steam.store.data.withoutIgnoredGames
import takagi.ru.monica.steam.store.domain.SteamStoreBrowseFilter
import takagi.ru.monica.steam.store.domain.SteamStoreCatalogPage
import takagi.ru.monica.steam.store.domain.SteamStoreHome
import takagi.ru.monica.steam.store.domain.SteamStoreItem

class SteamStoreIgnoredGamesServiceTest {
    @Test
    fun requestEnablesOfficialSteamUserFilters() {
        val request = SteamStoreIgnoredGamesService.buildIgnoredStateRequest(
            appIds = listOf(730, 1091500),
            countryCode = "cn",
            language = "schinese"
        )
        val root = SteamProtoReader(request.toByteArray()).parse()
        val context = SteamProtoReader(requireNotNull(root[2]?.bytes)).parse()
        val dataRequest = SteamProtoReader(requireNotNull(root[3]?.bytes)).parse()

        assertEquals("CN", context[3]?.asString)
        assertTrue(dataRequest[16]?.asBool == true)
    }

    @Test
    fun parserReadsOnlyItemsMarkedIgnoredBySteam() {
        val response = SteamProtoWriter().apply {
            writeMessage(1, storeItem(appId = 730, ignored = true))
            writeMessage(1, storeItem(appId = 1091500, ignored = false))
            writeMessage(1, storeItem(appId = 578080, ignored = true))
        }.toByteArray()

        assertEquals(
            linkedSetOf(730, 578080),
            SteamStoreIgnoredGamesService.parseIgnoredAppIds(response)
        )
    }

    @Test
    fun ignoredGamesAreRemovedFromEveryHomeCollection() {
        val visible = SteamStoreHome(
            specials = listOf(item(1), item(2)),
            topSellers = listOf(item(2), item(3)),
            newReleases = listOf(item(2)),
            comingSoon = listOf(item(4), item(2))
        ).withoutIgnoredGames(setOf(2))

        assertFalse(visible.specials.any { it.appId == 2 })
        assertFalse(visible.topSellers.any { it.appId == 2 })
        assertTrue(visible.newReleases.isEmpty())
        assertFalse(visible.comingSoon.any { it.appId == 2 })
    }

    @Test
    fun catalogKeepsServerPaginationAfterIgnoredItemsAreRemoved() {
        val page = SteamStoreCatalogPage(
            filter = SteamStoreBrowseFilter.TOP_SELLERS,
            items = (1..24).map(::item),
            start = 24,
            totalCount = 100
        ).withoutIgnoredGames(setOf(1, 2, 3))

        assertEquals(21, page.items.size)
        assertEquals(48, page.nextStart)
        assertTrue(page.hasMore)
    }

    @Test
    fun emptyCatalogPageDoesNotBecomeLoadableAfterFiltering() {
        val page = SteamStoreCatalogPage(
            filter = SteamStoreBrowseFilter.TOP_SELLERS,
            items = emptyList(),
            start = 48,
            totalCount = 100
        ).withoutIgnoredGames(setOf(1))

        assertEquals(48, page.nextStart)
        assertFalse(page.hasMore)
    }

    @Test
    fun laterCatalogPageKeepsServerCursorWhenItHasNoIgnoredItems() {
        val firstPage = SteamStoreCatalogPage(
            filter = SteamStoreBrowseFilter.TOP_SELLERS,
            items = (1..24).map(::item),
            start = 0,
            totalCount = 100
        ).withoutIgnoredGames(setOf(1, 2, 3))
        val secondPage = SteamStoreCatalogPage(
            filter = SteamStoreBrowseFilter.TOP_SELLERS,
            items = (25..48).map(::item),
            start = 24,
            totalCount = 100
        ).withoutIgnoredGames(emptySet())

        val merged = secondPage.copy(
            start = 0,
            items = firstPage.items + secondPage.items
        )

        assertEquals(48, merged.nextStart)
        assertTrue(merged.hasMore)
    }

    private fun storeItem(appId: Int, ignored: Boolean): SteamProtoWriter =
        SteamProtoWriter().apply {
            writeVarint(9, appId.toLong())
            writeMessage(70, SteamProtoWriter().apply { writeBool(7, ignored) })
        }

    private fun item(appId: Int) = SteamStoreItem(appId = appId, name = "Game $appId")
}
