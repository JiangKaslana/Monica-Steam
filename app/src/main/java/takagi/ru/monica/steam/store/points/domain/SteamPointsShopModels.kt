package takagi.ru.monica.steam.store.points.domain

import kotlinx.serialization.Serializable

@Serializable
enum class SteamPointsShopCategory(val communityItemClasses: List<Int>) {
    FEATURED(emptyList()),
    BACKGROUNDS(listOf(3, 12)),
    EMOTICONS(listOf(4)),
    STICKERS(listOf(10)),
    PROFILE(listOf(8, 13, 14)),
    CHAT_EFFECTS(listOf(11))
}

@Serializable
data class SteamPointsShopItem(
    val appId: Int,
    val definitionId: Int,
    val type: Int,
    val communityItemClass: Int,
    val pointCost: Long,
    val title: String,
    val description: String = "",
    val imageUrl: String = "",
    val animated: Boolean = false
) {
    val officialUrl: String
        get() = "https://store.steampowered.com/points/shop/reward/$definitionId"
}

@Serializable
data class SteamPointsShopPage(
    val category: SteamPointsShopCategory,
    val items: List<SteamPointsShopItem> = emptyList(),
    val totalCount: Int = 0,
    val nextCursor: String? = null,
    val fetchedAt: Long = System.currentTimeMillis()
) {
    val hasMore: Boolean get() = !nextCursor.isNullOrBlank() && items.size < totalCount
}
