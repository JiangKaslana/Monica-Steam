package takagi.ru.monica.steam.community.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import takagi.ru.monica.steam.community.domain.SteamCommunityBadge
import takagi.ru.monica.steam.community.domain.SteamCommunityProfile
import takagi.ru.monica.steam.community.domain.SteamCommunityRecentGame

internal object SteamCommunityParser {
    fun profile(payload: JsonObject): SteamCommunityProfile? {
        val raw = (payload.obj("response") ?: payload).array("players")
            .firstOrNull() as? JsonObject ?: return null
        val steamId = raw.string("steamid")
        if (!steamId.matches(STEAM_ID_PATTERN)) return null
        return SteamCommunityProfile(
            steamId = steamId,
            displayName = raw.string("personaname").ifBlank { steamId },
            realName = raw.string("realname"),
            avatarUrl = raw.string("avatarfull").ifBlank { raw.string("avatarmedium") },
            profileUrl = raw.string("profileurl"),
            countryCode = raw.string("loccountrycode"),
            stateCode = raw.string("locstatecode"),
            cityId = raw.int("loccityid"),
            summary = raw.string("summary"),
            createdAt = raw.long("timecreated"),
            lastLogoff = raw.long("lastlogoff"),
            visibilityState = raw.int("communityvisibilitystate")
        )
    }

    fun level(payload: JsonObject): Int? =
        (payload.obj("response") ?: payload).intOrNull("player_level")

    fun badges(payload: JsonObject): ParsedBadges {
        val root = payload.obj("response") ?: payload
        return ParsedBadges(
            badges = root.array("badges").mapNotNull { element ->
                val raw = element as? JsonObject ?: return@mapNotNull null
                val id = raw.int("badgeid").takeIf { it > 0 } ?: return@mapNotNull null
                SteamCommunityBadge(
                    badgeId = id,
                    level = raw.int("level"),
                    xp = raw.int("xp"),
                    completionTime = raw.long("completion_time"),
                    scarcity = raw.int("scarcity")
                )
            },
            playerXp = root.intOrNull("player_xp"),
            playerXpNeededToLevelUp = root.intOrNull("player_xp_needed_to_level_up")
        )
    }

    fun recentGames(payload: JsonObject): List<SteamCommunityRecentGame> =
        (payload.obj("response") ?: payload).array("games").mapNotNull { element ->
            val raw = element as? JsonObject ?: return@mapNotNull null
            val appId = raw.int("appid").takeIf { it > 0 } ?: return@mapNotNull null
            SteamCommunityRecentGame(
                appId = appId,
                name = raw.string("name").ifBlank { "App $appId" },
                iconUrl = raw.string("img_icon_url").toCommunityIconUrl(appId),
                playtimeForeverMinutes = raw.int("playtime_forever"),
                playtimeTwoWeeksMinutes = raw.int("playtime_2weeks"),
                lastPlayedAt = raw.long("rtime_last_played")
            )
        }

    data class ParsedBadges(
        val badges: List<SteamCommunityBadge>,
        val playerXp: Int?,
        val playerXpNeededToLevelUp: Int?
    )

    private fun String.toCommunityIconUrl(appId: Int): String = takeIf(String::isNotBlank)
        ?.let { "https://media.steampowered.com/steamcommunity/public/images/apps/$appId/$it.jpg" }
        .orEmpty()

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.array(key: String) = this[key] as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull
        ?: string(key).toIntOrNull() ?: 0
    private fun JsonObject.intOrNull(key: String) = (this[key] as? JsonPrimitive)?.intOrNull
        ?: string(key).toIntOrNull()
    private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.longOrNull
        ?: string(key).toLongOrNull() ?: 0L

    private val STEAM_ID_PATTERN = Regex("7656119\\d{10}")
}
