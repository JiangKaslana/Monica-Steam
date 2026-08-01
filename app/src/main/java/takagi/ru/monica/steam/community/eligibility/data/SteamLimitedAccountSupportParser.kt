package takagi.ru.monica.steam.community.eligibility.data

import kotlin.math.roundToInt
import org.jsoup.Jsoup
import takagi.ru.monica.steam.community.eligibility.domain.DEFAULT_STEAM_UNLOCK_THRESHOLD_USD_CENTS
import takagi.ru.monica.steam.community.eligibility.domain.SteamLimitedAccountSupportProgress

internal object SteamLimitedAccountSupportParser {
    fun parse(html: String): SteamLimitedAccountSupportProgress? {
        if (html.isBlank()) return null
        val document = Jsoup.parse(html)
        val text = document.body()?.text()
            ?.replace('\u00A0', ' ')
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
        if (text.isBlank() || isLoginPage(document.location(), html, text)) return null

        val spent = findAmount(SPENT_AMOUNT, text)
        val threshold = findAmount(THRESHOLD_AMOUNT, text)
            ?: if (spent != null) DEFAULT_STEAM_UNLOCK_THRESHOLD_USD_CENTS else null
        val explicitRemaining = findAmount(REMAINING_AMOUNT, text)
        val remaining = when {
            explicitRemaining != null -> explicitRemaining
            spent != null && threshold != null -> (threshold - spent).coerceAtLeast(0)
            text.contains("your account is not limited", ignoreCase = true) -> 0
            else -> null
        }
        val limited = when {
            spent != null && threshold != null -> spent < threshold
            text.contains("your account is limited", ignoreCase = true) -> true
            text.contains("your account is a limited", ignoreCase = true) -> true
            text.contains("limited user account", ignoreCase = true) -> true
            text.contains("your account is not limited", ignoreCase = true) -> false
            else -> null
        }
        if (limited == null && spent == null && remaining == null) return null
        return SteamLimitedAccountSupportProgress(
            limited = limited,
            spentUsdCents = spent,
            thresholdUsdCents = threshold,
            remainingUsdCents = remaining
        )
    }

    private fun findAmount(pattern: Regex, text: String): Int? = pattern.find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.replace(",", "")
        ?.toDoubleOrNull()
        ?.times(100.0)
        ?.roundToInt()

    private fun isLoginPage(location: String, html: String, text: String): Boolean {
        val normalizedHtml = html.lowercase()
        return location.contains("/login", ignoreCase = true) ||
            "need_password=1" in normalizedHtml ||
            ("sign in to steam support" in text.lowercase() && "action=\"/login" in normalizedHtml)
    }

    private val SPENT_AMOUNT = Regex(
        "(?i)(?:your account\\s+has\\s+spent|you(?:'ve| have)\\s+spent|amount\\s+spent)" +
            "[^$]{0,80}(?:US)?\\$\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    )
    private val THRESHOLD_AMOUNT = Regex(
        "(?i)(?:out\\s+of(?:\\s+the)?|minimum|required|at\\s+least)" +
            "[^$]{0,80}(?:US)?\\$\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    )
    private val REMAINING_AMOUNT = Regex(
        "(?i)(?:remaining|left\\s+to\\s+spend|still\\s+need(?:s)?\\s+to\\s+spend|" +
            "need(?:s)?\\s+to\\s+spend\\s+an?\\s+additional)" +
            "[^$]{0,80}(?:US)?\\$\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    )
}
