package takagi.ru.monica.steam.friends.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.data.SteamFriendsCache
import takagi.ru.monica.steam.friends.domain.SteamFriendActionResult
import takagi.ru.monica.steam.friends.domain.SteamFriendsGateway
import takagi.ru.monica.steam.friends.domain.SteamFriendsSnapshot
import takagi.ru.monica.steam.network.SteamApiException
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver

@OptIn(ExperimentalCoroutinesApi::class)
class SteamFriendsViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun authenticationFailureForcesTheSharedResolverBeforeOneRetry() = runTest(scheduler) {
        val forceFlags = mutableListOf<Boolean>()
        val tokens = mutableListOf<String?>()
        val resolver = SteamAccountSessionResolver { account, forceRefresh ->
            forceFlags += forceRefresh
            if (forceRefresh) {
                account.copy(
                    accessToken = "fresh-token",
                    steamLoginSecure = "${account.steamId}||fresh-token"
                )
            } else {
                account
            }
        }
        val gateway = object : SteamFriendsGateway {
            override fun fetch(account: SteamAccount, fetchedAt: Long): SteamFriendsSnapshot {
                tokens += account.accessToken
                if (account.accessToken != "fresh-token") {
                    throw SteamApiException(
                        message = "session expired",
                        eResult = 15,
                        httpStatusCode = 403
                    )
                }
                return SteamFriendsSnapshot(fetchedAt = fetchedAt)
            }

            override fun respondToInvite(
                account: SteamAccount,
                friendSteamId: String,
                accept: Boolean
            ) = SteamFriendActionResult(success = true)
        }
        val viewModel = SteamFriendsViewModel(
            gateway = gateway,
            cache = MemoryFriendsCache(),
            sessionResolver = resolver,
            ioDispatcher = dispatcher
        )

        viewModel.selectAccount(account())
        advanceUntilIdle()

        assertEquals(listOf(false, true), forceFlags)
        assertEquals(listOf("old-token", "fresh-token"), tokens)
        assertEquals(null, viewModel.uiState.value.failure)
        assertEquals(false, viewModel.uiState.value.loading)
    }

    @Test
    fun sameLocalIdWithDifferentSteamIdStillSwitchesAccounts() = runTest(scheduler) {
        val fetchedSteamIds = mutableListOf<String>()
        val gateway = object : SteamFriendsGateway {
            override fun fetch(account: SteamAccount, fetchedAt: Long): SteamFriendsSnapshot {
                fetchedSteamIds += account.steamId
                return SteamFriendsSnapshot(fetchedAt = fetchedAt)
            }

            override fun respondToInvite(
                account: SteamAccount,
                friendSteamId: String,
                accept: Boolean
            ) = SteamFriendActionResult(success = true)
        }
        val viewModel = SteamFriendsViewModel(
            gateway = gateway,
            cache = MemoryFriendsCache(),
            ioDispatcher = dispatcher
        )

        viewModel.selectAccount(account())
        viewModel.selectAccount(account(steamId = "76561198000000009"))
        advanceUntilIdle()

        assertEquals(
            listOf("76561198000000009"),
            fetchedSteamIds
        )
    }

    private fun account(steamId: String = "76561198000000001") = SteamAccount(
        id = 1L,
        steamId = steamId,
        accountName = "account",
        displayName = "Account",
        deviceId = "android:test",
        sharedSecret = "secret",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "old-token",
        refreshToken = "refresh-token",
        steamLoginSecure = "$steamId||old-token",
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L
    )
}

private class MemoryFriendsCache : SteamFriendsCache {
    private var snapshot: SteamFriendsSnapshot? = null

    override fun load(accountKey: String): SteamFriendsSnapshot? = snapshot

    override fun save(accountKey: String, snapshot: SteamFriendsSnapshot) {
        this.snapshot = snapshot
    }
}
