package takagi.ru.monica.steam.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.store.bundle.data.SteamStoreBundleParser

class SteamStoreBundleParserTest {
    @Test
    fun parsesConsumerBundlePriceDiscountAndIncludedApps() {
        val html = """
            <div class="game_area_purchase_game_wrapper" data-ds-bundleid="233"
                 data-ds-bundle-data='{"m_nDiscountPct":"25","m_bIsCommercial":false,"m_rgItems":[{"m_rgIncludedAppIDs":[500]},{"m_rgIncludedAppIDs":[550]}]}'>
              <h2 class="title">Buy Left 4 Dead Bundle <span class="bundle_label">BUNDLE</span></h2>
              <p class="package_contents">
                <a href="https://store.steampowered.com/app/500/">Left 4 Dead</a>
                <a href="https://store.steampowered.com/app/550/">Left 4 Dead 2</a>
              </p>
              <a href="https://store.steampowered.com/bundle/233/Left_4_Dead_Bundle/?snr=test">Bundle info</a>
              <div class="discount_block" data-price-final="72000"></div>
            </div>
        """.trimIndent()

        val bundle = SteamStoreBundleParser.parse(html).single()

        assertEquals(233, bundle.bundleId)
        assertEquals("Left 4 Dead Bundle", bundle.title)
        assertEquals(72_000, bundle.finalPriceCents)
        assertEquals(25, bundle.discountPercent)
        assertEquals(
            "https://store.steampowered.com/bundle/233/Left_4_Dead_Bundle/",
            bundle.storeUrl
        )
        assertEquals(listOf(500, 550), bundle.items.map { it.appId })
        assertEquals("Left 4 Dead 2", bundle.items.last().name)
    }

    @Test
    fun excludesCommercialBundlesAndDeduplicatesResponsiveMarkup() {
        val commercial = """
            <div class="game_area_purchase_game_wrapper" data-ds-bundleid="9"
                 data-ds-bundle-data='{"m_bIsCommercial":true,"m_rgItems":[]}'><h2 class="title">Commercial</h2></div>
        """.trimIndent()
        val consumer = """
            <div class="game_area_purchase_game_wrapper" data-ds-bundleid="10"
                 data-ds-bundle-data='{"m_bIsCommercial":false,"m_rgItems":[{"m_rgIncludedAppIDs":[620]}]}'>
              <h2 class="title">Portal Bundle</h2><a href="/app/620/">Portal 2</a>
            </div>
        """.trimIndent()

        val parsed = SteamStoreBundleParser.parse(commercial + consumer + consumer)

        assertEquals(listOf(10), parsed.map { it.bundleId })
        assertTrue(parsed.single().items.isNotEmpty())
    }
}
