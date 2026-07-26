package takagi.ru.monica.steam.community.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteamCommunityParserTest {
    @Test
    fun parsesProfileLevelBadgesAndRecentGames() {
        val profile = SteamCommunityParser.profile(jsonObject("""
            {"response":{"players":[{
              "steamid":"76561198000000001",
              "personaname":"Alyx",
              "realname":"Alyx Vance",
              "avatarfull":"https://avatars.example/alyx.jpg",
              "profileurl":"https://steamcommunity.com/id/alyx/",
              "loccountrycode":"CN",
              "locstatecode":"31",
              "loccityid":100,
              "summary":"Resistance member",
              "timecreated":10,
              "lastlogoff":20,
              "communityvisibilitystate":3
            }]}}
        """))!!
        val level = SteamCommunityParser.level(jsonObject(
            """{"response":{"player_level":42}}"""
        ))
        val badges = SteamCommunityParser.badges(jsonObject("""
            {"response":{
              "badges":[
                {"badgeid":1,"level":3,"xp":300,"completion_time":50,"scarcity":7},
                {"badgeid":0,"level":9,"xp":900}
              ],
              "player_xp":4200,
              "player_xp_needed_to_level_up":800
            }}
        """))
        val games = SteamCommunityParser.recentGames(jsonObject("""
            {"response":{"games":[{
              "appid":620,
              "name":"Portal 2",
              "img_icon_url":"iconhash",
              "playtime_forever":1200,
              "playtime_2weeks":90,
              "rtime_last_played":1234
            }]}}
        """))

        assertEquals("76561198000000001", profile.steamId)
        assertEquals("Alyx", profile.displayName)
        assertEquals("Resistance member", profile.summary)
        assertEquals(42, level)
        assertEquals(1, badges.badges.size)
        assertEquals(4200, badges.playerXp)
        assertEquals(800, badges.playerXpNeededToLevelUp)
        assertEquals("Portal 2", games.single().name)
        assertEquals(
            "https://media.steampowered.com/steamcommunity/public/images/apps/620/iconhash.jpg",
            games.single().iconUrl
        )
        assertEquals(90, games.single().playtimeTwoWeeksMinutes)
    }

    @Test
    fun invalidOrMissingProfileDoesNotProduceAPlaceholderPlayer() {
        assertNull(SteamCommunityParser.profile(jsonObject(
            """{"response":{"players":[]}}"""
        )))
        assertNull(SteamCommunityParser.profile(jsonObject(
            """{"response":{"players":[{"steamid":"not-a-steam-id"}]}}"""
        )))
    }

    private fun jsonObject(raw: String) = Json.parseToJsonElement(raw).jsonObject
}
