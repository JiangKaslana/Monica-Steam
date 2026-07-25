package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.domain.SteamChatDeliveryState
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRichMessageContent
import takagi.ru.monica.steam.friends.chat.richmedia.ui.isSingleSteamEmoticonMessage
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContent
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichContentParser

@Composable
internal fun SteamChatMessageBubble(
    message: SteamChatMessage,
    replyToMessage: SteamChatMessage?,
    accountSteamId: String,
    groupedWithPrevious: Boolean,
    groupedWithNext: Boolean,
    onRetry: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val outgoing = message.isOutgoing(accountSteamId)
    val retryable = outgoing && message.deliveryState == SteamChatDeliveryState.FAILED_RETRYABLE
    val haptics = LocalHapticFeedback.current
    val retryLabel = stringResource(R.string.steam_chat_retry_send)
    val bubbleShape = chatBubbleShape(outgoing, groupedWithPrevious, groupedWithNext)
    val richContent = remember(message.body) { SteamChatRichContentParser.parse(message.body) }
    val transparentMedia = richContent is SteamChatRichContent.Sticker ||
        isSingleSteamEmoticonMessage(message.body)
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 324.dp)
                .pointerInput(retryable, message.stableId) {
                    detectTapGestures(
                        onTap = {
                            if (retryable) {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onRetry()
                            }
                        },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick()
                        }
                    )
                },
            shape = bubbleShape,
            color = if (transparentMedia) Color.Transparent else if (outgoing) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (outgoing) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ) {
            if (transparentMedia) {
                Box {
                    Column {
                        replyToMessage?.let { ReplyPreview(it) }
                        SteamChatRichMessageContent(body = message.body)
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        DeliveryMetadata(
                            message = message,
                            outgoing = outgoing,
                            retryLabel = retryLabel,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            } else Column {
                replyToMessage?.let { ReplyPreview(it) }
                Row(
                modifier = Modifier.padding(
                    start = 13.dp,
                    top = if (groupedWithPrevious) 7.dp else 10.dp,
                    end = if (outgoing) 7.dp else 11.dp,
                    bottom = if (groupedWithNext) 7.dp else 9.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
                ) {
                SteamChatRichMessageContent(
                    body = message.body,
                    modifier = Modifier.weight(1f, fill = false)
                )
                    DeliveryMetadata(message, outgoing, retryLabel)
                }
            }
        }
    }
}

@Composable
private fun ReplyPreview(message: SteamChatMessage) {
    Surface(
        modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 3.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
    ) {
        Text(
            text = message.body.replace(Regex("\\s+"), " ").take(72),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2
        )
    }
}

@Composable
private fun DeliveryMetadata(
    message: SteamChatMessage,
    outgoing: Boolean,
    retryLabel: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.timestamp * 1_000L)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End
        )
        if (outgoing) {
            Spacer(Modifier.width(2.dp))
            AnimatedContent(
                targetState = message.deliveryState,
                transitionSpec = {
                    (fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                        scaleIn(initialScale = 0.8f, animationSpec = spring()))
                        .togetherWith(fadeOut() + scaleOut(targetScale = 0.8f))
                        .using(SizeTransform(clip = false))
                },
                label = "SteamChatDelivery"
            ) { delivery ->
                when (delivery) {
                    SteamChatDeliveryState.QUEUED,
                    SteamChatDeliveryState.SENDING,
                    SteamChatDeliveryState.VERIFYING -> AnimatedSendingClock(Modifier.size(15.dp))
                    SteamChatDeliveryState.SENT -> Icon(
                        Icons.Default.Done,
                        contentDescription = stringResource(R.string.steam_chat_sent),
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    SteamChatDeliveryState.FAILED_RETRYABLE,
                    SteamChatDeliveryState.FAILED_PERMANENT -> Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = retryLabel,
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedSendingClock(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "SteamChatSendingClock")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SteamChatSendingClockHand"
    )
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val stroke = with(LocalDensity.current) { 1.35.dp.toPx() }
    Canvas(modifier) {
        val radius = size.minDimension / 2f - stroke
        drawCircle(color = color, radius = radius, style = Stroke(stroke))
        drawLine(
            color = color,
            start = center,
            end = center.copy(y = center.y - radius * 0.48f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        val radians = Math.toRadians((rotation - 90f).toDouble())
        drawLine(
            color = color,
            start = center,
            end = androidx.compose.ui.geometry.Offset(
                x = center.x + kotlin.math.cos(radians).toFloat() * radius * 0.68f,
                y = center.y + kotlin.math.sin(radians).toFloat() * radius * 0.68f
            ),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

private fun chatBubbleShape(
    outgoing: Boolean,
    groupedWithPrevious: Boolean,
    groupedWithNext: Boolean
): RoundedCornerShape {
    val large = 18.dp
    val joined = 5.dp
    return if (outgoing) {
        RoundedCornerShape(
            topStart = large,
            topEnd = if (groupedWithPrevious) joined else large,
            bottomStart = large,
            bottomEnd = if (groupedWithNext) joined else large
        )
    } else {
        RoundedCornerShape(
            topStart = if (groupedWithPrevious) joined else large,
            topEnd = large,
            bottomStart = if (groupedWithNext) joined else large,
            bottomEnd = large
        )
    }
}
