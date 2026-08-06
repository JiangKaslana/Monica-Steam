package takagi.ru.monica.steam.library.filter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryAchievementStatusFilter
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryFilterSelection
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryOwnershipFilter
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryPlayStatusFilter
import takagi.ru.monica.steam.library.filter.domain.SteamLibraryPlaytimeFilter
import takagi.ru.monica.steam.library.filter.domain.SteamLibrarySortOrder

@Composable
internal fun SteamLibraryFilterEntry(
    selection: SteamLibraryFilterSelection,
    filteredCount: Int,
    totalCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (selection.activeChoiceCount > 0) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.FilterList, contentDescription = null)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.steam_library_filter_and_sort),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (selection.activeChoiceCount == 0) {
                        stringResource(R.string.steam_library_filter_summary_default)
                    } else {
                        stringResource(
                            R.string.steam_library_filter_summary_active,
                            selection.activeChoiceCount
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Text(
                    text = stringResource(
                        R.string.steam_library_filter_result_count,
                        filteredCount,
                        totalCount
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SteamLibraryFilterSheet(
    selection: SteamLibraryFilterSelection,
    totalCount: Int,
    filteredCount: (SteamLibraryFilterSelection) -> Int,
    onApply: (SteamLibraryFilterSelection) -> Unit,
    onDismiss: () -> Unit
) {
    var pending by remember(selection) { mutableStateOf(selection) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.steam_library_filter_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(
                            R.string.steam_library_filter_result_count,
                            filteredCount(pending),
                            totalCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = { pending = SteamLibraryFilterSelection() },
                    enabled = pending != SteamLibraryFilterSelection(),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.steam_library_filter_reset))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "ownership") {
                    SteamLibraryChoiceGroup(
                        title = stringResource(R.string.steam_library_filter_group_ownership)
                    ) {
                        SteamLibraryOwnershipFilter.entries.forEach { option ->
                            SteamLibraryChoiceChip(
                                selected = pending.ownership == option,
                                label = steamLibraryOwnershipLabel(option),
                                onClick = { pending = pending.copy(ownership = option) }
                            )
                        }
                    }
                }
                item(key = "play_status") {
                    SteamLibraryChoiceGroup(
                        title = stringResource(R.string.steam_library_filter_group_play_status)
                    ) {
                        SteamLibraryPlayStatusFilter.entries.forEach { option ->
                            SteamLibraryChoiceChip(
                                selected = pending.playStatus == option,
                                label = steamLibraryPlayStatusLabel(option),
                                onClick = { pending = pending.copy(playStatus = option) }
                            )
                        }
                    }
                }
                item(key = "achievement_status") {
                    SteamLibraryChoiceGroup(
                        title = stringResource(R.string.steam_library_filter_group_achievements)
                    ) {
                        SteamLibraryAchievementStatusFilter.entries.forEach { option ->
                            SteamLibraryChoiceChip(
                                selected = pending.achievementStatus == option,
                                label = steamLibraryAchievementStatusLabel(option),
                                onClick = { pending = pending.copy(achievementStatus = option) }
                            )
                        }
                    }
                }
                item(key = "playtime") {
                    SteamLibraryChoiceGroup(
                        title = stringResource(R.string.steam_library_filter_group_playtime)
                    ) {
                        SteamLibraryPlaytimeFilter.entries.forEach { option ->
                            SteamLibraryChoiceChip(
                                selected = pending.playtime == option,
                                label = steamLibraryPlaytimeLabel(option),
                                onClick = { pending = pending.copy(playtime = option) }
                            )
                        }
                    }
                }
                item(key = "features") {
                    SteamLibraryChoiceGroup(
                        title = stringResource(R.string.steam_library_filter_group_features)
                    ) {
                        SteamLibraryChoiceChip(
                            selected = pending.requiresSteamCloud,
                            label = stringResource(R.string.steam_library_filter_steam_cloud),
                            onClick = {
                                pending = pending.copy(
                                    requiresSteamCloud = !pending.requiresSteamCloud
                                )
                            }
                        )
                    }
                }
                item(key = "sort") {
                    SteamLibraryChoiceGroup(
                        title = stringResource(R.string.steam_library_filter_group_sort)
                    ) {
                        SteamLibrarySortOrder.entries.forEach { option ->
                            SteamLibraryChoiceChip(
                                selected = pending.sortOrder == option,
                                label = steamLibrarySortLabel(option),
                                onClick = { pending = pending.copy(sortOrder = option) }
                            )
                        }
                    }
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp
            ) {
                Button(
                    onClick = { onApply(pending) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        stringResource(
                            R.string.steam_library_filter_apply,
                            filteredCount(pending)
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SteamLibraryChoiceGroup(
    title: String,
    content: @Composable FlowRowScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SteamLibraryChoiceChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else null,
        modifier = Modifier.heightIn(min = 48.dp)
    )
}

@Composable
private fun steamLibraryOwnershipLabel(option: SteamLibraryOwnershipFilter): String =
    stringResource(
        when (option) {
            SteamLibraryOwnershipFilter.ALL -> R.string.steam_library_filter_ownership_all
            SteamLibraryOwnershipFilter.OWNED -> R.string.steam_library_filter_ownership_owned
            SteamLibraryOwnershipFilter.FAMILY_SHARED ->
                R.string.steam_library_filter_family_shared
        }
    )

@Composable
private fun steamLibraryPlayStatusLabel(option: SteamLibraryPlayStatusFilter): String =
    stringResource(
        when (option) {
            SteamLibraryPlayStatusFilter.ALL -> R.string.steam_library_filter_status_all
            SteamLibraryPlayStatusFilter.UNPLAYED -> R.string.steam_library_filter_unplayed
            SteamLibraryPlayStatusFilter.PLAYED -> R.string.steam_library_filter_played
            SteamLibraryPlayStatusFilter.RECENT -> R.string.steam_library_filter_recent
        }
    )

@Composable
private fun steamLibraryAchievementStatusLabel(
    option: SteamLibraryAchievementStatusFilter
): String = stringResource(
    when (option) {
        SteamLibraryAchievementStatusFilter.ALL -> R.string.steam_library_filter_achievement_all
        SteamLibraryAchievementStatusFilter.PERFECT -> R.string.steam_library_filter_perfect
        SteamLibraryAchievementStatusFilter.INCOMPLETE ->
            R.string.steam_library_filter_achievement_incomplete
        SteamLibraryAchievementStatusFilter.NO_ACHIEVEMENTS ->
            R.string.steam_library_filter_achievement_none
    }
)

@Composable
private fun steamLibraryPlaytimeLabel(option: SteamLibraryPlaytimeFilter): String =
    stringResource(
        when (option) {
            SteamLibraryPlaytimeFilter.ANY -> R.string.steam_library_filter_playtime_any
            SteamLibraryPlaytimeFilter.UNDER_TWO_HOURS ->
                R.string.steam_library_filter_playtime_under_two
            SteamLibraryPlaytimeFilter.TWO_TO_TWENTY_HOURS ->
                R.string.steam_library_filter_playtime_two_to_twenty
            SteamLibraryPlaytimeFilter.OVER_TWENTY_HOURS ->
                R.string.steam_library_filter_playtime_over_twenty
        }
    )

@Composable
private fun steamLibrarySortLabel(option: SteamLibrarySortOrder): String = stringResource(
    when (option) {
        SteamLibrarySortOrder.SMART -> R.string.steam_library_sort_smart
        SteamLibrarySortOrder.RECENT_PLAYTIME -> R.string.steam_library_sort_recent_playtime
        SteamLibrarySortOrder.TOTAL_PLAYTIME -> R.string.steam_library_sort_total_playtime
        SteamLibrarySortOrder.NAME_ASCENDING -> R.string.steam_library_sort_name_ascending
        SteamLibrarySortOrder.NAME_DESCENDING -> R.string.steam_library_sort_name_descending
    }
)
