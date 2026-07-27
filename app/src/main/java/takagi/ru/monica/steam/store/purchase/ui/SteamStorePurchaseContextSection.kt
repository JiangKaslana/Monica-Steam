package takagi.ru.monica.steam.store.purchase.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.store.domain.SteamStoreDetail
import takagi.ru.monica.steam.store.domain.formatSteamPrice
import takagi.ru.monica.steam.store.purchase.domain.SteamStoreOwnershipStatus
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePackageOption
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePurchaseContext
import takagi.ru.monica.steam.store.purchase.domain.SteamStorePurchaseContextFailure

@Composable
fun SteamStorePurchaseContextSection(
    detail: SteamStoreDetail,
    context: SteamStorePurchaseContext?,
    contextFromCache: Boolean,
    loadingContext: Boolean,
    contextFailure: SteamStorePurchaseContextFailure?,
    selectedPackageId: Int?,
    onSelectPackage: (Int) -> Unit,
    onOpenRelatedApp: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OwnershipStatusCard(
            context = context,
            fromCache = contextFromCache,
            loading = loadingContext,
            failure = contextFailure
        )
        if (detail.packageOptions.isNotEmpty()) {
            PackageOptionsCard(
                options = detail.packageOptions,
                currency = detail.currency,
                selectedPackageId = selectedPackageId,
                onSelectPackage = onSelectPackage
            )
        }
        if (detail.fullGame != null || detail.demos.isNotEmpty() || detail.dlcAppIds.isNotEmpty()) {
            RelatedAppsCard(
                detail = detail,
                onOpenRelatedApp = onOpenRelatedApp
            )
        }
    }
}

@Composable
private fun OwnershipStatusCard(
    context: SteamStorePurchaseContext?,
    fromCache: Boolean,
    loading: Boolean,
    failure: SteamStorePurchaseContextFailure?
) {
    val status = context?.ownership ?: SteamStoreOwnershipStatus.UNKNOWN
    val visuals = ownershipVisuals(status)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = visuals.container,
        contentColor = visuals.content
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(visuals.icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = stringResource(visuals.label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val supporting = when {
                    status == SteamStoreOwnershipStatus.FAMILY_SHARED &&
                        context?.ownerSteamIds?.isNotEmpty() == true -> stringResource(
                            R.string.steam_store_family_owner_count,
                            context.ownerSteamIds.size
                        )
                    failure != null -> purchaseFailureLabel(failure)
                    fromCache -> stringResource(R.string.steam_store_purchase_context_cached)
                    else -> stringResource(R.string.steam_store_purchase_context_summary)
                }
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = visuals.content.copy(alpha = 0.76f)
                )
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun PackageOptionsCard(
    options: List<SteamStorePackageOption>,
    currency: String,
    selectedPackageId: Int?,
    onSelectPackage: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.steam_store_package_options),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            options.forEach { option ->
                val selected = option.packageId == selectedPackageId
                Surface(
                    onClick = { onSelectPackage(option.packageId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onSelectPackage(option.packageId) }
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                option.title.ifBlank {
                                    stringResource(
                                        R.string.steam_store_package_number,
                                        option.packageId
                                    )
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            option.description.takeIf(String::isNotBlank)?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (option.isFreeLicense || option.canGetFreeLicense) {
                                    stringResource(R.string.steam_store_free_license)
                                } else {
                                    formatSteamPrice(option.priceCents, currency)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedAppsCard(
    detail: SteamStoreDetail,
    onOpenRelatedApp: (Int) -> Unit
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
            Text(
                stringResource(R.string.steam_store_related_content),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                detail.fullGame?.let { game ->
                    item("base-${game.appId}") {
                        RelatedAppButton(
                            label = game.name.ifBlank {
                                stringResource(R.string.steam_store_base_game)
                            },
                            icon = Icons.Default.Inventory2,
                            onClick = { onOpenRelatedApp(game.appId) }
                        )
                    }
                }
                detail.demos.forEach { demo ->
                    item("demo-${demo.appId}") {
                        RelatedAppButton(
                            label = demo.description.ifBlank {
                                stringResource(R.string.steam_store_demo_number, demo.appId)
                            },
                            icon = Icons.Default.SportsEsports,
                            onClick = { onOpenRelatedApp(demo.appId) }
                        )
                    }
                }
                detail.dlcAppIds.take(MAX_VISIBLE_DLC).forEach { appId ->
                    item("dlc-$appId") {
                        RelatedAppButton(
                            label = stringResource(R.string.steam_store_dlc_number, appId),
                            icon = Icons.Default.Inventory2,
                            onClick = { onOpenRelatedApp(appId) }
                        )
                    }
                }
                val hiddenDlc = detail.dlcAppIds.size - MAX_VISIBLE_DLC
                if (hiddenDlc > 0) {
                    item("dlc-more") {
                        Surface(
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                stringResource(R.string.steam_store_more_dlc, hiddenDlc),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedAppButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ownershipVisuals(status: SteamStoreOwnershipStatus): OwnershipVisuals = when (status) {
    SteamStoreOwnershipStatus.OWNED -> OwnershipVisuals(
        icon = Icons.Default.CheckCircle,
        label = R.string.steam_store_owned,
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer
    )
    SteamStoreOwnershipStatus.FAMILY_SHARED -> OwnershipVisuals(
        icon = Icons.Default.FamilyRestroom,
        label = R.string.steam_store_family_shared,
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer
    )
    SteamStoreOwnershipStatus.NOT_OWNED -> OwnershipVisuals(
        icon = Icons.Default.Inventory2,
        label = R.string.steam_store_not_owned,
        container = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = MaterialTheme.colorScheme.onSurface
    )
    SteamStoreOwnershipStatus.UNKNOWN -> OwnershipVisuals(
        icon = Icons.AutoMirrored.Filled.HelpOutline,
        label = R.string.steam_store_ownership_unknown,
        container = MaterialTheme.colorScheme.tertiaryContainer,
        content = MaterialTheme.colorScheme.onTertiaryContainer
    )
}

@Composable
private fun purchaseFailureLabel(failure: SteamStorePurchaseContextFailure): String =
    stringResource(
        when (failure) {
            SteamStorePurchaseContextFailure.SESSION_REQUIRED ->
                R.string.steam_store_purchase_context_session
            SteamStorePurchaseContextFailure.RATE_LIMITED ->
                R.string.steam_store_purchase_context_rate_limited
            SteamStorePurchaseContextFailure.NETWORK ->
                R.string.steam_store_purchase_context_network
            SteamStorePurchaseContextFailure.INVALID_RESPONSE ->
                R.string.steam_store_purchase_context_invalid
        }
    )

private data class OwnershipVisuals(
    val icon: ImageVector,
    val label: Int,
    val container: Color,
    val content: Color
)

private const val MAX_VISIBLE_DLC = 12
