package takagi.ru.monica.steam.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.store.points.data.buildSteamPointsShopQuery
import takagi.ru.monica.steam.store.points.data.parseSteamPointsBalance
import takagi.ru.monica.steam.store.points.data.parseSteamPointsShopPage
import takagi.ru.monica.steam.store.points.domain.SteamPointsShopCategory

class SteamPointsShopProtocolTest {
    @Test
    fun queryUsesOfficialCategoryLanguageAndPagingFields() {
        val request = buildSteamPointsShopQuery(
            category = SteamPointsShopCategory.STICKERS,
            language = "schinese",
            count = 24,
            cursor = "next"
        )
        val query = SteamProtoReader(request.toByteArray()).parse()[1]?.bytes!!
        val fields = SteamProtoReader(query).parse()

        assertEquals(listOf(10L), SteamProtoReader.decodePackedVarints(fields[3]?.bytes!!))
        assertEquals("schinese", fields[4]?.asString)
        assertEquals(24, fields[5]?.asInt)
        assertEquals("next", fields[6]?.asString)
    }

    @Test
    fun parsesRewardDefinitionAndCursor() {
        val communityData = SteamProtoWriter().apply {
            writeString(2, "Animated sticker")
            writeString(3, "A reward description")
            writeString(5, "asset.png")
            writeBool(8, true)
        }
        val definition = SteamProtoWriter().apply {
            writeVarint(1, 730L)
            writeVarint(2, 42L)
            writeVarint(3, 1L)
            writeVarint(4, 10L)
            writeVarint(6, 1_000L)
            writeMessage(13, communityData)
        }
        val queryResponse = SteamProtoWriter().apply {
            writeMessage(1, definition)
            writeVarint(2, 30L)
            writeVarint(3, 1L)
            writeString(4, "cursor-2")
        }
        val batchResponse = SteamProtoWriter().apply {
            writeVarint(1, 1L)
            writeMessage(2, queryResponse)
        }
        val response = SteamProtoWriter().apply { writeMessage(1, batchResponse) }

        val page = parseSteamPointsShopPage(
            response.toByteArray(),
            SteamPointsShopCategory.STICKERS
        )

        val item = page.items.single()
        assertEquals(42, item.definitionId)
        assertEquals(1_000L, item.pointCost)
        assertEquals("Animated sticker", item.title)
        assertTrue(item.animated)
        assertTrue(item.imageUrl.endsWith("/730/asset.png"))
        assertEquals("cursor-2", page.nextCursor)
        assertTrue(page.hasMore)
    }

    @Test
    fun parsesAuthenticatedPointsBalance() {
        val summary = SteamProtoWriter().apply { writeVarint(1, 12_345L) }
        val response = SteamProtoWriter().apply { writeMessage(1, summary) }
        assertEquals(12_345L, parseSteamPointsBalance(response.toByteArray()))
    }
}
