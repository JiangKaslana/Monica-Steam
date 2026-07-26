package takagi.ru.monica.steam.friends.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.foundation.ui.SteamAccountSwitcherSheet
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatViewModel
import takagi.ru.monica.steam.friends.chat.actions.presentation.SteamChatMessageActionResult
import takagi.ru.monica.steam.friends.chat.actions.presentation.SteamChatMessageActionViewModel
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaViewModel
import takagi.ru.monica.steam.friends.presentation.SteamFriendsViewModel
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatViewModel
import takagi.ru.monica.steam.friends.groupchat.ui.SteamGroupChatDialogsHost
import takagi.ru.monica.steam.friends.groupchat.ui.SteamGroupChatList
import takagi.ru.monica.steam.friends.groupchat.ui.SteamGroupChatThreadHost
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.navigation.easyNotesScreenEnter
import takagi.ru.monica.ui.navigation.easyNotesScreenExit

@Composable
fun SteamChatScreen(
    searchQuery: String = "",
    refreshRequest: Long = 0L,
    standalone: Boolean = false,
    requestedPartnerSteamId: String? = null,
    onConsumeRequestedPartner: () -> Unit = {},
    onUnreadCountChange: (Int) -> Unit = {},
    onThreadVisibilityChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val accountSourceRepository = remember(context) {
        SteamAccountSourceRepository.get(context)
    }
    val accountSourceState by accountSourceRepository.state.collectAsState()
    val friendsViewModel: SteamFriendsViewModel = viewModel(
        factory = remember(context) { SteamFriendsViewModel.factory(context) }
    )
    val chatViewModel: SteamChatViewModel = viewModel(
        factory = remember(context) { SteamChatViewModel.factory(context) }
    )
    val groupChatViewModel: SteamGroupChatViewModel = viewModel(
        factory = remember(context) { SteamGroupChatViewModel.factory(context) }
    )
    val richMediaViewModel: SteamChatRichMediaViewModel = viewModel(
        factory = remember(context) { SteamChatRichMediaViewModel.factory(context) }
    )
    val messageActionViewModel: SteamChatMessageActionViewModel = viewModel(
        factory = remember(context) { SteamChatMessageActionViewModel.factory(context) }
    )
    val friendsState by friendsViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val groupChatState by groupChatViewModel.state.collectAsState()
    val richMediaState by richMediaViewModel.uiState.collectAsState()
    val selectedAccount = accountSourceState.accounts.firstOrNull {
        it.id == accountSourceState.selectedAccountId
    } ?: accountSourceState.accounts.firstOrNull()
    val selectedFriend = friendsState.snapshot?.friends?.firstOrNull {
        it.steamId == chatState.selectedPartnerSteamId
    }
    var standaloneSearchQuery by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var showAccounts by rememberSaveable { mutableStateOf(false) }
    var showFriends by rememberSaveable { mutableStateOf(false) }
    var showGroups by rememberSaveable { mutableStateOf(false) }
    var showCreateGroup by rememberSaveable { mutableStateOf(false) }
    var showInviteFriend by rememberSaveable { mutableStateOf(false) }
    val effectiveSearchQuery = if (standalone) standaloneSearchQuery else searchQuery
    LaunchedEffect(
        selectedAccount?.id,
        selectedAccount?.steamId,
        selectedAccount?.accessToken,
        selectedAccount?.steamLoginSecure
    ) {
        chatViewModel.selectAccount(selectedAccount)
        groupChatViewModel.selectAccount(selectedAccount)
        richMediaViewModel.selectAccount(selectedAccount)
        messageActionViewModel.selectAccount(selectedAccount)
        friendsViewModel.selectAccount(selectedAccount)
    }
    LaunchedEffect(messageActionViewModel) {
        messageActionViewModel.results.collect { result ->
            val message = when (result) {
                SteamChatMessageActionResult.REACTION_ADDED -> R.string.steam_chat_reaction_added
                SteamChatMessageActionResult.MESSAGE_REPORTED -> R.string.steam_chat_reported
                SteamChatMessageActionResult.FAILED -> R.string.steam_chat_action_failed
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    SteamChatThreadLifecycle(
        chatState = chatState,
        groupChatState = groupChatState,
        uploadCompletedAt = richMediaState.uploadCompletedAt,
        refreshRequest = refreshRequest,
        chatViewModel = chatViewModel,
        groupChatViewModel = groupChatViewModel,
        richMediaViewModel = richMediaViewModel,
        friendsViewModel = friendsViewModel,
        onThreadVisibilityChange = onThreadVisibilityChange
    )
    LaunchedEffect(requestedPartnerSteamId, selectedAccount?.id) {
        val partner = requestedPartnerSteamId?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
        if (selectedAccount != null) {
            showFriends = false
            chatViewModel.openThread(partner)
            onConsumeRequestedPartner()
        }
    }

    LaunchedEffect(chatState.unreadCount, groupChatState.groups) {
        onUnreadCountChange(chatState.unreadCount + groupChatState.groups.sumOf { it.unreadCount })
    }

    LaunchedEffect(groupChatState.createdGroupId) {
        if (groupChatState.createdGroupId != null) {
            showCreateGroup = false
            Toast.makeText(context, R.string.steam_group_chat_created, Toast.LENGTH_SHORT).show()
            groupChatViewModel.clearCreatedGroup()
        }
    }
    LaunchedEffect(groupChatState.failure) {
        groupChatState.failure?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            groupChatViewModel.clearFailure()
        }
    }

    DisposableEffect(lifecycleOwner, chatViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> chatViewModel.setForeground(true)
                Lifecycle.Event.ON_STOP -> chatViewModel.setForeground(false)
                else -> Unit
            }
            when (event) {
                Lifecycle.Event.ON_START -> groupChatViewModel.setForeground(true)
                Lifecycle.Event.ON_STOP -> groupChatViewModel.setForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        chatViewModel.setForeground(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        groupChatViewModel.setForeground(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            chatViewModel.setForeground(false)
            groupChatViewModel.setForeground(false)
        }
    }

    BackHandler(enabled = chatState.selectedPartnerSteamId != null) {
        chatViewModel.closeThread()
    }
    BackHandler(enabled = groupChatState.selectedChatId != null) {
        groupChatViewModel.closeRoom()
    }
    BackHandler(
        enabled = standalone && chatState.selectedPartnerSteamId == null && showFriends
    ) {
        showFriends = false
    }

    AnimatedContent(
        targetState = chatState.selectedPartnerSteamId to groupChatState.selectedChatId,
        modifier = modifier.fillMaxSize(),
        transitionSpec = { easyNotesScreenEnter().togetherWith(easyNotesScreenExit()) },
        label = "SteamChatNavigation"
    ) { (partnerSteamId, groupRoomId) ->
        if (partnerSteamId == null && groupRoomId == null) {
            if (standalone) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        ExpressiveTopBar(
                            title = stringResource(R.string.steam_chat_title),
                            searchQuery = standaloneSearchQuery,
                            onSearchQueryChange = { standaloneSearchQuery = it },
                            isSearchExpanded = searchExpanded,
                            onSearchExpandedChange = { expanded ->
                                searchExpanded = expanded
                                if (!expanded) standaloneSearchQuery = ""
                            },
                            searchHint = stringResource(R.string.steam_chat_search_hint),
                            modifier = Modifier.statusBarsPadding(),
                            actions = {
                                IconButton(
                                    onClick = { showAccounts = true },
                                    enabled = accountSourceState.accounts.isNotEmpty() ||
                                        accountSourceState.mdbxDatabases.isNotEmpty()
                                ) {
                                    Icon(
                                        Icons.Default.SwitchAccount,
                                        contentDescription = stringResource(R.string.steam_switch_account)
                                    )
                                }
                                IconButton(onClick = {
                                    showFriends = !showFriends
                                    if (showFriends) showGroups = false
                                }) {
                                    Icon(
                                        Icons.Default.Groups,
                                        contentDescription = stringResource(R.string.steam_friends_title)
                                    )
                                }
                                IconButton(onClick = {
                                    showGroups = !showGroups
                                    if (showGroups) showFriends = false
                                }) {
                                    Icon(
                                        Icons.Default.Forum,
                                        contentDescription = stringResource(R.string.steam_group_chat_title)
                                    )
                                }
                                IconButton(onClick = { searchExpanded = true }) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = stringResource(R.string.steam_store_search)
                                    )
                                }
                            }
                        )
                    }
                ) { padding ->
                    AnimatedContent(
                        targetState = when {
                            showGroups -> 2
                            showFriends -> 1
                            else -> 0
                        },
                        modifier = Modifier.fillMaxSize().padding(padding),
                        transitionSpec = {
                            easyNotesScreenEnter().togetherWith(easyNotesScreenExit())
                        },
                        label = "SteamChatRootMode"
                    ) { rootMode ->
                        if (rootMode == 2) {
                            SteamGroupChatList(
                                state = groupChatState,
                                query = effectiveSearchQuery,
                                onOpenRoom = groupChatViewModel::openRoom,
                                onRefresh = groupChatViewModel::refreshGroups,
                                onCreateGroup = { showCreateGroup = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (rootMode == 1) {
                            SteamChatFriendPicker(
                                friends = friendsState.snapshot?.acceptedFriends.orEmpty(),
                                loading = friendsState.loading && friendsState.snapshot == null,
                                query = effectiveSearchQuery,
                                onOpenThread = { steamId ->
                                    showFriends = false
                                    chatViewModel.openThread(steamId)
                                },
                                onRefresh = friendsViewModel::refresh,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            SteamChatSessionList(
                                state = chatState,
                                friends = friendsState.snapshot?.acceptedFriends.orEmpty(),
                                query = effectiveSearchQuery,
                                onOpenThread = chatViewModel::openThread,
                                onRetry = chatViewModel::refreshSessions,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            } else {
                SteamChatSessionList(
                    state = chatState,
                    friends = friendsState.snapshot?.acceptedFriends.orEmpty(),
                    query = effectiveSearchQuery,
                    onOpenThread = chatViewModel::openThread,
                    onRetry = chatViewModel::refreshSessions,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (partnerSteamId != null) {
            SteamChatThread(
                state = chatState,
                richMediaState = richMediaState,
                friend = selectedFriend,
                onNavigateBack = chatViewModel::closeThread,
                onRefresh = chatViewModel::refreshThread,
                onLoadOlder = chatViewModel::loadOlder,
                onSend = chatViewModel::sendMessage,
                onRetryMessage = chatViewModel::retryMessage,
                onReact = { message, emoticon ->
                    messageActionViewModel.react(partnerSteamId, message, emoticon)
                },
                onStickerReply = { message, stickerCode ->
                    chatViewModel.sendReply(stickerCode, message.stableId)
                },
                onReport = { message, reason ->
                    messageActionViewModel.report(partnerSteamId, message, reason)
                },
                onAttachmentSelected = richMediaViewModel::selectAttachment,
                onAttachmentSpoilerChanged = richMediaViewModel::setAttachmentSpoiler,
                onUploadAttachment = richMediaViewModel::uploadAttachment,
                onClearAttachment = richMediaViewModel::clearAttachment,
                onClearAttachmentFailure = richMediaViewModel::clearAttachmentFailure,
                onRefreshCatalogs = richMediaViewModel::refreshCatalogs,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            SteamGroupChatThreadHost(
                state = groupChatState,
                richMediaState = richMediaState,
                friends = friendsState.snapshot?.acceptedFriends.orEmpty(),
                onBack = groupChatViewModel::closeRoom,
                onOpenRoom = groupChatViewModel::openRoom,
                onLoadOlder = groupChatViewModel::loadOlder,
                onSend = groupChatViewModel::sendMessage,
                onInvite = { showInviteFriend = true },
                onAttachmentSelected = richMediaViewModel::selectAttachment,
                onAttachmentSpoilerChanged = richMediaViewModel::setAttachmentSpoiler,
                onUploadAttachment = richMediaViewModel::uploadAttachment,
                onClearAttachment = richMediaViewModel::clearAttachment,
                onClearAttachmentFailure = richMediaViewModel::clearAttachmentFailure,
                onRefreshCatalogs = richMediaViewModel::refreshCatalogs,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (standalone && showAccounts) {
        SteamAccountSwitcherSheet(
            accounts = accountSourceState.accounts,
            selectedAccountId = accountSourceState.selectedAccountId,
            storageSource = accountSourceState.storageSource,
            mdbxDatabases = accountSourceState.mdbxDatabases,
            loading = accountSourceState.loading,
            errorMessage = accountSourceState.errorMessage,
            onSelectStorageSource = accountSourceRepository::selectStorageSource,
            onSelectAccount = { accountId ->
                accountSourceRepository.selectAccount(accountId)
                showAccounts = false
            },
            onRefresh = accountSourceRepository::refreshCurrentSource,
            onDismiss = { showAccounts = false }
        )
    }
    SteamGroupChatDialogsHost(
        state = groupChatState,
        friends = friendsState.snapshot?.acceptedFriends.orEmpty(),
        showCreateGroup = showCreateGroup,
        showInviteFriend = showInviteFriend,
        onCreate = groupChatViewModel::createGroup,
        onInvite = { groupChatViewModel.inviteFriend(it); showInviteFriend = false },
        onDismissCreate = { if (!groupChatState.creatingGroup) showCreateGroup = false },
        onDismissInvite = { showInviteFriend = false }
    )
}
