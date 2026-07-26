package takagi.ru.monica.steam.community.domain

import kotlinx.serialization.Serializable

@Serializable
data class SteamCommunityProfile(
    val steamId: String,
    val displayName: String,
    val realName: String = "",
    val avatarUrl: String = "",
    val profileUrl: String = "",
    val countryCode: String = "",
    val stateCode: String = "",
    val cityId: Int = 0,
    val summary: String = "",
    val createdAt: Long = 0L,
    val lastLogoff: Long = 0L,
    val visibilityState: Int = 0
)

@Serializable
data class SteamCommunityBadge(
    val badgeId: Int,
    val level: Int,
    val xp: Int,
    val completionTime: Long,
    val scarcity: Int
)

@Serializable
data class SteamCommunityRecentGame(
    val appId: Int,
    val name: String,
    val iconUrl: String = "",
    val playtimeForeverMinutes: Int = 0,
    val playtimeTwoWeeksMinutes: Int = 0,
    val lastPlayedAt: Long = 0L
)

@Serializable
enum class SteamCommunitySection { PROFILE, LEVEL, BADGES, RECENT_GAMES }

@Serializable
data class SteamCommunitySnapshot(
    val accountSteamId: String,
    val profile: SteamCommunityProfile? = null,
    val steamLevel: Int? = null,
    val badges: List<SteamCommunityBadge> = emptyList(),
    val playerXp: Int? = null,
    val playerXpNeededToLevelUp: Int? = null,
    val recentGames: List<SteamCommunityRecentGame> = emptyList(),
    val unavailableSections: Set<SteamCommunitySection> = emptySet(),
    val fetchedAt: Long
)

fun interface SteamCommunityGateway {
    fun fetch(account: takagi.ru.monica.steam.data.SteamAccount): SteamCommunitySnapshot
}
