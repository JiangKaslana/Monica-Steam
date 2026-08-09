package takagi.ru.monica.steam.store.data

import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.store.domain.SteamStoreCatalogPage
import takagi.ru.monica.steam.store.domain.SteamStoreHome
import takagi.ru.monica.steam.store.domain.SteamStoreItem

internal class SteamStoreIgnoredGamesService(
    private val api: SteamApiClient
) {
    fun ignoredAppIds(
        appIds: Collection<Int>,
        accessToken: String,
        countryCode: String,
        language: String = "schinese"
    ): Set<Int> {
        val ids = appIds.asSequence().filter { it > 0 }.distinct().toList()
        if (ids.isEmpty() || accessToken.isBlank()) return emptySet()
        return ids.chunked(MAX_ITEMS_PER_REQUEST).flatMapTo(linkedSetOf()) { batch ->
            parseIgnoredAppIds(
                api.callProtobuf(
                    iface = "IStoreBrowseService",
                    method = "GetItems",
                    request = buildIgnoredStateRequest(batch, countryCode, language),
                    accessToken = accessToken,
                    useGet = true
                )
            )
        }
    }

    companion object {
        private const val MAX_ITEMS_PER_REQUEST = 50

        internal fun buildIgnoredStateRequest(
            appIds: Collection<Int>,
            countryCode: String,
            language: String
        ): SteamProtoWriter = SteamProtoWriter().apply {
            appIds.asSequence().filter { it > 0 }.distinct().forEach { appId ->
                writeMessage(1, SteamProtoWriter().apply { writeVarint(1, appId.toLong()) })
            }
            writeMessage(2, SteamProtoWriter().apply {
                writeString(1, language)
                writeString(3, countryCode.trim().uppercase())
            })
            writeMessage(3, SteamProtoWriter().apply {
                writeBool(16, true)
            })
        }

        internal fun parseIgnoredAppIds(response: ByteArray): Set<Int> =
            SteamProtoReader(response).parseAll()
                .asSequence()
                .filter { it.number == 1 && it.bytes != null }
                .mapNotNull { field ->
                    val item = runCatching {
                        SteamProtoReader(field.bytes ?: return@mapNotNull null).parse()
                    }.getOrNull() ?: return@mapNotNull null
                    val appId = item[9]?.asLong?.toInt()?.takeIf { it > 0 }
                        ?: return@mapNotNull null
                    val ignored = item[70]?.bytes?.let { failure ->
                        SteamProtoReader(failure).parse()[7]?.asBool
                    } == true
                    appId.takeIf { ignored }
                }
                .toCollection(linkedSetOf())
    }
}

internal fun SteamStoreHome.withoutIgnoredGames(ignoredAppIds: Set<Int>): SteamStoreHome {
    if (ignoredAppIds.isEmpty()) return this
    fun List<SteamStoreItem>.visible() = filterNot { it.appId in ignoredAppIds }
    return copy(
        specials = specials.visible(),
        topSellers = topSellers.visible(),
        newReleases = newReleases.visible(),
        comingSoon = comingSoon.visible()
    )
}

internal fun SteamStoreCatalogPage.withoutIgnoredGames(
    ignoredAppIds: Set<Int>
): SteamStoreCatalogPage {
    if (items.isEmpty()) return this
    val serverNextStart = nextStart
    return copy(
        items = if (ignoredAppIds.isEmpty()) items else items.filterNot {
            it.appId in ignoredAppIds
        },
        nextStartOverride = serverNextStart
    )
}

internal fun List<SteamStoreItem>.withoutIgnoredGames(
    ignoredAppIds: Set<Int>
): List<SteamStoreItem> = if (ignoredAppIds.isEmpty()) this else filterNot {
    it.appId in ignoredAppIds
}
