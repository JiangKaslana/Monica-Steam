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
            limit = 4
        )

        assertEquals(listOf(2, 3, 4), games.map { it.appId })
        assertTrue(games.all { (it.finalPriceCents ?: 0) >= 3_600 })
        assertTrue(requestedUrl.get().contains("cc=CN"))
        assertTrue(requestedUrl.get().contains("maxprice=58"))
        assertTrue(requestedUrl.get().contains("sort_by=Price_DESC"))
    }

    private fun payload(): String {
        val rows = listOf(
            row(1, "Too cheap", "¥ 30.00"),
            row(4, "Far", "¥ 50.00"),
            row(2, "Exact", "¥ 36.00"),
            row(3, "Close", "¥ 40.00")
        ).joinToString("")
        return """{"success":1,"start":0,"total_count":4,"results_html":${json(rows)}}"""
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
