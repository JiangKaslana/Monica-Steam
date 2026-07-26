package takagi.ru.monica.steam.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.library.context.domain.SteamLibraryGameContext

class SteamLibraryStateRaceTest {
    @Test
    fun regionalPriceResultAfterDetailIsClosedIsIgnored() {
        val state = SteamLibraryUiState(
            selectedAccountId = 7L,
            selectedGame = null,
            loadingRegionalPrices = true
        )

        val updated = applyRegionalPricesToState(
            state = state,
            gameAppId = 730,
            freshPrices = listOf(
                SteamRegionalPrice("CN", "CNY", 990, 1_990, true, fetchedAt = 1L)
            )
        )

        assertNull(updated)
    }

    @Test
    fun regionalPriceResultForPreviousGameCannotOverwriteCurrentDetail() {
        val currentGame = SteamGame(
            appId = 570,
            name = "Dota 2",
            playtimeForeverMinutes = 1,
            playtimeRecentMinutes = 0
        )
        val state = SteamLibraryUiState(
            selectedAccountId = 7L,
            selectedGame = currentGame,
            loadingRegionalPrices = true
        )

        val updated = applyRegionalPricesToState(
            state = state,
            gameAppId = 730,
            freshPrices = listOf(
                SteamRegionalPrice("CN", "CNY", 990, 1_990, true, fetchedAt = 1L)
            )
        )

        assertNull(updated)
        assertEquals(570, state.selectedGame?.appId)
    }

    @Test
    fun gameContextRequiresTheSameSteamIdAccountAppAndGeneration() {
        val account = account("76561198000000001")
        val state = SteamLibraryUiState(
            accounts = listOf(account),
            selectedAccountId = account.id,
            selectedGame = SteamGame(620, "Portal 2", 1, 0)
        )

        assertTrue(steamLibraryGameContextRequestIsCurrent(state, account, 620, 3L, 3L))
        assertFalse(
            steamLibraryGameContextRequestIsCurrent(
                state = state,
                account = account("76561198000000009"),
                appId = 620,
                generation = 3L,
                currentGeneration = 3L
            )
        )
        assertFalse(steamLibraryGameContextRequestIsCurrent(state, account, 730, 3L, 3L))
        assertFalse(steamLibraryGameContextRequestIsCurrent(state, account, 620, 2L, 3L))
    }

    @Test
    fun detailContextCloudSupportUpdatesSelectedGameAndSnapshot() {
        val game = SteamGame(620, "Portal 2", 1, 0, supportsSteamCloud = null)
        val state = SteamLibraryUiState(
            selectedGame = game,
            snapshot = SteamLibrarySnapshot(
                accountId = 7L,
                games = listOf(game),
                fetchedAt = 1L
            )
        )

        val updated = applyGameContextToLibraryState(
            state = state,
            context = SteamLibraryGameContext(
                accountSteamId = "76561198000000001",
                appId = 620,
                ownership = SteamGameOwnership.OWNED,
                supportsSteamCloud = true
            )
        )

        assertTrue(updated.selectedGame?.supportsSteamCloud == true)
        assertTrue(updated.snapshot?.games?.single()?.supportsSteamCloud == true)
    }

    private fun account(steamId: String) = SteamAccount(
        id = 7L,
        steamId = steamId,
        accountName = "account",
        displayName = "Account",
        deviceId = "android:test",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "access-token",
        refreshToken = "refresh-token",
        steamLoginSecure = "$steamId||access-token",
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L
    )
}
