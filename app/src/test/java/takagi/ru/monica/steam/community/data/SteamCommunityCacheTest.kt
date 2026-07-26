package takagi.ru.monica.steam.community.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.community.domain.SteamCommunityBadge
import takagi.ru.monica.steam.community.domain.SteamCommunityProfile
import takagi.ru.monica.steam.community.domain.SteamCommunityRecentGame
import takagi.ru.monica.steam.community.domain.SteamCommunitySection
import takagi.ru.monica.steam.community.domain.SteamCommunitySnapshot

class SteamCommunityCacheTest {
    @Test
    fun codecPreservesEveryReadOnlySection() {
        val original = snapshot(ACCOUNT_A)

        val restored = SteamCommunityCacheCodec.decode(
            SteamCommunityCacheCodec.encode(original)
        )

        assertEquals(original, restored)
        assertEquals("Portal 2", restored?.recentGames?.single()?.name)
        assertTrue(SteamCommunitySection.BADGES in restored!!.unavailableSections)
    }

    @Test
    fun preferencesCacheIsolatesDifferentSteamIds() {
        val store = MemoryStore()
        val cache = SteamCommunityPreferencesCache(store)
        cache.save(snapshot(ACCOUNT_A))

        assertEquals(ACCOUNT_A, cache.load(ACCOUNT_A)?.accountSteamId)
        assertNull(cache.load(ACCOUNT_B))
        assertEquals(1, store.values.size)
        assertTrue(store.values.keys.none { it.contains(ACCOUNT_A) })
    }

    private fun snapshot(steamId: String) = SteamCommunitySnapshot(
        accountSteamId = steamId,
        profile = SteamCommunityProfile(
            steamId = steamId,
            displayName = "Alyx"
        ),
        steamLevel = 42,
        badges = listOf(
            SteamCommunityBadge(
                badgeId = 1,
                level = 3,
                xp = 300,
                completionTime = 50L,
                scarcity = 7
            )
        ),
        playerXp = 4200,
        playerXpNeededToLevelUp = 800,
        recentGames = listOf(
            SteamCommunityRecentGame(appId = 620, name = "Portal 2")
        ),
        unavailableSections = setOf(SteamCommunitySection.BADGES),
        fetchedAt = 99L
    )

    private class MemoryStore : SteamCommunityKeyValueStore {
        val values = linkedMapOf<String, String>()

        override fun get(key: String): String? = values[key]

        override fun put(key: String, value: String) {
            values[key] = value
        }
    }

    private companion object {
        const val ACCOUNT_A = "76561198000000001"
        const val ACCOUNT_B = "76561198000000002"
    }
}
