package takagi.ru.monica.steam.store.freebie.data

import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.network.SteamHttpClientProvider
import takagi.ru.monica.steam.store.data.SteamWebAccountSessionPolicy
import takagi.ru.monica.steam.store.data.buildSteamStoreRequest
import takagi.ru.monica.steam.store.data.encodeSteamCookieValue
import takagi.ru.monica.steam.store.data.SteamStoreService
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieCatalog
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieClaimMethod
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieClaimResult
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieClaimStatus
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieItem
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieOfferKind
import takagi.ru.monica.steam.store.purchase.data.SteamStorePurchaseContextService
import takagi.ru.monica.steam.store.purchase.domain.SteamStoreOwnershipStatus
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePurchaseContextGateway

internal class SteamFreebieService(
    private val client: OkHttpClient = SteamHttpClientProvider.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build(),
    private val claimClient: OkHttpClient = SteamHttpClientProvider.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
    private val storeService: SteamStoreService = SteamStoreService(),
    private val purchaseContextGateway: SteamStorePurchaseContextGateway =
        SteamStorePurchaseContextService(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun load(account: SteamAccount?): SteamFreebieCatalog = coroutineScope {
        val countryCode = account?.takeIf { it.hasRealSteamId }
            ?.let(storeService::accountCountryCode)
        val candidates = SteamFreebieSearchParser.parse(
            execute(buildSteamFreebieSearchRequest(countryCode))
        )
        val publicItems = candidates.map { candidate ->
            async {
                runCatching {
                    SteamFreebieOfferPageParser.parse(
                        candidate = candidate,
                        html = execute(buildSteamFreebieOfferPageRequest(candidate, countryCode)),
                        nowMillis = nowMillis()
                    )
                }.onFailure { error ->
                    SteamDiagLogger.append(
                        "store_freebie offer_failed app_id=${candidate.appId} " +
                            "type=${error.javaClass.simpleName}"
                    )
                }.getOrElse {
                    SteamFreebieItem(
                        appId = candidate.appId,
                        name = candidate.name,
                        imageUrl = candidate.imageUrl,
                        storeUrl = candidate.storeUrl,
                        offerKind = SteamFreebieOfferKind.KEEP_FOREVER,
                        claimMethod = SteamFreebieClaimMethod.NONE,
                        originalPriceText = candidate.originalPriceText,
                        finalPriceText = candidate.finalPriceText,
                        discountPercent = candidate.discountPercent
                    )
                }
            }
        }.awaitAll()
        val annotated = publicItems.map { item ->
            annotateAccountState(account, item).copy(accountCountryCode = countryCode)
        }.sortedWith(
            compareBy<SteamFreebieItem> { if (it.isOwned) 1 else 0 }
                .thenBy { it.endsAtEpochMillis ?: Long.MAX_VALUE }
                .thenBy(SteamFreebieItem::name)
        )
        SteamFreebieCatalog(
            items = annotated,
            accountCountryCode = countryCode,
            fetchedAt = nowMillis()
        )
    }

    suspend fun claim(
        account: SteamAccount,
        item: SteamFreebieItem
    ): SteamFreebieClaimResult {
        if (item.isOwned) {
            return SteamFreebieClaimResult(SteamFreebieClaimStatus.ALREADY_OWNED)
        }
        if (item.needsBaseGame) {
            return SteamFreebieClaimResult(SteamFreebieClaimStatus.NEEDS_BASE_GAME)
        }
        if (!item.isPermanentlyClaimable) {
            return SteamFreebieClaimResult(SteamFreebieClaimStatus.FAILED)
        }
        val packageId = item.packageId
            ?: return SteamFreebieClaimResult(SteamFreebieClaimStatus.FAILED)
        val secure = effectiveSteamLoginSecure(account)
            ?: return SteamFreebieClaimResult(SteamFreebieClaimStatus.SESSION_REQUIRED)
        val decision = SteamWebAccountSessionPolicy.decide(
            expectedSteamId = account.steamId,
            steamLoginSecure = secure,
            requireAuthenticatedSession = true
        )
        if (!decision.canLoad || !decision.installAuthenticatedCookie) {
            return SteamFreebieClaimResult(SteamFreebieClaimStatus.SESSION_REQUIRED)
        }
        val sessionId = newSteamSessionId()
        val response = claimClient.newCall(
            buildSteamFreebieClaimRequest(
                steamLoginSecure = secure,
                packageId = packageId,
                sessionId = sessionId,
                storeUrl = item.storeUrl
            )
        ).execute().use { httpResponse ->
            val body = httpResponse.body?.string().orEmpty()
            SteamFreebieClaimResponse(
                submissionStatus = classifySteamFreebieSubmission(
                    statusCode = httpResponse.code,
                    location = httpResponse.header("Location")
                ),
                body = body,
                statusCode = httpResponse.code
            )
        }
        when (response.submissionStatus) {
            SteamFreebieSubmissionStatus.SESSION_REQUIRED ->
                return SteamFreebieClaimResult(SteamFreebieClaimStatus.SESSION_REQUIRED)
            SteamFreebieSubmissionStatus.RATE_LIMITED ->
                return SteamFreebieClaimResult(SteamFreebieClaimStatus.RATE_LIMITED)
            SteamFreebieSubmissionStatus.REJECTED -> {
                return SteamFreebieClaimResult(
                    status = classifyRejectedClaim(response.body),
                    detail = "HTTP ${response.statusCode}"
                )
            }
            SteamFreebieSubmissionStatus.ACCEPTED -> Unit
        }

        repeat(OWNERSHIP_VERIFICATION_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(OWNERSHIP_VERIFICATION_DELAY_MILLIS)
            val ownership = runCatching {
                purchaseContextGateway.fetch(account, item.appId, "schinese").ownership
            }.getOrNull()
            if (ownership == SteamStoreOwnershipStatus.OWNED) {
                return SteamFreebieClaimResult(SteamFreebieClaimStatus.CLAIMED)
            }
        }
        return SteamFreebieClaimResult(SteamFreebieClaimStatus.PENDING_VERIFICATION)
    }

    private fun annotateAccountState(
        account: SteamAccount?,
        item: SteamFreebieItem
    ): SteamFreebieItem {
        if (account?.hasRealSteamId != true || account.accessToken.isNullOrBlank()) return item
        val ownership = runCatching {
            purchaseContextGateway.fetch(account, item.appId, "schinese").ownership
        }.onFailure { error ->
            SteamDiagLogger.append(
                "store_freebie ownership_failed app_id=${item.appId} " +
                    "type=${error.javaClass.simpleName}"
            )
        }.getOrDefault(SteamStoreOwnershipStatus.UNKNOWN)
        val baseOwnership = item.baseGameAppId?.let { baseAppId ->
            runCatching {
                purchaseContextGateway.fetch(account, baseAppId, "schinese").ownership
            }.getOrDefault(SteamStoreOwnershipStatus.UNKNOWN)
        } ?: SteamStoreOwnershipStatus.UNKNOWN
        return item.copy(
            ownership = ownership,
            baseGameOwnership = baseOwnership
        )
    }

    private fun execute(request: Request): String = client.newCall(request).execute().use { response ->
        when {
            response.code == 429 -> throw SteamFreebieRateLimitException()
            !response.isSuccessful -> throw IOException("Steam freebie HTTP ${response.code}")
            else -> response.body?.string().orEmpty()
        }
    }

    private data class SteamFreebieClaimResponse(
        val submissionStatus: SteamFreebieSubmissionStatus,
        val body: String,
        val statusCode: Int
    )

    private companion object {
        const val OWNERSHIP_VERIFICATION_ATTEMPTS = 3
        const val OWNERSHIP_VERIFICATION_DELAY_MILLIS = 700L
    }
}

internal fun buildSteamFreebieSearchRequest(
    countryCode: String?,
    language: String = "schinese"
): Request = buildSteamStoreRequest(
    path = "/search/results/",
    query = linkedMapOf(
        "query" to "",
        "start" to "0",
        "count" to "100",
        "dynamic_data" to "",
        "sort_by" to "_ASC",
        "specials" to "1",
        "maxprice" to "free",
        "infinite" to "1",
        "l" to language
    ),
    steamLoginSecure = null,
    countryCode = countryCode
)

internal fun buildSteamFreebieOfferPageRequest(
    candidate: SteamFreebieCandidate,
    countryCode: String?
): Request = buildSteamStoreRequest(
    path = "/app/${candidate.appId}/",
    query = mapOf("l" to "english"),
    steamLoginSecure = null,
    countryCode = countryCode
).newBuilder()
    .header("Accept", "text/html")
    .build()

internal fun buildSteamFreebieClaimRequest(
    steamLoginSecure: String,
    packageId: Int,
    sessionId: String,
    storeUrl: String
): Request {
    require(packageId > 0)
    require(sessionId.isNotBlank())
    val body = FormBody.Builder()
        .add("action", "add_to_cart")
        .add("sessionid", sessionId)
        .add("subid", packageId.toString())
        .build()
    return Request.Builder()
        .url("https://store.steampowered.com/freelicense/addfreelicense/")
        .header("User-Agent", "Monica-Steam/1.0")
        .header("Accept", "text/html,application/xhtml+xml")
        .header("Origin", "https://store.steampowered.com")
        .header("Referer", storeUrl)
        .header(
            "Cookie",
            "sessionid=$sessionId; birthtime=0; lastagecheckage=1-January-1980; " +
                "steamLoginSecure=${encodeSteamCookieValue(steamLoginSecure)}"
        )
        .post(body)
        .build()
}

internal fun effectiveSteamLoginSecure(account: SteamAccount): String? =
    account.steamLoginSecure?.trim()?.takeIf(String::isNotBlank)
        ?: account.accessToken?.trim()?.takeIf(String::isNotBlank)?.let { token ->
            "${account.steamId}||$token"
        }

internal fun classifyRejectedClaim(body: String): SteamFreebieClaimStatus = when {
    REGION_RESTRICTED_TEXT.containsMatchIn(body) -> SteamFreebieClaimStatus.REGION_RESTRICTED
    BASE_GAME_REQUIRED_TEXT.containsMatchIn(body) -> SteamFreebieClaimStatus.NEEDS_BASE_GAME
    else -> SteamFreebieClaimStatus.FAILED
}

internal class SteamFreebieRateLimitException : IOException("Steam freebie requests are rate limited")

private fun newSteamSessionId(): String {
    val bytes = ByteArray(12)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private val REGION_RESTRICTED_TEXT = Regex(
    "not available in your region|not available in your country|地区不可用|所在地区",
    RegexOption.IGNORE_CASE
)
private val BASE_GAME_REQUIRED_TEXT = Regex(
    "requires.*base game|must own.*base game|需要.*本体|必须拥有.*基础游戏",
    RegexOption.IGNORE_CASE
)
