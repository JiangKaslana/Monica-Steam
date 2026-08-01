package takagi.ru.monica.steam.community.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.R
import takagi.ru.monica.steam.community.presentation.SteamCommunityViewModel
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.foundation.ui.SteamAccountSwitcherSheet
import takagi.ru.monica.steam.foundation.ui.SteamExpressivePullToRefresh
import takagi.ru.monica.ui.components.ExpressiveTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamCommunityScreen(
    onNavigateBack: () -> Unit,
    initialSteamId: String? = null,
    onInitialSteamIdConsumed: () -> Unit = {},
    onOpenStoreApp: (Int) -> Unit = {},
    onOpenStore: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accountSource = remember(context) {
        SteamAccountSourceRepository.get(context.applicationContext)
    }
    val accountState by accountSource.state.collectAsState()
    val viewModel: SteamCommunityViewModel = viewModel(
        factory = remember(context) { SteamCommunityViewModel.factory(context) }
    )
    val state by viewModel.uiState.collectAsState()
    val selectedAccount = accountState.accounts.firstOrNull {
        it.id == accountState.selectedAccountId
    } ?: accountState.accounts.firstOrNull()
    var showAccountSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(
        initialSteamId,
        accountState.storageSource,
        accountState.accounts.map { it.steamId }
    ) {
        val requestedSteamId = initialSteamId ?: return@LaunchedEffect
        val requestedAccount = accountState.accounts.firstOrNull {
            it.steamId == requestedSteamId
        } ?: return@LaunchedEffect
        accountSource.selectAccount(requestedAccount.id)
        onInitialSteamIdConsumed()
    }

    LaunchedEffect(
        selectedAccount?.id,
        selectedAccount?.steamId,
        selectedAccount?.accessToken,
        selectedAccount?.steamLoginSecure
    ) {
        viewModel.selectAccount(selectedAccount)
    }

    if (showAccountSheet) {
        SteamAccountSwitcherSheet(
            accounts = accountState.accounts,
            selectedAccountId = accountState.selectedAccountId,
            storageSource = accountState.storageSource,
            mdbxDatabases = accountState.mdbxDatabases,
            loading = accountState.loading,
            errorMessage = accountState.errorMessage,
            onSelectStorageSource = accountSource::selectStorageSource,
            onSelectAccount = { accountId ->
                accountSource.selectAccount(accountId)
                showAccountSheet = false
            },
            onRefresh = accountSource::refreshCurrentSource,
            onDismiss = { showAccountSheet = false }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ExpressiveTopBar(
                title = stringResource(R.string.steam_community_title),
                searchQuery = "",
                onSearchQueryChange = {},
                isSearchExpanded = false,
                onSearchExpandedChange = {},
                modifier = Modifier.statusBarsPadding(),
                compact = true,
                collapsedTitleEndPadding = 120.dp,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAccountSheet = true },
                        enabled = accountState.accounts.isNotEmpty() ||
                            accountState.mdbxDatabases.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.SwitchAccount,
                            contentDescription = stringResource(R.string.steam_switch_account)
                        )
                    }
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = selectedAccount?.hasRealSteamId == true &&
                            !state.loading && !state.refreshing
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh)
                        )
                    }
                }
            )
        }
    ) { padding ->
        SteamExpressivePullToRefresh(
            refreshing = state.loading || state.refreshing,
            onRefresh = {
                accountSource.refreshCurrentSource()
                viewModel.refresh()
            },
            enabled = selectedAccount?.hasRealSteamId == true,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SteamCommunityContent(
                account = selectedAccount,
                state = state,
                onRetry = viewModel::refresh,
                onOpenUrl = { url -> openCommunityUrl(context, url) },
                onOpenStoreApp = onOpenStoreApp,
                onOpenStore = onOpenStore,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun openCommunityUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
