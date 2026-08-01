package takagi.ru.monica.steam.store.catalog.data

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import okhttp3.OkHttpClient
import takagi.ru.monica.steam.store.data.buildSteamStoreRequest
import takagi.ru.monica.steam.store.domain.SteamStoreBrowseFilter
import takagi.ru.monica.steam.store.domain.SteamStoreCatalogPage
import takagi.ru.monica.steam.store.domain.SteamStoreItem

internal class SteamStoreCatalogService(private val client: OkHttpClient) {
    fun page(
        filter: SteamStoreBrowseFilter,
        start: Int,
        count: Int,
        language: String,
        countryCode: String?,
        steamLoginSecure: String?
    ): SteamStoreCatalogPage {
        require(filter != SteamStoreBrowseFilter.ALL)
        val request = buildSteamStoreRequest(
            path = "/search/results/",
            query = buildQuery(filter, start, count, language),
            steamLoginSecure = steamLoginSecure,
            countryCode = countryCode
        )
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Steam 商店目录请求失败：${response.code}")
            }
            val body = response.body?.string()?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Steam 商店目录返回空数据")
            return SteamStoreCatalogParser.parse(body, filter)
        }
    }

    fun budgetSuggestions(
        targetMinor: Int,
        countryCode: String,
        steamLoginSecure: String?,
        language: String,
        limit: Int = 6
    ): List<SteamStoreItem> {
        if (targetMinor <= 0 || countryCode.isBlank() || limit <= 0) return emptyList()
        val upperMinor = max(
            targetMinor + MINIMUM_BUDGET_WINDOW_MINOR,
            (targetMinor * BUDGET_WINDOW_MULTIPLIER).toInt()
        )
        val request = buildSteamStoreRequest(
            path = "/search/results/",
            query = buildBudgetQuery(
                upperMinor = upperMinor,
                language = language
            ),
            steamLoginSecure = steamLoginSecure,
            countryCode = countryCode
        )
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Steam 预算目录请求失败：${response.code}")
            }
            val body = response.body?.string()?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Steam 预算目录返回空数据")
            return SteamStoreCatalogParser.parse(body, SteamStoreBrowseFilter.TOP_SELLERS)
                .items
                .asSequence()
                .filter { item ->
                    val price = item.finalPriceCents ?: return@filter false
                    price in targetMinor..upperMinor
                }
                .sortedWith(
                    compareBy<SteamStoreItem> {
                        abs((it.finalPriceCents ?: Int.MAX_VALUE) - targetMinor)
                    }.thenByDescending(SteamStoreItem::discountPercent)
                        .thenBy(SteamStoreItem::name)
                )
                .take(limit)
                .toList()
        }
    }

    private fun buildQuery(
        filter: SteamStoreBrowseFilter,
        start: Int,
        count: Int,
        language: String
    ): Map<String, String> = buildMap {
        put("query", "")
        put("start", start.coerceAtLeast(0).toString())
        put("count", count.coerceIn(1, 50).toString())
        put("dynamic_data", "")
        put("infinite", "1")
        put("l", language)
        put("category1", "998")
        when (filter) {
            SteamStoreBrowseFilter.SPECIALS -> put("specials", "1")
            SteamStoreBrowseFilter.TOP_SELLERS -> put("filter", "topsellers")
            SteamStoreBrowseFilter.NEW_RELEASES -> put("sort_by", "Released_DESC")
            SteamStoreBrowseFilter.COMING_SOON -> put("filter", "comingsoon")
            SteamStoreBrowseFilter.FREE -> put("maxprice", "free")
            SteamStoreBrowseFilter.ALL -> Unit
        }
    }

    private fun buildBudgetQuery(
        upperMinor: Int,
        language: String
    ): Map<String, String> = mapOf(
        "query" to "",
        "start" to "0",
        "count" to "50",
        "dynamic_data" to "",
        "infinite" to "1",
        "l" to language,
        "category1" to "998",
        "maxprice" to ceil(upperMinor / 100.0).toInt().toString(),
        "sort_by" to "Price_DESC"
    )

    private companion object {
        const val MINIMUM_BUDGET_WINDOW_MINOR = 500
        const val BUDGET_WINDOW_MULTIPLIER = 1.6
    }
}
