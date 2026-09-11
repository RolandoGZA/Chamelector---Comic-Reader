package com.rolandb.chamelector.ui.theme.screens.comicviewer

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicViewerScreen(
    comicUri: Uri,
    comicTitle: String = "Lector de Cómics",
    onBackPress: () -> Unit,
    viewModel: ComicViewerViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(comicUri) {
        viewModel.loadComicPages(context, comicUri)
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = uiState.isControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                TopAppBar(
                    title = { Text(text = comicTitle, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onBackPress) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.7f),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = (uiState.isControlsVisible && uiState.totalPages > 0),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Página ${uiState.currentPage + 1} de ${uiState.totalPages}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isLoading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Cargando páginas...", color = Color.White)
                    }
                }

                uiState.errorMessage != null -> {
                    Text(
                        text = uiState.errorMessage ?: "Error desconocido",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                uiState.pages.isNotEmpty() -> {
                    val pagerState = rememberPagerState { uiState.pages.size }
                    var isAnyPageZoomed by remember { mutableStateOf(false) }

                    LaunchedEffect(pagerState.currentPage) {
                        viewModel.onPageChanged(pagerState.currentPage)
                        isAnyPageZoomed = false
                    }

                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !isAnyPageZoomed,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) { pageIndex ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .zIndex(
                                    // Use a snapshot flow or read in graphicsLayer to avoid composition overhead
                                    if (pagerState.currentPage >= pageIndex) 1f else 0f
                                )
                                .graphicsLayer {
                                    val pageOffset = ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction)
                                    
                                    // Stack pages by counter-acting the pager's natural horizontal layout
                                    translationX = pageOffset * size.width

                                    if (pageOffset > 0f && pageOffset < 1f) {
                                        // The page being turned away
                                        rotationY = -180f * pageOffset
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                        cameraDistance = 8f * density
                                        
                                        // Hide back of page after 90 degrees
                                        alpha = if (pageOffset > 0.5f) 0f else 1f
                                    } else if (pageOffset <= 0f && pageOffset > -1f) {
                                        // The page being revealed underneath
                                        rotationY = 0f
                                        alpha = 1f
                                    } else {
                                        alpha = if (pageOffset.absoluteValue < 0.1f) 1f else 0f
                                    }
                                }
                        ) {
                            ZoomableComicPage(
                                bitmap = uiState.pages[pageIndex],
                                onTap = viewModel::toggleControlsVisibility,
                                onZoomChanged = { isAnyPageZoomed = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ZoomableComicPage(
    bitmap: android.graphics.Bitmap,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(scale) {
        onZoomChanged(scale > 1f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .pointerInput(scale) {
                // Manual transformation detection to avoid blocking the Pager
                awaitEachGesture {
                    var zoom = 1f
                    var pan = Offset.Zero
                    var pastTouchSlop = false
                    val touchSlop = viewConfiguration.touchSlop

                    // Wait for the first down
                    awaitFirstDown(requireUnconsumed = false)
                    
                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        
                        if (!canceled) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            if (!pastTouchSlop) {
                                zoom *= zoomChange
                                pan += panChange
                                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                val zoomMotion = (zoom - 1f).absoluteValue * centroidSize
                                val panMotion = pan.getDistance()

                                if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                    pastTouchSlop = true
                                }
                            }

                            if (pastTouchSlop) {
                                // If scale is 1 and we are not zooming (just panning), 
                                // we check if it's a multi-touch. 
                                // If it's a single-touch pan at scale 1, we DON'T consume.
                                val isMultiTouch = event.changes.size > 1
                                if (scale > 1f || isMultiTouch) {
                                    // Handle zoom/pan
                                    val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                                    scale = newScale
                                    if (scale > 1f) {
                                        offsetX += panChange.x
                                        offsetY += panChange.y
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                    } while (!canceled && event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Página del cómic",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
        )
    }
}
