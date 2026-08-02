package takagi.ru.monica.steam.store.catalog.data

import kotlin.math.ceil
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
        wishlistAppIds: Set<Int> = emptySet(),
        limit: Int = 6
    ): List<SteamStoreItem> {
        if (targetMinor <= 0 || countryCode.isBlank() || limit <= 0) return emptyList()
        val upperMinor = maximumCommunityBudgetMinor(targetMinor)
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
            return selectCommunityBudgetSuggestions(
                items = SteamStoreCatalogParser.parse(
                    body,
                    SteamStoreBrowseFilter.TOP_SELLERS
                ).items,
                targetMinor = targetMinor,
                wishlistAppIds = wishlistAppIds,
                limit = limit
            )
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
}

internal fun maximumCommunityBudgetMinor(targetMinor: Int): Int =
    ((targetMinor.toLong() * 110L) / 100L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()

internal fun selectCommunityBudgetSuggestions(
    items: List<SteamStoreItem>,
    targetMinor: Int,
    wishlistAppIds: Set<Int>,
    limit: Int
): List<SteamStoreItem> {
    if (targetMinor <= 0 || limit <= 0) return emptyList()
    val upperMinor = maximumCommunityBudgetMinor(targetMinor)
    return items.asSequence()
        .filter { item ->
            val price = item.finalPriceCents ?: return@filter false
            price in targetMinor..upperMinor
        }
        .sortedWith(
            compareByDescending<SteamStoreItem> { it.appId in wishlistAppIds }
                .thenBy { (it.finalPriceCents ?: Int.MAX_VALUE) - targetMinor }
                .thenByDescending(SteamStoreItem::discountPercent)
                .thenBy(SteamStoreItem::name)
        )
        .take(limit)
        .toList()
}
