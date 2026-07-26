package takagi.ru.monica.steam.library.context.ui

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.ui.loadSteamRemoteImage
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.SteamGameOwnership
import takagi.ru.monica.steam.library.SteamLibraryFailureReason
import takagi.ru.monica.steam.library.context.domain.SteamLibraryCloudStatus
import takagi.ru.monica.steam.library.context.domain.SteamLibraryDlcContext
import takagi.ru.monica.steam.library.context.domain.SteamLibraryDlcOwnership
import takagi.ru.monica.steam.library.context.domain.SteamLibraryGameContext

@Composable
fun SteamLibraryGameContextSection(
    game: SteamGame,
    context: SteamLibraryGameContext?,
    fromCache: Boolean,
    loading: Boolean,
    failure: SteamLibraryFailureReason?,
    onRetry: () -> Unit,
    onOpenStoreApp: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OwnershipCard(
            game = game,
            context = context,
            fromCache = fromCache,
            loading = loading
        )
        CloudCard(context)
        DlcCard(
            context = context,
            loading = loading,
            onOpenStoreApp = onOpenStoreApp
        )
        if (failure != null) {
            ContextFailureCard(
                failure = failure,
                loading = loading,
                onRetry = onRetry
            )
        }
    }
}

@Composable
private fun OwnershipCard(
    game: SteamGame,
    context: SteamLibraryGameContext?,
    fromCache: Boolean,
    loading: Boolean
) {
    val ownership = context?.ownership ?: game.ownership
    val owners = context?.ownerSteamIds ?: game.ownerSteamIds
    val familyShared = ownership == SteamGameOwnership.FAMILY_SHARED
    val container = if (familyShared) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val content = if (familyShared) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = content
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (familyShared) Icons.Default.FamilyRestroom else Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(
                        if (familyShared) {
                            R.string.steam_library_context_family_shared
                        } else {
                            R.string.steam_library_context_owned_by_account
                        }
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val supporting = when {
                    familyShared && owners.isNotEmpty() -> stringResource(
                        R.string.steam_library_context_family_owner_count,
                        owners.size
                    )
                    fromCache -> stringResource(R.string.steam_library_context_cached)
                    else -> game.name
                }
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.76f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = content
                )
            }
        }
    }
}

