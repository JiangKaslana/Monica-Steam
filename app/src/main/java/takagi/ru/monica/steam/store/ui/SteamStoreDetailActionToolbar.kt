package takagi.ru.monica.steam.store.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

@Composable
internal fun SteamStoreDetailActionToolbar(
    onOpenPurchaseOptions: () -> Unit,
    onOpenOfficialStore: () -> Unit,
    onOpenReviews: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SteamStoreDetailToolbarAction(
                icon = Icons.Default.ShoppingBag,
                contentDescription = stringResource(
                    R.string.steam_store_detail_purchase_options
                ),
                onClick = onOpenPurchaseOptions
            )
            SteamStoreDetailToolbarAction(
                icon = Icons.Default.Storefront,
                contentDescription = stringResource(R.string.steam_store_open_official),
                onClick = onOpenOfficialStore
            )
            SteamStoreDetailToolbarAction(
                icon = Icons.Default.RateReview,
                contentDescription = stringResource(
                    R.string.steam_store_detail_jump_to_reviews
                ),
                onClick = onOpenReviews
            )
            SteamStoreDetailToolbarAction(
                icon = Icons.Default.Share,
                contentDescription = stringResource(R.string.steam_store_detail_share),
                onClick = onShare
            )
        }
    }
}

@Composable
private fun SteamStoreDetailToolbarAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp)
        )
    }
}
