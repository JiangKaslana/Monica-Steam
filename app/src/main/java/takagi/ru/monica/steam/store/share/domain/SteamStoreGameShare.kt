package takagi.ru.monica.steam.store.share.domain

import takagi.ru.monica.steam.store.domain.SteamStoreDetail

data class SteamStoreGameShare(
    val appId: Int,
    val name: String,
    val storeUrl: String
) {
    val messageBody: String
        get() = "$name\n$storeUrl"
}

internal fun SteamStoreDetail.toGameShare(): SteamStoreGameShare = SteamStoreGameShare(
    appId = appId,
    name = name,
    storeUrl = storeUrl
)
