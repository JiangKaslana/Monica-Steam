package takagi.ru.monica.steam.profile.viewer.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import takagi.ru.monica.steam.friends.domain.SteamPersonaState
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGameAchievementProgress
import takagi.ru.monica.steam.library.SteamGameLibraryService
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileSummary
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerTarget

internal object SteamProfileViewerParser {
    fun parseProfileSummary(
        payload: JsonObject,
        target: SteamProfileViewerTarget
    ): SteamProfileSummary? {
        val root = payload.obj("response") ?: payload
        val player = root.array("players")
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it.string("steamid") == target.steamId }
            ?: return null
        return SteamProfileSummary(
            steamId = target.steamId,
            personaName = player.string("personaname").ifBlank { target.fallbackName },
            realName = player.string("realname"),
            avatarUrl = player.string("avatarfull")
                .ifBlank { player.string("avatarmedium") }
                .ifBlank { target.fallbackAvatarUrl },
            profileUrl = player.string("profileurl").ifBlank { target.fallbackProfileUrl },
            personaState = SteamPersonaState.fromCode(player.int("personastate")),
            lastLogoff = player.long("lastlogoff"),
            timeCreated = player.long("timecreated"),
            currentGameId = player.string("gameid"),
            currentGameName = player.string("gameextrainfo"),
            countryCode = player.string("loccountrycode"),
            communityVisibilityState = player.int("communityvisibilitystate")
        )
    }

    fun parseSteamLevel(response: ByteArray): Int? = SteamProtoReader(response)
        .parse()[1]
        ?.asLong
        ?.toInt()
        ?.takeIf { it >= 0 }

    fun parseOwnedGames(response: ByteArray): List<SteamGame> =
        SteamGameLibraryService.parseOwnedGames(response).map { game ->
            game.copy(
                headerImageUrl =
                    "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/" +
                        "${game.appId}/header.jpg"
            )
        }

    fun parseAchievementProgress(response: ByteArray): Map<Int, SteamGameAchievementProgress> =
        SteamGameLibraryService.parseAchievementProgress(response)

    fun hasAchievementDefinitions(response: ByteArray): Boolean {
        if (response.isEmpty()) return false
        return SteamProtoReader(response).parseAll().any { field ->
            field.number == 1 && field.bytes != null
        }
    }

    fun applyAchievementProgress(
        games: List<SteamGame>,
        progress: Map<Int, SteamGameAchievementProgress>
    ): List<SteamGame> = games.map { game ->
        val item = progress[game.appId] ?: return@map game
        game.copy(
            achievementUnlockedCount = item.unlocked,
            achievementTotalCount = item.total,
            allAchievementsUnlocked = item.allUnlocked
        )
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.array(key: String): JsonArray =
        this[key] as? JsonArray ?: JsonArray(emptyList())

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.int(key: String): Int {
        val value = this[key] as? JsonPrimitive ?: return 0
        return value.intOrNull ?: value.contentOrNull?.toIntOrNull() ?: 0
    }

    private fun JsonObject.long(key: String): Long {
        val value = this[key] as? JsonPrimitive ?: return 0L
        return value.longOrNull ?: value.contentOrNull?.toLongOrNull() ?: 0L
    }
}
