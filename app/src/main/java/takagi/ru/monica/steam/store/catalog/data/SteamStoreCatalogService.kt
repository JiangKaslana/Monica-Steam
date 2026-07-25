package takagi.ru.monica.steam.store.catalog.data

import okhttp3.OkHttpClient
import takagi.ru.monica.steam.store.data.buildSteamStoreRequest
import takagi.ru.monica.steam.store.domain.SteamStoreBrowseFilter
import takagi.ru.monica.steam.store.domain.SteamStoreCatalogPage

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
}
