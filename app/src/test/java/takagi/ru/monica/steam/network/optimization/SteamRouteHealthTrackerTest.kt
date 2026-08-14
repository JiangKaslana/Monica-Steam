package takagi.ru.monica.steam.network.optimization

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Test

class SteamRouteHealthTrackerTest {
    @Test
    fun recentSuccessBecomesStickyWithoutRemovingOtherCandidates() {
        var now = 1_000L
        val tracker = SteamRouteHealthTracker(clockMillis = { now })
        val first = ip("203.0.113.10")
        val second = ip("203.0.113.20")
        val third = ip("203.0.113.30")

        tracker.recordSuccess("store.steampowered.com", second)

        assertEquals(
            listOf(second, first, third),
            tracker.rank("STORE.STEAMPOWERED.COM.", listOf(first, second, third))
        )
    }

    @Test
    fun failedRouteMovesBehindHealthyAlternativesThenReturnsAfterCooldown() {
        var now = 10_000L
        val tracker = SteamRouteHealthTracker(
            clockMillis = { now },
            baseCooldownMillis = 30_000L,
            maxCooldownMillis = 300_000L
        )
        val first = ip("203.0.113.10")
        val second = ip("203.0.113.20")

        tracker.recordFailure("steamcommunity.com", first)
        assertEquals(
            listOf(second, first),
            tracker.rank("steamcommunity.com", listOf(first, second))
        )

        now += 30_001L
        assertEquals(
            listOf(first, second),
            tracker.rank("steamcommunity.com", listOf(first, second))
        )
    }

    @Test
    fun repeatedFailureUsesLongerCooldownButNeverBlacklistsRoute() {
        var now = 0L
        val tracker = SteamRouteHealthTracker(
            clockMillis = { now },
            baseCooldownMillis = 1_000L,
            maxCooldownMillis = 8_000L
        )
        val first = ip("203.0.113.10")
        val second = ip("203.0.113.20")

        tracker.recordFailure("help.steampowered.com", first)
        now = 1_001L
        tracker.recordFailure("help.steampowered.com", first)

        now = 2_500L
        assertEquals(
            listOf(second, first),
            tracker.rank("help.steampowered.com", listOf(first, second))
        )

        now = 3_002L
        assertEquals(
            listOf(first, second),
            tracker.rank("help.steampowered.com", listOf(first, second))
        )
    }

    @Test
    fun stickyPreferenceExpiresAndOriginalResolverOrderWinsAgain() {
        var now = 5_000L
        val tracker = SteamRouteHealthTracker(
            clockMillis = { now },
            stickyTtlMillis = 10_000L
        )
        val first = ip("203.0.113.10")
        val second = ip("203.0.113.20")

        tracker.recordSuccess("login.steampowered.com", second)
        assertEquals(
            listOf(second, first),
            tracker.rank("login.steampowered.com", listOf(first, second))
        )

        now += 10_001L
        assertEquals(
            listOf(first, second),
            tracker.rank("login.steampowered.com", listOf(first, second))
        )
    }

    private fun ip(raw: String): InetAddress = InetAddress.getByName(raw)
}
