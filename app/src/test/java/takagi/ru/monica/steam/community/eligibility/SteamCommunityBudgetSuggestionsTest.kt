package takagi.ru.monica.steam.community.eligibility

import java.util.concurrent.atomic.AtomicReference
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.catalog.data.SteamStoreCatalogService

class SteamCommunityBudgetSuggestionsTest {
    @Test
    fun budgetCatalogUsesAccountRegionAndReturnsGamesThatCoverRemainingSpend() {
        val requestedUrl = AtomicReference<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestedUrl.set(chain.request().url.toString())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(payload().toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val games = SteamStoreCatalogService(client).budgetSuggestions(
            targetMinor = 3_600,
            countryCode = "CN",
            steamLoginSecure = "secure",
            language = "schinese",
            wishlistAppIds = setOf(3),
            limit = 6
        )

        assertEquals(listOf(3, 2, 4), games.map { it.appId })
        assertTrue(games.all { (it.finalPriceCents ?: 0) >= 3_600 })
        assertTrue(games.all { (it.finalPriceCents ?: Int.MAX_VALUE) <= 3_960 })
        assertTrue(requestedUrl.get().contains("cc=CN"))
        assertTrue(requestedUrl.get().contains("maxprice=40"))
        assertTrue(requestedUrl.get().contains("sort_by=Price_DESC"))
    }

    private fun payload(): String {
        val rows = listOf(
            row(1, "Too cheap", "¥ 35.99"),
            row(5, "Over ten percent", "¥ 39.61"),
            row(4, "Ten percent", "¥ 39.60"),
            row(2, "Exact", "¥ 36.00"),
            row(3, "Wishlist", "¥ 39.00")
        ).joinToString("")
        return """{"success":1,"start":0,"total_count":5,"results_html":${json(rows)}}"""
    }

    private fun row(appId: Int, name: String, price: String): String =
        "<a data-ds-appid='$appId' class='search_result_row'>" +
            "<span class='title'>$name</span>" +
            "<div class='discount_final_price'>$price</div></a>"

    private fun json(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else -> append(char)
            }
        }
        append('"')
    }
}
