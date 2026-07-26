package takagi.ru.monica.steam.store.ui.gallery

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.steam.profile.SteamRemoteImageCache

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SteamStoreScreenshotViewer(
    gameName: String,
    screenshots: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    if (screenshots.isEmpty()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloader = remember(context) {
        SteamScreenshotDownloader(context.applicationContext)
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(screenshots.indices)
    ) { screenshots.size }
    var downloading by remember { mutableStateOf(false) }
    var pendingPermissionIndex by remember { mutableStateOf<Int?>(null) }

    val startDownload: (Int) -> Unit = { requestedIndex ->
        if (!downloading) {
            val safeIndex = requestedIndex.coerceIn(screenshots.indices)
            downloading = true
            scope.launch {
                val result = downloader.download(
                    imageUrl = screenshots[safeIndex],
                    gameName = gameName,
                    screenshotIndex = safeIndex
                )
                val message = when (result) {
                    is SteamScreenshotDownloadResult.Success -> context.getString(
                        R.string.steam_store_screenshot_download_success,
                        result.displayName
                    )
                    SteamScreenshotDownloadResult.PermissionRequired -> context.getString(
                        R.string.steam_store_screenshot_permission_denied
                    )
                    SteamScreenshotDownloadResult.InvalidSource -> context.getString(
                        R.string.steam_store_screenshot_invalid_source
                    )
                    SteamScreenshotDownloadResult.UnsupportedImage -> context.getString(
                        R.string.steam_store_screenshot_download_unsupported
                    )
                    SteamScreenshotDownloadResult.TooLarge -> context.getString(
                        R.string.steam_store_screenshot_download_too_large
                    )
                    SteamScreenshotDownloadResult.NetworkFailure,
                    SteamScreenshotDownloadResult.StorageFailure -> context.getString(
                        R.string.steam_store_screenshot_download_failed
                    )
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                downloading = false
            }
        }
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val index = pendingPermissionIndex
        pendingPermissionIndex = null
        if (granted && index != null) {
            startDownload(index)
        } else {
            Toast.makeText(
                context,
                R.string.steam_store_screenshot_permission_denied,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { page ->
                SteamStoreScreenshotPage(
                    url = screenshots[page],
                    contentDescription = stringResource(
                        R.string.steam_store_screenshot_description,
                        page + 1
                    )
                )
            }

            GalleryTopControls(
                gameName = gameName,
                downloading = downloading,
                onDismiss = onDismiss,
                onDownload = {
                    val currentIndex = pagerState.currentPage
                    if (downloader.requiresLegacyStoragePermission()) {
                        pendingPermissionIndex = currentIndex
                        storagePermissionLauncher.launch(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    } else {
                        startDownload(currentIndex)
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            GalleryPageControls(
                currentIndex = pagerState.currentPage,
                screenshotCount = screenshots.size,
                onPrevious = {
                    scope.launch {
                        pagerState.animateScrollToPage(
                            (pagerState.currentPage - 1).coerceAtLeast(0)
                        )
                    }
                },
                onNext = {
                    scope.launch {
                        pagerState.animateScrollToPage(
                            (pagerState.currentPage + 1).coerceAtMost(screenshots.lastIndex)
                        )
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GalleryTopControls(
    gameName: String,
    downloading: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColors = IconButtonDefaults.iconButtonColors(
        contentColor = Color.White,
        disabledContentColor = Color.White.copy(alpha = 0.38f)
    )
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.78f),
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(48.dp),
                colors = iconColors
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(
                        R.string.steam_store_screenshot_close
                    )
                )
            }
            Text(
                text = gameName,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = onDownload,
                enabled = !downloading,
                modifier = Modifier.size(48.dp),
                colors = iconColors
            ) {
                if (downloading) {
                    val loadingDescription = stringResource(
                        R.string.steam_store_screenshot_downloading
                    )
                    LoadingIndicator(
                        modifier = Modifier
                            .size(28.dp)
                            .semantics { contentDescription = loadingDescription },
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = stringResource(
                            R.string.steam_store_screenshot_download
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryPageControls(
    currentIndex: Int,
    screenshotCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColors = IconButtonDefaults.iconButtonColors(
        contentColor = Color.White,
        disabledContentColor = Color.White.copy(alpha = 0.28f)
    )
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.Black.copy(alpha = 0.78f),
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = currentIndex > 0,
                modifier = Modifier.size(48.dp),
                colors = iconColors
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(
                        R.string.steam_store_screenshot_previous
                    )
                )
            }
            Text(
                text = stringResource(
                    R.string.steam_store_screenshot_position,
                    currentIndex + 1,
                    screenshotCount
                ),
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(
                onClick = onNext,
                enabled = currentIndex < screenshotCount - 1,
                modifier = Modifier.size(48.dp),
                colors = iconColors
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(
                        R.string.steam_store_screenshot_next
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SteamStoreScreenshotPage(
    url: String,
    contentDescription: String
) {
    val context = LocalContext.current
    val cache = remember(context) {
        SteamRemoteImageCache.get(context.applicationContext)
    }
    val imageState by produceState<GalleryImageState>(
        initialValue = GalleryImageState.Loading,
        key1 = url
    ) {
        value = url
            .takeIf(String::isNotBlank)
            ?.let { cache.load(it)?.asImageBitmap() }
            ?.let(GalleryImageState::Loaded)
            ?: GalleryImageState.Failed
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 8.dp, vertical = 84.dp),
        contentAlignment = Alignment.Center
    ) {
        when (val state = imageState) {
            GalleryImageState.Loading -> LoadingIndicator(
                modifier = Modifier.size(56.dp),
                color = Color.White
            )
            GalleryImageState.Failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Color.White.copy(alpha = 0.72f)
                )
                Text(
                    text = stringResource(R.string.steam_store_screenshot_load_failed),
                    color = Color.White.copy(alpha = 0.82f),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                )
            }
            is GalleryImageState.Loaded -> ZoomableScreenshotImage(
                image = state.image,
                contentDescription = contentDescription
            )
        }
    }
}

@Composable
private fun ZoomableScreenshotImage(
    image: ImageBitmap,
    contentDescription: String
) {
    var scale by remember(image) { mutableFloatStateOf(1f) }
    var offset by remember(image) { mutableStateOf(Offset.Zero) }
    val transformableState = rememberTransformableState { _, zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        offset = if (nextScale <= 1f) Offset.Zero else offset + panChange
    }

    Image(
        bitmap = image,
        contentDescription = contentDescription,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
            .transformable(
                state = transformableState,
                canPan = { scale > 1f },
                lockRotationOnZoomPan = true
            ),
        contentScale = ContentScale.Fit
    )
}

private sealed interface GalleryImageState {
    data object Loading : GalleryImageState
    data object Failed : GalleryImageState
    data class Loaded(val image: ImageBitmap) : GalleryImageState
}
