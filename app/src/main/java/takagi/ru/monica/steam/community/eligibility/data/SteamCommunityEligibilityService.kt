package takagi.ru.monica.steam.community.eligibility.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import takagi.ru.monica.steam.community.eligibility.domain.DEFAULT_STEAM_UNLOCK_THRESHOLD_USD_CENTS
import takagi.ru.monica.steam.community.eligibility.domain.CURRENT_STEAM_COMMUNITY_EVIDENCE_REVISION
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityEligibilityGateway
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityBudgetGame
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityRestrictionStatus
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityUnlockCalculator
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityUnlockProgress
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityUnlockSource
import takagi.ru.monica.steam.community.eligibility.domain.steamCurrencyForCountry
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.library.SteamCurrencyExchangeService
import takagi.ru.monica.steam.store.data.SteamStoreService

internal class SteamCommunityEligibilityService(
    private val accountInfoService: SteamCommunityAccountInfoService =
        SteamCommunityAccountInfoService(),
    private val supportService: SteamLimitedAccountSupportService =
        SteamLimitedAccountSupportService(),
    private val storeService: SteamStoreService = SteamStoreService(),
    private val exchangeService: SteamCurrencyExchangeService = SteamCurrencyExchangeService(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) : SteamCommunityEligibilityGateway {
    override suspend fun fetch(account: SteamAccount): SteamCommunityUnlockProgress =
        coroutineScope {
        val accountInfoRequest = async { accountInfoService.fetch(account) }
        val supportRequest = async(Dispatchers.IO) {
            runCatching { supportService.fetch(account) }.getOrNull()
        }
        val countryRequest = async(Dispatchers.IO) {
            runCatching { storeService.accountCountryCode(account) }.getOrNull()
        }
        val ratesRequest = async(Dispatchers.IO) {
            runCatching { exchangeService.fetchCnyRates() }.getOrNull()
        }
        val accountInfo = accountInfoRequest.await()
        val support = supportRequest.await()
        val countryCode = countryRequest.await()?.takeIf(String::isNotBlank)
            ?: accountInfo?.countryCode.orEmpty()
        val currencyCode = steamCurrencyForCountry(countryCode)
        val status = when (support?.limited) {
            true -> SteamCommunityRestrictionStatus.LIMITED
            false -> SteamCommunityRestrictionStatus.UNRESTRICTED
            null -> when (accountInfo?.limited) {
                true -> SteamCommunityRestrictionStatus.LIMITED
                false -> SteamCommunityRestrictionStatus.UNRESTRICTED
                null -> SteamCommunityRestrictionStatus.UNKNOWN
            }
        }
        val thresholdUsd = support?.thresholdUsdCents
            ?: DEFAULT_STEAM_UNLOCK_THRESHOLD_USD_CENTS
        val remainingUsd = when (status) {
            SteamCommunityRestrictionStatus.UNRESTRICTED -> 0
            else -> support?.remainingUsdCents ?: thresholdUsd
        }.coerceAtLeast(0)
        val rates = ratesRequest.await()
        val localThreshold = rates?.let {
            SteamCommunityUnlockCalculator.localMinorFromUsd(
                thresholdUsd,
                currencyCode,
                it.unitsPerCny
            )
        }
        val localRemaining = rates?.let {
            SteamCommunityUnlockCalculator.localMinorFromUsd(
                remainingUsd,
                currencyCode,
                it.unitsPerCny
            )
        }
        val suggestions = if (
            status != SteamCommunityRestrictionStatus.UNRESTRICTED &&
            !countryCode.isBlank() &&
            localRemaining != null &&
            localRemaining in 1..Int.MAX_VALUE.toLong()
        ) {
            runCatching {
                storeService.budgetSuggestions(
                    targetMinor = localRemaining.toInt(),
                    countryCode = countryCode,
                    steamLoginSecure = account.steamLoginSecure
                ).mapNotNull { item ->
                    val price = item.finalPriceCents ?: return@mapNotNull null
                    SteamCommunityBudgetGame(
                        appId = item.appId,
                        name = item.name,
                        imageUrl = item.imageUrl.ifBlank { item.headerImageUrl },
                        currency = item.currency,
                        finalPriceMinor = price,
                        originalPriceMinor = item.initialPriceCents,
                        discountPercent = item.discountPercent
                    )
                }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        SteamCommunityUnlockProgress(
            status = status,
            source = when {
                support?.hasExactProgress == true -> SteamCommunityUnlockSource.STEAM_SUPPORT
                accountInfo != null -> SteamCommunityUnlockSource.STEAM_ACCOUNT_FLAGS
                else -> SteamCommunityUnlockSource.ESTIMATE
            },
            accountCountryCode = countryCode,
            accountCurrencyCode = currencyCode,
            thresholdUsdCents = thresholdUsd,
            spentUsdCents = support?.spentUsdCents,
            remainingUsdCents = remainingUsd,
            localThresholdMinor = localThreshold,
            localRemainingMinor = localRemaining,
            exchangeRateFetchedAt = rates?.fetchedAt,
            exactProgress = support?.hasExactProgress == true,
            evidenceRevision = CURRENT_STEAM_COMMUNITY_EVIDENCE_REVISION,
            suggestedGames = suggestions,
            fetchedAt = nowMillis()
        )
    }
}
