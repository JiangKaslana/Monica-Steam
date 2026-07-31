package takagi.ru.monica.steam.token.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.token.loginerror.domain.SteamLoginErrorPolicy

class SteamLoginErrorPolicyTest {
    @Test
    fun throttleResultUsesActionableChineseMessageAndStopsFallback() {
        val message = SteamLoginErrorPolicy.userMessage(
            eResult = 87,
            rawMessage = "Account login denied due to throttling"
        )

        assertTrue(message.contains("限制"))
        assertTrue(message.contains("等待"))
        assertFalse(
            SteamLoginErrorPolicy.shouldFallbackCredentialAuth(
                eResult = 87,
                httpStatusCode = 200
            )
        )
    }

    @Test
    fun challengeResultsMayUseLegacyFlowButDefinitiveFailuresDoNot() {
        assertTrue(SteamLoginErrorPolicy.shouldFallbackCredentialAuth(85, 200))
        assertTrue(SteamLoginErrorPolicy.shouldFallbackCredentialAuth(101, 200))
        assertFalse(SteamLoginErrorPolicy.shouldFallbackCredentialAuth(5, 200))
        assertFalse(SteamLoginErrorPolicy.shouldFallbackCredentialAuth(84, 200))
        assertFalse(SteamLoginErrorPolicy.shouldFallbackCredentialAuth(88, 200))
    }

    @Test
    fun technicalEnglishHtmlAndJsonAreNeverShownDirectly() {
        val values = listOf(
            "Steam API failed: IAuthenticationService/BeginAuthSessionViaCredentials (500)",
            "<html><body>upstream error</body></html>",
            "{\"success\":false,\"error\":\"backend unavailable\"}",
            "java.net.SocketTimeoutException: timeout\n at okhttp3.RealCall"
        )

        values.forEach { raw ->
            val message = SteamLoginErrorPolicy.userMessage(null, raw)
            assertFalse(message.contains(raw))
            assertFalse(message.startsWith("<"))
            assertFalse(message.startsWith("{"))
            assertFalse(message.contains("Exception"))
        }
    }
}
