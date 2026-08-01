package takagi.ru.monica.steam.community.eligibility.domain

import java.util.Currency
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.serialization.Serializable
import takagi.ru.monica.steam.data.SteamAccount

@Serializable
enum class SteamCommunityRestrictionStatus {
    LIMITED,
    UNRESTRICTED,
    UNKNOWN
}

@Serializable
enum class SteamCommunityUnlockSource {
    STEAM_SUPPORT,
    STEAM_ACCOUNT_FLAGS,
    ESTIMATE
}

@Serializable
data class SteamCommunityBudgetGame(
    val appId: Int,
    val name: String,
    val imageUrl: String = "",
    val currency: String,
    val finalPriceMinor: Int,
    val originalPriceMinor: Int? = null,
    val discountPercent: Int = 0
)

@Serializable
data class SteamCommunityUnlockProgress(
    val status: SteamCommunityRestrictionStatus = SteamCommunityRestrictionStatus.UNKNOWN,
    val source: SteamCommunityUnlockSource = SteamCommunityUnlockSource.ESTIMATE,
    val accountCountryCode: String = "",
    val accountCurrencyCode: String = "USD",
    val thresholdUsdCents: Int = DEFAULT_STEAM_UNLOCK_THRESHOLD_USD_CENTS,
    val spentUsdCents: Int? = null,
    val remainingUsdCents: Int = DEFAULT_STEAM_UNLOCK_THRESHOLD_USD_CENTS,
    val localThresholdMinor: Long? = null,
    val localRemainingMinor: Long? = null,
    val exchangeRateFetchedAt: Long? = null,
    val exactProgress: Boolean = false,
    val suggestedGames: List<SteamCommunityBudgetGame> = emptyList(),
    val fetchedAt: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = when {
            status == SteamCommunityRestrictionStatus.UNRESTRICTED -> 1f
            thresholdUsdCents <= 0 -> 0f
            spentUsdCents == null -> 0f
            else -> (spentUsdCents.toFloat() / thresholdUsdCents.toFloat()).coerceIn(0f, 1f)
        }
}

internal data class SteamCommunityAccountInfo(
    val countryCode: String,
    val accountFlags: Long,
    val limited: Boolean
)

internal data class SteamLimitedAccountSupportProgress(
    val limited: Boolean?,
    val spentUsdCents: Int?,
    val thresholdUsdCents: Int?,
    val remainingUsdCents: Int?
) {
    val hasExactProgress: Boolean
        get() = spentUsdCents != null || remainingUsdCents != null
}

internal object SteamCommunityUnlockCalculator {
    fun localMinorFromUsd(
        usdCents: Int,
        currencyCode: String,
        unitsPerCny: Map<String, Double>
    ): Long? {
        if (usdCents < 0) return null
        val usdPerCny = unitsPerCny["USD"]?.takeIf { it.isFinite() && it > 0.0 }
            ?: return null
        val localPerCny = unitsPerCny[currencyCode.trim().uppercase()]
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: return null
        return (usdCents.toDouble() / usdPerCny * localPerCny).roundToLong()
    }
}

internal fun steamCurrencyForCountry(countryCode: String): String {
    val country = countryCode.trim().uppercase()
    return when (country) {
        "CN" -> "CNY"
        "US" -> "USD"
        "GB" -> "GBP"
        "JP" -> "JPY"
        "KR" -> "KRW"
        "HK" -> "HKD"
        "TW" -> "TWD"
        "UA" -> "UAH"
        "IN" -> "INR"
        "ID" -> "IDR"
        "MY" -> "MYR"
        "PH" -> "PHP"
        "SG" -> "SGD"
        "TH" -> "THB"
        "VN" -> "VND"
        "BR" -> "BRL"
        "CA" -> "CAD"
        "AU" -> "AUD"
        "NZ" -> "NZD"
        "MX" -> "MXN"
        "CL" -> "CLP"
        "CO" -> "COP"
        "PE" -> "PEN"
        "ZA" -> "ZAR"
        "PK", "BD", "BT", "NP", "LK", "AR", "TR" -> "USD"
        else -> runCatching {
            Currency.getInstance(Locale("", country)).currencyCode
        }.getOrDefault("USD")
    }
}

internal const val DEFAULT_STEAM_UNLOCK_THRESHOLD_USD_CENTS = 500

fun interface SteamCommunityEligibilityGateway {
    suspend fun fetch(account: SteamAccount): SteamCommunityUnlockProgress
}
