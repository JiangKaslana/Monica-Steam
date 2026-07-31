package takagi.ru.monica.steam.friends.chat.position.domain

internal data class SteamChatImeAnchorState(
    val imeVisible: Boolean = false,
    val wasAtBottomBeforeIme: Boolean = true,
    val restored: Boolean = false
)

internal data class SteamChatImeAnchorResult(
    val state: SteamChatImeAnchorState,
    val shouldScrollToLatest: Boolean
)

internal fun reduceSteamChatImeAnchor(
    previous: SteamChatImeAnchorState,
    imeVisible: Boolean,
    atBottom: Boolean,
    restored: Boolean,
    hasMessages: Boolean
): SteamChatImeAnchorResult {
    val openingIme = !previous.imeVisible && imeVisible
    val shouldFollow = openingIme && previous.restored && previous.wasAtBottomBeforeIme
    val bottomSnapshot = if (!imeVisible) atBottom else previous.wasAtBottomBeforeIme
    return SteamChatImeAnchorResult(
        state = SteamChatImeAnchorState(
            imeVisible = imeVisible,
            wasAtBottomBeforeIme = bottomSnapshot,
            restored = restored
        ),
        shouldScrollToLatest = restored && hasMessages && shouldFollow
    )
}
