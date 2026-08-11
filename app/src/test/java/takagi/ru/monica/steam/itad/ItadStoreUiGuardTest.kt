package takagi.ru.monica.steam.itad

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.itad.domain.ItadMoney
import takagi.ru.monica.steam.itad.ui.formatItadMoney

class ItadStoreUiGuardTest {
    @Test
    fun storeDetailRendersHistoryLowWithSettingsAndOfficialAttribution() {
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val card = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/itad/ui/ItadHistoryLowSection.kt"
        ).readText()

        assertTrue(store.contains("ItadHistoryLowSection("))
        assertTrue(store.contains("detail.accountCountryCode ?: detail.priceCountryCode"))
        assertTrue(store.contains("onOpenItadSettings = onOpenSettings"))
        assertTrue(card.contains("R.string.itad_history_low_source"))
        assertTrue(card.contains("current.historicalLow.sourceUrl"))
        assertTrue(card.contains("isthereanydeal.com"))
        assertTrue(card.contains("value = null"))
    }

    @Test
    fun moneyFormattingKeepsItadCurrencyAndAmountWithoutConversion() {
        val formatted = formatItadMoney(
            ItadMoney(amount = 9.99, amountInt = 999, currency = "CNY"),
            Locale.US
        )

        assertEquals("CNY 9.99", formatted)
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!
        }
        return File(directory, path)
    }
}
