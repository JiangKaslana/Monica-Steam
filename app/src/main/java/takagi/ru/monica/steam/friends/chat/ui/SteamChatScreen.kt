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
import takagi.ru.monica.steam.friends.groupchat.ui.SteamGroupChatThreadHost
import takagi.ru.monica.steam.friends.groupchat.ui.SteamGroupAdminScreen
import takagi.ru.monica.steam.friends.chat.info.data.SteamChatInfoPreferencesStore
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationId
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationPreferences
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationType
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatHistoryItem
import takagi.ru.monica.steam.friends.chat.info.ui.SteamChatHistorySearchScreen
import takagi.ru.monica.steam.friends.chat.info.ui.SteamChatInfoScreen
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.navigation.easyNotesScreenEnter
import takagi.ru.monica.ui.navigation.easyNotesScreenExit

private enum class SteamChatSubpage { INFO, SEARCH, ADMIN }

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
    val infoPreferencesStore = remember(context) { SteamChatInfoPreferencesStore(context) }
    var standaloneSearchQuery by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var showAccounts by rememberSaveable { mutableStateOf(false) }
    var showFriends by rememberSaveable { mutableStateOf(false) }
    var showCreateGroup by rememberSaveable { mutableStateOf(false) }
    var showInviteFriend by rememberSaveable { mutableStateOf(false) }
    var initialGroupInvitees by remember { mutableStateOf(emptySet<String>()) }
    var subpage by remember { mutableStateOf<SteamChatSubpage?>(null) }
    var targetMessageId by remember { mutableStateOf<String?>(null) }
    var conversationPreferences by remember { mutableStateOf(SteamChatConversationPreferences()) }
    val currentConversationId = when {
        chatState.selectedPartnerSteamId != null -> SteamChatConversationId(
            accountSteamId = chatState.accountSteamId,
            type = SteamChatConversationType.DIRECT,
            peerOrGroupId = chatState.selectedPartnerSteamId.orEmpty()
        )
        groupChatState.selectedGroupId != null -> SteamChatConversationId(
            accountSteamId = groupChatState.accountSteamId,
            type = SteamChatConversationType.GROUP,
            peerOrGroupId = groupChatState.selectedGroupId.orEmpty()
        )
        else -> null
    }
    LaunchedEffect(currentConversationId) {
        conversationPreferences = currentConversationId?.let(infoPreferencesStore::load)
            ?: SteamChatConversationPreferences()
        subpage = null
        targetMessageId = null
    }
    val pinnedDirectIds = remember(chatState.sessions, conversationPreferences, chatState.accountSteamId) {
        chatState.sessions?.sessions.orEmpty().mapNotNull { session ->
            val id = SteamChatConversationId(
                chatState.accountSteamId,
                SteamChatConversationType.DIRECT,
                session.partnerSteamId
            )
            session.partnerSteamId.takeIf { infoPreferencesStore.load(id).pinned }
        }.toSet()
    }
    val pinnedGroupIds = remember(groupChatState.groups, conversationPreferences, groupChatState.accountSteamId) {
        groupChatState.groups.mapNotNull { group ->
            val id = SteamChatConversationId(
                groupChatState.accountSteamId,
                SteamChatConversationType.GROUP,
                group.groupId
            )
            group.groupId.takeIf { infoPreferencesStore.load(id).pinned }
        }.toSet()
    }
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

    LaunchedEffect(groupChatState.createdGroupId, groupChatState.groups) {
        val createdGroupId = groupChatState.createdGroupId ?: return@LaunchedEffect
        val createdGroup = groupChatState.groups.firstOrNull { it.groupId == createdGroupId }
        if (createdGroup != null) {
            showCreateGroup = false
            showFriends = false
            initialGroupInvitees = emptySet()
            subpage = null
            chatViewModel.closeThread()
            groupChatViewModel.openRoom(createdGroup.groupId, createdGroup.preferredChatId)
            Toast.makeText(context, R.string.steam_group_chat_created, Toast.LENGTH_SHORT).show()
            groupChatViewModel.clearCreatedGroup()
        } else if (!groupChatState.groupsRefreshing && !groupChatState.groupsLoading) {
            groupChatViewModel.refreshGroups()
        }
    }
    LaunchedEffect(groupChatState.failure) {
        groupChatState.failure?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            groupChatViewModel.clearFailure()
        }
    }
    LaunchedEffect(subpage, groupChatState.selectedGroupId) {
        if (subpage == SteamChatSubpage.ADMIN && groupChatState.selectedGroupId != null) {
            groupChatViewModel.refreshAdminSnapshot()
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

    BackHandler(enabled = subpage != null) {
        subpage = if (subpage == SteamChatSubpage.SEARCH || subpage == SteamChatSubpage.ADMIN) {
            SteamChatSubpage.INFO
        } else null
    }
    BackHandler(enabled = subpage == null && chatState.selectedPartnerSteamId != null) {
        chatViewModel.closeThread()
    }
    BackHandler(enabled = subpage == null && groupChatState.selectedChatId != null) {
        groupChatViewModel.closeRoom()
    }
    BackHandler(
        enabled = standalone && chatState.selectedPartnerSteamId == null && showFriends
    ) {
        showFriends = false
    }

    AnimatedContent(
        targetState = Triple(chatState.selectedPartnerSteamId, groupChatState.selectedChatId, subpage),
        modifier = modifier.fillMaxSize(),
        transitionSpec = { easyNotesScreenEnter().togetherWith(easyNotesScreenExit()) },
        label = "SteamChatNavigation"
    ) { (partnerSteamId, groupRoomId, currentSubpage) ->
        if (partnerSteamId == null && groupRoomId == null) {
            if (standalone) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        ExpressiveTopBar(
                            title = stringResource(
                                if (showFriends) R.string.steam_friends_title
                                else R.string.steam_chat_title
                            ),
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
                                }) {
                                    Icon(
                                        Icons.Default.Groups,
                                        contentDescription = stringResource(R.string.steam_friends_title)
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
                        targetState = if (showFriends) 1 else 0,
                        modifier = Modifier.fillMaxSize().padding(padding),
                        transitionSpec = {
                            easyNotesScreenEnter().togetherWith(easyNotesScreenExit())
                        },
                        label = "SteamChatRootMode"
                    ) { rootMode ->
                        if (rootMode == 1) {
                            SteamChatFriendPicker(
                                friends = friendsState.snapshot?.acceptedFriends.orEmpty(),
                                sessions = chatState.sessions?.sessions.orEmpty(),
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
                            SteamConversationList(
                                chatState = chatState,
                                groupState = groupChatState,
                                friends = friendsState.snapshot?.acceptedFriends.orEmpty(),
                                query = effectiveSearchQuery,
                                pinnedPartnerSteamIds = pinnedDirectIds,
                                pinnedGroupIds = pinnedGroupIds,
                                onOpenDirect = { steamId ->
                                    groupChatViewModel.closeRoom()
                                    chatViewModel.openThread(steamId)
                                },
                                onOpenGroup = { groupId, chatId ->
                                    chatViewModel.closeThread()
                                    groupChatViewModel.openRoom(groupId, chatId)
                                },
                                onRefresh = {
                                    chatViewModel.refreshSessions()
                                    groupChatViewModel.refreshGroups()
                                },
                                onCreateGroup = { showCreateGroup = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            } else {
                SteamConversationList(
                    chatState = chatState,
                    groupState = groupChatState,
                    friends = friendsState.snapshot?.acceptedFriends.orEmpty(),
                    query = effectiveSearchQuery,
                    pinnedPartnerSteamIds = pinnedDirectIds,
                    pinnedGroupIds = pinnedGroupIds,
                    onOpenDirect = { steamId ->
                        groupChatViewModel.closeRoom()
                        chatViewModel.openThread(steamId)
                    },
                    onOpenGroup = { groupId, chatId ->
                        chatViewModel.closeThread()
                        groupChatViewModel.openRoom(groupId, chatId)
                    },
                    onRefresh = {
                        chatViewModel.refreshSessions()
                        groupChatViewModel.refreshGroups()
                    },
                    onCreateGroup = { showCreateGroup = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (currentSubpage == SteamChatSubpage.INFO) {
            val group = groupChatState.groups.firstOrNull { it.groupId == groupChatState.selectedGroupId }
            val friendMap = friendsState.snapshot?.friends.orEmpty().associateBy { it.steamId }
            val groupMembers = group?.topMemberSteamIds.orEmpty().mapNotNull(friendMap::get)
            SteamChatInfoScreen(
                title = if (partnerSteamId != null) "聊天信息" else "群聊信息",
                directFriend = if (partnerSteamId != null) selectedFriend else null,
                group = group,
                members = groupMembers,
                preferences = conversationPreferences,
                canEditGroup = group?.ownerAccountId?.let { owner ->
                    owner > 0L && accountIdFromSteamId(groupChatState.accountSteamId) == owner
                } == true,
                updatingGroup = groupChatState.updatingGroup,
                updatingGroupAvatar = groupChatState.updatingGroupAvatar,
                onBack = { subpage = null },
                onAddMember = {
                    if (partnerSteamId != null) {
                        initialGroupInvitees = setOf(partnerSteamId)
                        showCreateGroup = true
                    } else showInviteFriend = true
                },
                onSearchHistory = { subpage = SteamChatSubpage.SEARCH },
                onOpenGroupAdmin = { subpage = SteamChatSubpage.ADMIN },
                onPreferencesChange = { updated ->
                    conversationPreferences = updated
                    currentConversationId?.let { infoPreferencesStore.save(it, updated) }
                },
                onUpdateGroup = groupChatViewModel::updateGroup,
                onUpdateGroupAvatar = groupChatViewModel::updateGroupAvatar,
                channelActionLoading = groupChatState.channelActionLoading,
                voiceSession = groupChatState.voiceSession,
                onCreateChannel = groupChatViewModel::createChannel,
                onRenameChannel = groupChatViewModel::renameChannel,
                onDeleteChannel = groupChatViewModel::deleteChannel,
                onReorderChannel = groupChatViewModel::reorderChannel,
                onJoinVoiceChat = groupChatViewModel::joinVoiceChat,
                onLeaveVoiceChat = groupChatViewModel::leaveVoiceChat,
                modifier = Modifier.fillMaxSize()
            )
        } else if (currentSubpage == SteamChatSubpage.ADMIN) {
            val group = groupChatState.groups.firstOrNull { it.groupId == groupChatState.selectedGroupId }
            if (group != null) {
                SteamGroupAdminScreen(
                    group = group,
                    snapshot = groupChatState.adminSnapshot,
                    friends = friendsState.snapshot?.friends.orEmpty(),
                    loading = groupChatState.adminLoading,
                    actionLoading = groupChatState.adminActionLoading,
                    canEdit = group.ownerAccountId.let { owner ->
                        owner > 0L && accountIdFromSteamId(groupChatState.accountSteamId) == owner
                    },
                    createdInviteLink = groupChatState.createdInviteLink,
                    onBack = { subpage = SteamChatSubpage.INFO },
                    onRefresh = groupChatViewModel::refreshAdminSnapshot,
                    onCreateInviteLink = groupChatViewModel::createInviteLink,
                    onDeleteInviteLink = groupChatViewModel::deleteInviteLink,
                    onRevokeInvite = groupChatViewModel::revokeInvite,
                    onSetBanState = groupChatViewModel::setUserBanState,
                    onKick = groupChatViewModel::kickUser,
                    onMute = groupChatViewModel::muteUser,
                    onCreateRole = groupChatViewModel::createRole,
                    onRenameRole = groupChatViewModel::renameRole,
                    onDeleteRole = groupChatViewModel::deleteRole,
                    onReplaceRoleActions = groupChatViewModel::replaceRoleActions,
                    onAddRoleToUser = groupChatViewModel::addRoleToUser,
                    onRemoveRoleFromUser = groupChatViewModel::removeRoleFromUser,
                    onClearCreatedInviteLink = groupChatViewModel::clearCreatedInviteLink,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (currentSubpage == SteamChatSubpage.SEARCH) {
            val friendsById = friendsState.snapshot?.friends.orEmpty().associateBy { it.steamId }
            val items = if (partnerSteamId != null) {
                chatState.thread?.messages.orEmpty().map { message ->
                    SteamChatHistoryItem(
                        id = message.stableId,
                        senderName = if (message.senderSteamId == chatState.accountSteamId) "我"
                            else selectedFriend?.displayName ?: message.senderSteamId,
                        body = message.body,
                        timestamp = message.timestamp
                    )
                }
            } else {
                groupChatState.thread?.messages.orEmpty().map { message ->
                    SteamChatHistoryItem(
                        id = message.stableId,
                        senderName = if (message.senderSteamId == groupChatState.accountSteamId) "我"
                            else friendsById[message.senderSteamId]?.displayName ?: message.senderSteamId,
                        body = message.body,
                        timestamp = message.timestamp
                    )
                }
            }
            SteamChatHistorySearchScreen(
                items = items,
                onBack = { subpage = SteamChatSubpage.INFO },
                onOpenMessage = { messageId -> targetMessageId = messageId; subpage = null },
                modifier = Modifier.fillMaxSize()
            )
        } else if (partnerSteamId != null) {
            SteamChatThread(
                state = chatState,
                richMediaState = richMediaState,
                friend = selectedFriend,
                targetMessageId = targetMessageId,
                onNavigateBack = chatViewModel::closeThread,
                onOpenInfo = { subpage = SteamChatSubpage.INFO },
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
                targetMessageId = targetMessageId,
                onBack = groupChatViewModel::closeRoom,
                onOpenInfo = { subpage = SteamChatSubpage.INFO },
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
                onUpdateReaction = groupChatViewModel::updateMessageReaction,
                onReportMessage = groupChatViewModel::reportMessage,
                onDeleteMessage = groupChatViewModel::deleteMessage,
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
        initialInviteeSteamIds = initialGroupInvitees,
        onCreate = groupChatViewModel::createGroup,
        onInvite = { groupChatViewModel.inviteFriend(it); showInviteFriend = false },
        onDismissCreate = {
            if (!groupChatState.creatingGroup) {
                showCreateGroup = false
                initialGroupInvitees = emptySet()
            }
        },
        onDismissInvite = { showInviteFriend = false }
    )
}

private fun accountIdFromSteamId(steamId: String): Long? = runCatching {
    steamId.toBigInteger().subtract("76561197960265728".toBigInteger()).longValueExact()
}.getOrNull()
