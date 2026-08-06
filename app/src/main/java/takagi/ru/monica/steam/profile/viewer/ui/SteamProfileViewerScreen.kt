package takagi.ru.monica.steam.profile.viewer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.foundation.ui.SteamExpressivePullToRefresh
import takagi.ru.monica.steam.profile.viewer.domain.SteamProfileViewerTarget
import takagi.ru.monica.steam.profile.viewer.presentation.SteamProfileViewerViewModel
import takagi.ru.monica.ui.LocalReduceAnimations
import takagi.ru.monica.ui.navigation.easyNotesScreenEnter
import takagi.ru.monica.ui.navigation.easyNotesScreenExit

@Composable
fun SteamProfileViewerScreen(
    viewerAccount: SteamAccount,
    target: SteamProfileViewerTarget,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reduceAnimations = LocalReduceAnimations.current
    val profileViewModel: SteamProfileViewerViewModel = viewModel(
        key = "steam_profile_${viewerAccount.id}_${target.steamId}",
        factory = remember(context) { SteamProfileViewerViewModel.factory(context) }
    )
    val state by profileViewModel.uiState.collectAsState()
    val selectedGame = state.selectedGame

    LaunchedEffect(
        viewerAccount.id,
        viewerAccount.steamId,
        viewerAccount.accessToken,
        target.steamId,
        target.fallbackName,
        target.fallbackAvatarUrl
    ) {
        profileViewModel.load(viewerAccount, target)
    }

    BackHandler(enabled = true) {
        if (selectedGame != null) profileViewModel.closeGame() else onNavigateBack()
    }

    AnimatedContent(
        targetState = selectedGame,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            easyNotesScreenEnter(reduceAnimations)
                .togetherWith(easyNotesScreenExit(reduceAnimations))
        },
        contentKey = { game -> game?.appId ?: 0 },
        label = "SteamProfileViewerNavigation"
    ) { game ->
        if (game == null) {
            SteamExpressivePullToRefresh(
                refreshing = state.loading,
                onRefresh = profileViewModel::refresh,
                enabled = !state.loading,
                modifier = Modifier.fillMaxSize()
            ) {
                SteamProfileViewerOverview(
                    state = state,
                    target = target,
                    onNavigateBack = onNavigateBack,
                    onRefresh = profileViewModel::refresh,
                    onOpenGame = profileViewModel::openGame,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            SteamProfileAchievementComparisonScreen(
                state = state,
                game = game,
                onNavigateBack = profileViewModel::closeGame,
                onRetry = { profileViewModel.openGame(game) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
