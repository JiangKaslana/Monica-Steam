package takagi.ru.monica.steam.richtext.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import takagi.ru.monica.steam.richtext.domain.SteamRichTextDocument
import takagi.ru.monica.steam.richtext.domain.SteamRichTextLink
import takagi.ru.monica.steam.richtext.domain.SteamRichTextParser
import takagi.ru.monica.steam.richtext.domain.SteamRichTextStyle

@Composable
internal fun SteamRichText(
    source: String,
    sourceLinks: List<SteamRichTextLink> = emptyList(),
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
    inlineRanges: List<SteamRichTextInlineRange> = emptyList(),
) {
    val document = remember(source, sourceLinks) {
        SteamRichTextParser.parse(source, sourceLinks)
    }
    SteamRichText(
        document = document,
        onOpenLink = onOpenLink,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        inlineContent = inlineContent,
        inlineRanges = inlineRanges,
    )
}

@Composable
internal fun SteamRichText(
    document: SteamRichTextDocument,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
    inlineRanges: List<SteamRichTextInlineRange> = emptyList(),
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val quoteColor = MaterialTheme.colorScheme.onSurfaceVariant
    val contentColor = LocalContentColor.current
    val linkListener = remember(onOpenLink) {
        LinkInteractionListener { annotation ->
            (annotation as? LinkAnnotation.Url)?.url?.let(onOpenLink)
        }
    }
    val annotated = remember(
        document,
        linkColor,
        codeBackground,
        quoteColor,
        contentColor,
        linkListener,
        inlineRanges,
    ) {
        buildSteamRichAnnotatedString(
            document = document,
            linkColor = linkColor,
            codeBackground = codeBackground,
            quoteColor = quoteColor,
            contentColor = contentColor,
            linkListener = linkListener,
            inlineRanges = inlineRanges,
        )
    }
    Text(
        text = annotated,
        inlineContent = inlineContent,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
    )
}

internal data class SteamRichTextInlineRange(
    val id: String,
    val start: Int,
    val endExclusive: Int,
)

private fun buildSteamRichAnnotatedString(
    document: SteamRichTextDocument,
    linkColor: Color,
    codeBackground: Color,
    quoteColor: Color,
    contentColor: Color,
    linkListener: LinkInteractionListener,
    inlineRanges: List<SteamRichTextInlineRange>,
): AnnotatedString = AnnotatedString.Builder().apply {
    val validInlineRanges = inlineRanges
        .sortedBy(SteamRichTextInlineRange::start)
        .fold(mutableListOf<SteamRichTextInlineRange>()) { accepted, range ->
            val valid = range.start >= 0 &&
                range.endExclusive <= document.text.length &&
                range.start < range.endExclusive &&
                accepted.lastOrNull()?.endExclusive?.let { it <= range.start } != false
            if (valid) accepted += range
            accepted
        }
    var cursor = 0
    validInlineRanges.forEach { range ->
        append(document.text, cursor, range.start)
        appendInlineContent(
            id = range.id,
            alternateText = document.text.substring(range.start, range.endExclusive),
        )
        cursor = range.endExclusive
    }
    append(document.text, cursor, document.text.length)

    document.spans.forEach { span ->
        if (span.start !in 0..length || span.endExclusive !in 0..length ||
            span.start >= span.endExclusive
        ) return@forEach
        addStyle(
            style = when (span.style) {
                SteamRichTextStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                SteamRichTextStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                SteamRichTextStyle.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                SteamRichTextStyle.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                SteamRichTextStyle.CODE -> SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = codeBackground,
                )
                SteamRichTextStyle.QUOTE -> SpanStyle(
                    color = quoteColor,
                    fontStyle = FontStyle.Italic,
                )
                SteamRichTextStyle.HEADING -> SpanStyle(fontWeight = FontWeight.SemiBold)
                SteamRichTextStyle.HIGHLIGHT -> SpanStyle(background = linkColor.copy(alpha = 0.18f))
                SteamRichTextStyle.SPOILER -> SpanStyle(
                    background = contentColor,
                    color = contentColor,
                )
            },
            start = span.start,
            end = span.endExclusive,
        )
    }
    document.links.forEach { link ->
        if (link.start !in 0..length || link.endExclusive !in 0..length ||
            link.start >= link.endExclusive
        ) return@forEach
        addLink(
            url = LinkAnnotation.Url(
                url = link.url,
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    )
                ),
                linkInteractionListener = linkListener,
            ),
            start = link.start,
            end = link.endExclusive,
        )
    }
}.toAnnotatedString()
