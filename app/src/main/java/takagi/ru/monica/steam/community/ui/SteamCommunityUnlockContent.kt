package takagi.ru.monica.steam.community.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import takagi.ru.monica.R
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityBudgetGame
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityRestrictionStatus
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityUnlockProgress
import takagi.ru.monica.steam.store.domain.formatSteamPrice
import takagi.ru.monica.ui.theme.GoogleSansFlexFontFamily

@Composable
internal fun CommunityUnlockSection(
    progress: SteamCommunityUnlockProgress,
    stale: Boolean,
    onOpenGame: (Int) -> Unit,
    onOpenStore: () -> Unit,
    onOpenRules: () -> Unit
) {
    val status = progress.status
    val unlocked = status == SteamCommunityRestrictionStatus.UNRESTRICTED
    val containerColor = when (progress.status) {
        SteamCommunityRestrictionStatus.LIMITED -> MaterialTheme.colorScheme.secondaryContainer
        SteamCommunityRestrictionStatus.UNRESTRICTED -> MaterialTheme.colorScheme.primaryContainer
        SteamCommunityRestrictionStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when (progress.status) {
        SteamCommunityRestrictionStatus.LIMITED -> MaterialTheme.colorScheme.onSecondaryContainer
        SteamCommunityRestrictionStatus.UNRESTRICTED -> MaterialTheme.colorScheme.onPrimaryContainer
        SteamCommunityRestrictionStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    color = contentColor.copy(alpha = 0.10f),
                    contentColor = contentColor
                ) {
                    Icon(
                        imageVector = if (unlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp).size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.steam_community_unlock_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = unlockStatusText(progress.status),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.76f)
                    )
                    if (stale) {
                        Text(
                            text = stringResource(R.string.steam_community_cached_section),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.68f)
                        )
                    }
                }
            }

            Text(
                text = when (status) {
                    SteamCommunityRestrictionStatus.UNRESTRICTED ->
                        stringResource(R.string.steam_community_unlock_complete)
                    SteamCommunityRestrictionStatus.LIMITED -> stringResource(
                        R.string.steam_community_unlock_remaining,
                        remainingAmount(progress)
                    )
                    SteamCommunityRestrictionStatus.UNKNOWN ->
                        stringResource(R.string.steam_community_unlock_unknown_headline)
                },
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = GoogleSansFlexFontFamily
                ),
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (status != SteamCommunityRestrictionStatus.UNKNOWN) {
                LinearProgressIndicator(
                    progress = { progress.progressFraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.14f)
                )
            }

            Text(
                text = when (status) {
                    SteamCommunityRestrictionStatus.LIMITED -> if (progress.exactProgress) {
                        stringResource(
                        R.string.steam_community_unlock_official_progress,
                        formatUsd(progress.remainingUsdCents)
                        )
                    } else {
                        stringResource(R.string.steam_community_unlock_estimate_summary)
                    }
                    SteamCommunityRestrictionStatus.UNRESTRICTED ->
                        stringResource(R.string.steam_community_unlock_complete_summary)
                    SteamCommunityRestrictionStatus.UNKNOWN ->
                        stringResource(R.string.steam_community_unlock_unknown_summary)
                },
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.78f)
            )

            CommunityUnlockActions(onOpenStore = onOpenStore, onOpenRules = onOpenRules)
        }
    }

    if (
        progress.status == SteamCommunityRestrictionStatus.LIMITED &&
        progress.suggestedGames.isNotEmpty()
    ) {
        CommunitySectionHeader(
            title = stringResource(R.string.steam_community_unlock_games_title),
            supporting = stringResource(R.string.steam_community_unlock_games_summary)
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            progress.suggestedGames.forEach { game ->
                CommunityBudgetGameCard(game = game, onClick = { onOpenGame(game.appId) })
            }
        }
    }
}

@Composable
private fun CommunityBudgetGameCard(game: SteamCommunityBudgetGame, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CommunityGameIcon(game.imageUrl, Modifier.size(width = 96.dp, height = 54.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (game.discountPercent > 0 && game.originalPriceMinor != null) {
                        Text(
                            text = formatSteamPrice(game.originalPriceMinor, game.currency),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough
                        )
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(
                        text = formatSteamPrice(game.finalPriceMinor, game.currency),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (game.discountPercent > 0) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Text(
                        text = "-${game.discountPercent}%",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun unlockStatusText(status: SteamCommunityRestrictionStatus): String = stringResource(
    when (status) {
        SteamCommunityRestrictionStatus.LIMITED -> R.string.steam_community_unlock_limited
        SteamCommunityRestrictionStatus.UNRESTRICTED -> R.string.steam_community_unlock_unrestricted
        SteamCommunityRestrictionStatus.UNKNOWN -> R.string.steam_community_unlock_unknown
    }
)

private fun remainingAmount(progress: SteamCommunityUnlockProgress): String {
    val local = progress.localRemainingMinor
    return if (local != null && local in 0..Int.MAX_VALUE.toLong()) {
        formatSteamPrice(local.toInt(), progress.accountCurrencyCode)
    } else {
        formatUsd(progress.remainingUsdCents)
    }
}

private fun formatUsd(cents: Int): String = String.format(Locale.US, "$%.2f", cents / 100.0)
