package takagi.ru.monica.steam.community.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityRestrictionStatus

class SteamCommunityUnlockContentTest {
    @Test
    fun limitedAndUnknownAccountsKeepTheSpendEstimateVisible() {
        assertTrue(
            shouldShowCommunitySpendEstimate(SteamCommunityRestrictionStatus.LIMITED)
        )
        assertTrue(
            shouldShowCommunitySpendEstimate(SteamCommunityRestrictionStatus.UNKNOWN)
        )
        assertFalse(
            shouldShowCommunitySpendEstimate(SteamCommunityRestrictionStatus.UNRESTRICTED)
        )
    }
}
