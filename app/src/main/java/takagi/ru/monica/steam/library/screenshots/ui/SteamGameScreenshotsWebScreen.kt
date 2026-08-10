package takagi.ru.monica.steam.library.screenshots.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.library.screenshots.domain.SteamGameScreenshotsPage
import takagi.ru.monica.steam.web.domain.SteamWebClientMode
import takagi.ru.monica.steam.web.ui.SteamWebBrowserScreen

@Composable
internal fun SteamGameScreenshotsWebScreen(
    page: SteamGameScreenshotsPage,
    account: SteamAccount,
    onPlatformViewVisibilityChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    SteamWebBrowserScreen(
        url = page.url,
        steamLoginSecure = account.steamLoginSecure
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: account.accessToken
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { token -> "${account.steamId}||$token" },
        expectedSteamId = account.steamId,
        title = stringResource(R.string.steam_library_screenshots_title),
        requireAuthenticatedSession = true,
        clientMode = SteamWebClientMode.COMMUNITY_DESKTOP,
        onPlatformViewVisibilityChanged = onPlatformViewVisibilityChanged,
        onClose = onClose,
        modifier = modifier
    )
}