@Composable
private fun CloudCard(context: SteamLibraryGameContext?) {
    val cloud = context?.cloud
    val status = cloud?.status ?: SteamLibraryCloudStatus.UNKNOWN
    val visuals = cloudVisuals(status)
    val androidContext = LocalContext.current
    val supporting = when (status) {
        SteamLibraryCloudStatus.AVAILABLE -> stringResource(
            R.string.steam_library_context_cloud_available,
            cloud?.fileCount ?: 0,
            Formatter.formatShortFileSize(androidContext, cloud?.totalBytes ?: 0L)
        )
        SteamLibraryCloudStatus.EMPTY ->
            stringResource(R.string.steam_library_context_cloud_empty)
        SteamLibraryCloudStatus.NOT_SUPPORTED ->
            stringResource(R.string.steam_library_context_cloud_unsupported)
        SteamLibraryCloudStatus.UNKNOWN -> if (cloud?.failure != null) {
            contextFailureLabel(cloud.failure)
        } else {
            stringResource(R.string.steam_library_context_cloud_unknown)
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = visuals.container,
                contentColor = visuals.content
            ) {
                Icon(
                    visuals.icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(24.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(R.string.steam_library_context_cloud_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                cloud?.lastUpdatedAtSeconds?.let { timestamp ->
                    Text(
                        stringResource(
                            R.string.steam_library_context_cloud_updated,
                            formatCloudTimestamp(timestamp)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DlcCard(
    context: SteamLibraryGameContext?,
    loading: Boolean,
    onOpenStoreApp: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.steam_library_context_dlc_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val summary = when {
                        context == null && loading ->
                            stringResource(R.string.steam_library_context_dlc_loading)
                        context?.dlcMetadataFailure != null && context.dlc.isEmpty() ->
                            stringResource(R.string.steam_library_context_dlc_unavailable)
                        context == null ->
                            stringResource(R.string.steam_library_context_dlc_unavailable)
                        context.dlc.isEmpty() ->
                            stringResource(R.string.steam_library_context_dlc_empty)
                        else -> stringResource(
                            R.string.steam_library_context_dlc_summary,
                            context.dlc.size,
                            context.ownedDlcCount,
                            context.familySharedDlcCount
                        )
                    }
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
            if (!context?.dlc.isNullOrEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    context?.dlc.orEmpty().forEach { dlc ->
                        item("dlc-${dlc.appId}") {
                            DlcItem(dlc = dlc, onClick = { onOpenStoreApp(dlc.appId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DlcItem(dlc: SteamLibraryDlcContext, onClick: () -> Unit) {
    val image = rememberDlcImage(dlc.headerImageUrl)
    Surface(
        onClick = onClick,
        modifier = Modifier.width(220.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = dlc.name,
                    modifier = Modifier.fillMaxWidth().height(92.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(92.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SportsEsports,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    dlc.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    minLines = 2
                )
                DlcOwnershipBadge(dlc.ownership)
            }
        }
    }
}

@Composable
private fun DlcOwnershipBadge(ownership: SteamLibraryDlcOwnership) {
    val (label, container, content) = when (ownership) {
        SteamLibraryDlcOwnership.OWNED -> Triple(
            R.string.steam_library_context_dlc_owned,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        SteamLibraryDlcOwnership.FAMILY_SHARED -> Triple(
            R.string.steam_library_context_dlc_family_shared,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        SteamLibraryDlcOwnership.NOT_OWNED -> Triple(
            R.string.steam_library_context_dlc_not_owned,
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        SteamLibraryDlcOwnership.UNKNOWN -> Triple(
            R.string.steam_library_context_dlc_unknown,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Text(
            stringResource(label),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ContextFailureCard(
    failure: SteamLibraryFailureReason,
    loading: Boolean,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Inventory2, contentDescription = null)
            Text(contextFailureLabel(failure), modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = onRetry, enabled = !loading) {
                Text(stringResource(R.string.steam_library_retry))
            }
        }
    }
}

@Composable
private fun contextFailureLabel(failure: SteamLibraryFailureReason): String = stringResource(
    when (failure) {
        SteamLibraryFailureReason.PRIVATE_PROFILE -> R.string.steam_library_private_profile
        SteamLibraryFailureReason.SESSION_REQUIRED -> R.string.steam_library_session_required
        SteamLibraryFailureReason.RATE_LIMITED -> R.string.steam_library_rate_limited
        SteamLibraryFailureReason.NETWORK -> R.string.steam_library_network_error
        SteamLibraryFailureReason.INVALID_RESPONSE -> R.string.steam_library_unavailable
    }
)

@Composable
private fun cloudVisuals(status: SteamLibraryCloudStatus): CloudVisuals = when (status) {
    SteamLibraryCloudStatus.AVAILABLE -> CloudVisuals(
        Icons.Default.CloudDone,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer
    )
    SteamLibraryCloudStatus.EMPTY -> CloudVisuals(
        Icons.Default.CloudQueue,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer
    )
    SteamLibraryCloudStatus.NOT_SUPPORTED -> CloudVisuals(
        Icons.Default.CloudOff,
        MaterialTheme.colorScheme.surfaceContainerHighest,
        MaterialTheme.colorScheme.onSurfaceVariant
    )
    SteamLibraryCloudStatus.UNKNOWN -> CloudVisuals(
        Icons.Default.CloudQueue,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer
    )
}

@Composable
private fun rememberDlcImage(url: String): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = url) {
        value = if (url.isBlank()) null else loadSteamRemoteImage(context, url)
    }
    return image
}

private fun formatCloudTimestamp(seconds: Long): String = DateFormat
    .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    .format(Date(seconds * 1_000L))

private data class CloudVisuals(
    val icon: ImageVector,
    val container: Color,
    val content: Color
)
