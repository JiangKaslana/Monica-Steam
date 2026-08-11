package takagi.ru.monica.steam.itad

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.itad.domain.ItadMoney
import takagi.ru.monica.steam.itad.domain.ItadPriceHistoryPoint
import takagi.ru.monica.steam.itad.ui.ItadPriceTrendRange
import takagi.ru.monica.steam.itad.ui.buildItadPriceTrendSamples

class ItadPriceTrendTest {
    @Test
    fun rebuildsAllStoreMinimumAsStepChanges() {
        val points = listOf(
            point("2026-01-01T00:00:00Z", 61, 40.0),
            point("2026-02-01T00:00:00Z", 35, 30.0),
            point("2026-03-01T00:00:00Z", 35, null),
            point("2026-04-01T00:00:00Z", 61, 20.0)
        )

        val samples = buildItadPriceTrendSamples(
            points = points,
            range = ItadPriceTrendRange.ALL,
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(listOf(40.0, 30.0, 40.0, 20.0), samples.map { it.amount })
        assertEquals(listOf("CNY", "CNY", "CNY", "CNY"), samples.map { it.currency })
    }

    private fun point(
        timestamp: String,
        shopId: Int,
        amount: Double?
    ): ItadPriceHistoryPoint = ItadPriceHistoryPoint(
        timestamp = timestamp,
        shopId = shopId,
        shopName = "Shop $shopId",
        price = amount?.let { ItadMoney(it, (it * 100).toLong(), "CNY") },
        regular = amount?.let { ItadMoney(it, (it * 100).toLong(), "CNY") },
        discountPercent = 0
    )
}
