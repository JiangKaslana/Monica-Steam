package takagi.ru.monica.steam.friends.chat.richmedia.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEmoticon
import takagi.ru.monica.steam.friends.chat.gameinvite.ui.SteamChatGameInviteCard
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContent
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContentParser
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatOfficialMessage
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatOfficialMessageKind
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatTextLink

@Composable
internal fun SteamChatRichMessageContent(
    body: String,
    onOpenStoreApp: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    when (val content = remember(body) { SteamChatRichContentParser.parse(body) }) {
        is SteamChatRichContent.Text -> SteamChatEmoticonText(
            body = content.body,
            links = content.links,
            modifier = modifier
        )
        is SteamChatRichContent.Action -> Text(
            text = content.body,
            modifier = modifier,
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        is SteamChatRichContent.GameInvite -> SteamChatGameInviteCard(
            content = content,
            onOpenStoreApp = onOpenStoreApp,
            modifier = modifier
        )
        is SteamChatRichContent.OfficialMessage -> SteamOfficialMessageContent(content.message, modifier)
        is SteamChatRichContent.Sticker -> SteamChatRemoteImage(
            url = content.imageUrl,
            contentDescription = content.name,
            modifier = modifier.size(184.dp),
            mode = SteamChatRemoteImageMode.STICKER
        )
        is SteamChatRichContent.Attachment -> AttachmentContent(content, modifier)
    }
}

@Composable
private fun SteamOfficialMessageContent(
    content: SteamChatOfficialMessage,
    modifier: Modifier
) {
    val context = LocalContext.current
    var showDetails by remember(content.rawBody) { mutableStateOf(false) }
    val open = {
        content.url?.let { url ->
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
        Unit
    }
    Surface(
        modifier = modifier.widthIn(min = 220.dp, max = 292.dp),
        color = officialMessageContainerColor(content.kind),
        contentColor = officialMessageContentColor(content.kind),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = officialMessageIcon(content.kind),
                    contentDescription = content.title,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = content.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Text(
                text = buildString {
                    content.description.takeIf { it.isNotBlank() && it != content.url }?.let {
                        append(it)
                    }
                    content.tradeOfferId?.let {
                        if (isNotEmpty()) append("\n")
                        append("Offer #").append(it)
                    }
                    if (isEmpty()) append(officialMessageFallback(content.kind))
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showDetails = true }) { Text("Details") }
                if (content.url != null) {
                    FilledTonalButton(onClick = open) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Open")
                    }
                }
            }
        }
    }
    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            icon = { Icon(officialMessageIcon(content.kind), contentDescription = null) },
            title = { Text(content.title) },
            text = {
                SelectionContainer {
                    Text(
                        text = content.rawBody,
                        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun officialMessageContainerColor(kind: SteamChatOfficialMessageKind) = when (kind) {
    SteamChatOfficialMessageKind.TRADE_OFFER -> MaterialTheme.colorScheme.tertiaryContainer
    SteamChatOfficialMessageKind.BROADCAST_INVITE,
    SteamChatOfficialMessageKind.BROADCAST_VIEW_REQUEST,
    SteamChatOfficialMessageKind.PLAYTEST_INVITE,
    SteamChatOfficialMessageKind.REMOTE_PLAY_INVITE -> MaterialTheme.colorScheme.secondaryContainer
    else -> MaterialTheme.colorScheme.surfaceContainerHigh
}

@Composable
private fun officialMessageContentColor(kind: SteamChatOfficialMessageKind) = when (kind) {
    SteamChatOfficialMessageKind.TRADE_OFFER -> MaterialTheme.colorScheme.onTertiaryContainer
    SteamChatOfficialMessageKind.BROADCAST_INVITE,
    SteamChatOfficialMessageKind.BROADCAST_VIEW_REQUEST,
    SteamChatOfficialMessageKind.PLAYTEST_INVITE,
    SteamChatOfficialMessageKind.REMOTE_PLAY_INVITE -> MaterialTheme.colorScheme.onSecondaryContainer
    else -> MaterialTheme.colorScheme.onSurface
}

private fun officialMessageIcon(kind: SteamChatOfficialMessageKind) = when (kind) {
    SteamChatOfficialMessageKind.TRADE_OFFER -> Icons.Default.SwapHoriz
    SteamChatOfficialMessageKind.BROADCAST_INVITE,
    SteamChatOfficialMessageKind.BROADCAST_VIEW_REQUEST -> Icons.Default.LiveTv
    SteamChatOfficialMessageKind.PLAYTEST_INVITE -> Icons.Default.Science
    SteamChatOfficialMessageKind.REMOTE_PLAY_INVITE -> Icons.Default.SportsEsports
    SteamChatOfficialMessageKind.GIFT -> Icons.Default.CardGiftcard
    SteamChatOfficialMessageKind.INVENTORY_ITEM -> Icons.Default.Inventory2
    SteamChatOfficialMessageKind.FRIEND_REQUEST -> Icons.Default.PersonAdd
    SteamChatOfficialMessageKind.GROUP_INVITE -> Icons.Default.PersonAdd
    SteamChatOfficialMessageKind.EVENT,
    SteamChatOfficialMessageKind.COMMENT,
    SteamChatOfficialMessageKind.MARKET -> Icons.Default.OpenInNew
    SteamChatOfficialMessageKind.ROOM_EFFECT -> Icons.Default.AutoAwesome
    SteamChatOfficialMessageKind.UNKNOWN -> Icons.Default.HelpOutline
}

private fun officialMessageFallback(kind: SteamChatOfficialMessageKind): String = when (kind) {
    SteamChatOfficialMessageKind.TRADE_OFFER -> "A Steam trade offer is waiting for you."
    SteamChatOfficialMessageKind.GIFT -> "A Steam gift notification was received."
    SteamChatOfficialMessageKind.INVENTORY_ITEM -> "A new Steam inventory item was received."
    SteamChatOfficialMessageKind.UNKNOWN -> "This Steam message type is not recognized yet."
    else -> "Steam sent an invitation or account notification."
}

@Composable
private fun SteamChatEmoticonText(
    body: String,
    links: List<SteamChatTextLink>,
    modifier: Modifier
) {
    val context = LocalContext.current
    val matches = remember(body) { emoticonPattern.findAll(body).toList() }
    if (matches.isEmpty() && links.isEmpty()) {
        Text(text = body, modifier = modifier, style = MaterialTheme.typography.bodyLarge)
        return
    }
    if (links.isEmpty() && matches.size == 1 && matches.single().value == body.trim()) {
        val name = matches.single().groupValues[1]
        SteamChatRemoteImage(
            url = SteamChatEmoticon(name).imageUrl,
            contentDescription = name,
            modifier = modifier.size(60.dp),
            mode = SteamChatRemoteImageMode.EMOTICON
        )
        return
    }
    val linkColor = MaterialTheme.colorScheme.primary
    val linkInteractionListener = remember(context) {
        LinkInteractionListener { annotation ->
            (annotation as? LinkAnnotation.Url)?.url?.let { openSteamChatLink(context, it) }
        }
    }
    val annotated = remember(body, matches, links, linkColor, linkInteractionListener) {
        val builder = AnnotatedString.Builder()
        var sourceCursor = 0
        matches.forEachIndexed { index, match ->
            appendSourceSegment(
                builder = builder,
                body = body,
                start = sourceCursor,
                endExclusive = match.range.first,
                links = links,
                linkColor = linkColor,
                linkInteractionListener = linkInteractionListener
            )
            builder.appendInlineContent("steam-emoticon-$index", match.value)
            sourceCursor = match.range.last + 1
        }
        appendSourceSegment(
            builder = builder,
            body = body,
            start = sourceCursor,
            endExclusive = body.length,
            links = links,
            linkColor = linkColor,
            linkInteractionListener = linkInteractionListener
        )
        builder.toAnnotatedString()
    }
    val inline = remember(matches) {
        matches.mapIndexed { index, match ->
            val name = match.groupValues[1]
            "steam-emoticon-$index" to InlineTextContent(
                placeholder = Placeholder(1.8.em, 1.8.em, PlaceholderVerticalAlign.Center)
            ) {
                SteamChatRemoteImage(
                    url = SteamChatEmoticon(name).imageUrl,
                    contentDescription = name,
                    modifier = Modifier.fillMaxWidth(),
                    mode = SteamChatRemoteImageMode.EMOTICON
                )
            }
        }.toMap()
    }
    Text(
        text = annotated,
        inlineContent = inline,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge
    )
}

private fun appendSourceSegment(
    builder: AnnotatedString.Builder,
    body: String,
    start: Int,
    endExclusive: Int,
    links: List<SteamChatTextLink>,
    linkColor: androidx.compose.ui.graphics.Color,
    linkInteractionListener: LinkInteractionListener
) {
    if (start >= endExclusive) return
    val outputStart = builder.length
    builder.append(body, start, endExclusive)
    links.forEach { link ->
        val overlapStart = maxOf(start, link.start)
        val overlapEnd = minOf(endExclusive, link.endExclusive)
        if (overlapStart < overlapEnd) {
            val annotationStart = outputStart + overlapStart - start
            val annotationEnd = outputStart + overlapEnd - start
            builder.addLink(
                url = LinkAnnotation.Url(
                    url = link.url,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        )
                    ),
                    linkInteractionListener = linkInteractionListener
                ),
                start = annotationStart,
                end = annotationEnd
            )
        }
    }
}

private fun openSteamChatLink(context: android.content.Context, url: String) {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
    if (uri.scheme?.lowercase() !in setOf("http", "https", "steam")) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

private val emoticonPattern = Regex("(?<![A-Za-z0-9]):([A-Za-z0-9_+\\-]{2,64}):(?![A-Za-z0-9])")

internal fun isSingleSteamEmoticonMessage(body: String): Boolean =
    emoticonPattern.matchEntire(body.trim()) != null
