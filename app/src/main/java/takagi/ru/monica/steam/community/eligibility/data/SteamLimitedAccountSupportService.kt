package takagi.ru.monica.steam.community.eligibility.data

import java.security.SecureRandom
import okhttp3.OkHttpClient
import okhttp3.Request
import takagi.ru.monica.steam.community.eligibility.domain.SteamLimitedAccountSupportProgress
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamHttpClientProvider

internal class SteamLimitedAccountSupportService(
    client: OkHttpClient = SteamHttpClientProvider.client
) {
    private val client = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun fetch(account: SteamAccount): SteamLimitedAccountSupportProgress? {
        val secure = account.steamLoginSecure?.takeIf(String::isNotBlank)
            ?: account.accessToken?.takeIf(String::isNotBlank)?.let {
                "${account.steamId}||$it"
            }
            ?: return null
        val request = Request.Builder()
            .url(SUPPORT_URL)
            .get()
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header(
                "Cookie",
                "steamLoginSecure=$secure; sessionid=${newSessionId()}; " +
                    "mobileClient=android; mobileClientVersion=777777%203.6.4"
            )
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful || response.isRedirect) return@use null
            SteamLimitedAccountSupportParser.parse(response.body?.string().orEmpty())
        }
    }

    private fun newSessionId(): String = ByteArray(12)
        .also(random::nextBytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        const val SUPPORT_URL =
            "https://help.steampowered.com/en/wizard/HelpWithLimitedAccount"
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
        val random = SecureRandom()
    }
}
