package takagi.ru.monica.steam.profile.viewer.data

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.friends.domain.SteamPersonaState
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileGameDataVisibility
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileSummary
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerSnapshot

class SteamProfileViewerCacheTest {
    @Test
    fun profileSnapshotCodecPreservesViewerAndTargetIdentity() {
        val snapshot = SteamProfileViewerSnapshot(
            viewerAccountId = 1L,
            viewerSteamId = VIEWER,
            target = SteamProfileSummary(
                steamId = TARGET,
                personaName = "Target",
                personaState = SteamPersonaState.ONLINE
            ),
            targetGames = emptyList(),
            viewerGames = emptyList(),
            gameDataVisibility = SteamProfileGameDataVisibility.PRIVATE,
            fetchedAt = 123L
        )

        assertEquals(
            snapshot,
            SteamProfileViewerCacheCodec.decodeProfile(
                SteamProfileViewerCacheCodec.encodeProfile(snapshot)
            )
        )
    }

    private companion object {
        const val VIEWER = "76561198000000001"
        const val TARGET = "76561198000000002"
    }
}
