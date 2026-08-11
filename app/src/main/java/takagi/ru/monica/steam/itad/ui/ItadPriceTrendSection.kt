package takagi.ru.monica.steam.itad.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.time.OffsetDateTime
import java.util.Date
import kotlin.math.abs
import takagi.ru.monica.R
import takagi.ru.monica.steam.itad.data.ItadHistoryLowRepository
import takagi.ru.monica.steam.itad.domain.ItadMoney
import takagi.ru.monica.steam.itad.domain.ItadPriceHistoryLoadResult
import takagi.ru.monica.steam.itad.domain.ItadPriceHistoryPoint

internal enum class ItadPriceTrendRange(val days: Long?) {
    SIX_MONTHS(183L),
    TWELVE_MONTHS(366L),
    ALL(null)
}

internal data class ItadPriceTrendSample(
    val timestampMillis: Long,
    val amount: Double,
    val currency: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ItadPriceTrendSection(
    repository: ItadHistoryLowRepository,
    appId: Int,
    countryCode: String?,
    modifier: Modifier = Modifier
) {
    var reloadToken by remember(appId, countryCode) { mutableIntStateOf(0) }
    val result by produceState<ItadPriceHistoryLoadResult?>(
        initialValue = null,
        key1 = appId,
        key2 = countryCode,
        key3 = reloadToken
    ) {
        value = null
        value = repository.loadPriceHistory(
            appId = appId,
            countryCode = countryCode,
            force = reloadToken > 0
        )
    }
    var selectedRange by rememberSaveable(appId, countryCode) {
        mutableStateOf(ItadPriceTrendRange.TWELVE_MONTHS)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.itad_price_trend_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.itad_price_trend_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when (val current = result) {
            null -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(strokeWidth = 3.dp)
                Text(
                    text = stringResource(R.string.itad_price_trend_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is ItadPriceHistoryLoadResult.Failure -> {
                Text(
                    text = stringResource(R.string.itad_price_trend_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { reloadToken++ }) {
                    Text(stringResource(R.string.itad_history_low_retry))
                }
            }

            is ItadPriceHistoryLoadResult.Success -> {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ItadPriceTrendRange.entries.forEachIndexed { index, range ->
                        SegmentedButton(
                            selected = selectedRange == range,
                            onClick = { selectedRange = range },
                            shape = SegmentedButtonDefaults.itemShape(
                                index,
                                ItadPriceTrendRange.entries.size
                            )
                        ) {
                            Text(
                                text = stringResource(range.labelResource()),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                val samples = remember(current.points, selectedRange) {
                    buildItadPriceTrendSamples(
                        points = current.points,
                        range = selectedRange,
                        nowMillis = System.currentTimeMillis()
                    )
                }
                if (samples.isEmpty()) {
                    Text(
                        text = stringResource(R.string.itad_price_trend_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ItadPriceStepChart(samples)
                    val lowest = samples.minBy(ItadPriceTrendSample::amount)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTrendDate(samples.first().timestampMillis),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatTrendDate(samples.last().timestampMillis),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.itad_price_trend_summary,
                            formatItadMoney(
                                ItadMoney(
                                    amount = lowest.amount,
                                    amountInt = 0L,
                                    currency = lowest.currency
                                )
                            ),
                            (samples.size - 1).coerceAtLeast(0)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (current.fromCache) {
                        Text(
                            text = stringResource(
                                if (current.stale) {
                                    R.string.itad_history_low_stale_cache
                                } else {
                                    R.string.itad_history_low_cached
                                }
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ItadPriceStepChart(samples: List<ItadPriceTrendSample>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(156.dp)
            .padding(vertical = 4.dp)
    ) {
        val left = 4.dp.toPx()
        val right = size.width - 4.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val minimum = samples.minOf(ItadPriceTrendSample::amount)
        val maximum = samples.maxOf(ItadPriceTrendSample::amount)
        val valueSpan = (maximum - minimum).takeIf { abs(it) > 0.0001 } ?: 1.0
        val startTime = samples.first().timestampMillis
        val endTime = samples.last().timestampMillis
        val timeSpan = (endTime - startTime).coerceAtLeast(1L)

        repeat(3) { index ->
            val y = top + (bottom - top) * index / 2f
            drawLine(
                color = gridColor,
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        fun xFor(timestamp: Long): Float = left +
            (right - left) * ((timestamp - startTime).toDouble() / timeSpan).toFloat()
        fun yFor(amount: Double): Float = bottom -
            (bottom - top) * ((amount - minimum) / valueSpan).toFloat()

        val path = Path()
        val first = samples.first()
        var previousX = xFor(first.timestampMillis)
        var previousY = yFor(first.amount)
        path.moveTo(previousX, previousY)
        samples.drop(1).forEach { sample ->
            val nextX = xFor(sample.timestampMillis)
            val nextY = yFor(sample.amount)
            path.lineTo(nextX, previousY)
            path.lineTo(nextX, nextY)
            previousX = nextX
            previousY = nextY
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
        drawCircle(
            color = lineColor,
            radius = 4.dp.toPx(),
            center = Offset(previousX, previousY)
        )
    }
}

internal fun buildItadPriceTrendSamples(
    points: List<ItadPriceHistoryPoint>,
    range: ItadPriceTrendRange,
    nowMillis: Long
): List<ItadPriceTrendSample> {
    val parsed = points.mapNotNull { point ->
        val timestamp = runCatching {
            OffsetDateTime.parse(point.timestamp).toInstant().toEpochMilli()
        }.getOrNull() ?: return@mapNotNull null
        timestamp to point
    }.sortedBy { it.first }
    val currency = parsed.mapNotNull { it.second.price?.currency }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
        ?: return emptyList()

    val activeDeals = mutableMapOf<Int, ItadMoney>()
    val rebuilt = buildList<ItadPriceTrendSample> {
        parsed.forEach { (timestamp, point) ->
            val price = point.price
            if (price == null || price.currency != currency) {
                activeDeals.remove(point.shopId)
            } else {
                activeDeals[point.shopId] = price
            }
            val minimum = activeDeals.values.minByOrNull(ItadMoney::amount)
                ?: return@forEach
            if (lastOrNull()?.amount != minimum.amount) {
                add(
                    ItadPriceTrendSample(
                        timestampMillis = timestamp,
                        amount = minimum.amount,
                        currency = currency
                    )
                )
            }
        }
    }
    val days = range.days ?: return rebuilt
    val cutoff = nowMillis - days * MILLIS_PER_DAY
    val preceding = rebuilt.lastOrNull { it.timestampMillis <= cutoff }
    val visible = rebuilt.filter { it.timestampMillis > cutoff }
    return buildList {
        if (preceding != null) {
            add(preceding.copy(timestampMillis = cutoff))
        }
        addAll(visible)
    }
}

private fun ItadPriceTrendRange.labelResource(): Int = when (this) {
    ItadPriceTrendRange.SIX_MONTHS -> R.string.itad_price_trend_six_months
    ItadPriceTrendRange.TWELVE_MONTHS -> R.string.itad_price_trend_twelve_months
    ItadPriceTrendRange.ALL -> R.string.itad_price_trend_all
}

private fun formatTrendDate(timestampMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(timestampMillis))

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L
