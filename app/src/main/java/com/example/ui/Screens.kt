package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.scale
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.MediaTrack
import com.example.data.Playlist
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.math.cos

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.FileProvider
import android.content.Intent
import android.media.MediaMetadataRetriever
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

fun shareMediaTrack(context: Context, track: MediaTrack) {
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            if (track.filePath.startsWith("content://")) {
                putExtra(Intent.EXTRA_STREAM, Uri.parse(track.filePath))
                type = if (track.isVideo) "video/*" else "audio/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else if (!track.filePath.startsWith("http")) {
                val file = File(track.filePath)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    putExtra(Intent.EXTRA_STREAM, uri)
                    type = if (track.isVideo) "video/*" else "audio/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    putExtra(Intent.EXTRA_TEXT, "Mendengarkan: ${track.title} - ${track.artist}")
                    type = "text/plain"
                }
            } else {
                putExtra(Intent.EXTRA_TEXT, "Mendengarkan: ${track.title} - ${track.artist}")
                type = "text/plain"
            }
        }
        val chooser = Intent.createChooser(sendIntent, "Bagikan Berkas Media")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        try {
            val fallbackIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "${track.title} - ${track.artist}")
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(fallbackIntent, "Bagikan"))
        } catch (ex: Exception) {
            Toast.makeText(context, "Gagal membagikan berkas", Toast.LENGTH_SHORT).show()
        }
    }
}

fun shareFolderPath(context: Context, folderName: String) {
    try {
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Folder Media: $folderName")
            type = "text/plain"
        }
        val chooser = Intent.createChooser(intent, "Bagikan Folder")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Gagal membagikan folder", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun AnimatedVideoThumbnail(
    filePath: String,
    modifier: Modifier = Modifier
) {
    var frameBitmap by remember(filePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    val context = LocalContext.current

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                if (filePath.startsWith("content://") || filePath.startsWith("android.resource://")) {
                    retriever.setDataSource(context, Uri.parse(filePath))
                } else if (!filePath.startsWith("http")) {
                    val file = File(filePath)
                    if (file.exists()) {
                        retriever.setDataSource(file.absolutePath)
                    }
                }
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationUs = (durationStr?.toLongOrNull() ?: 10000L) * 1000L
                val timePoints = listOf(
                    durationUs / 5,
                    (durationUs * 2) / 5,
                    (durationUs * 3) / 5,
                    (durationUs * 4) / 5
                )
                var idx = 0
                while (true) {
                    val timeUs = timePoints[idx % timePoints.size]
                    val bmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (bmp != null) {
                        withContext(Dispatchers.Main) {
                            frameBitmap = bmp
                        }
                    }
                    idx++
                    delay(1400)
                }
            } catch (e: Exception) {
                // fallback gracefully
            } finally {
                try { retriever.release() } catch (e: Exception) {}
            }
        }
    }

    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        if (frameBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = frameBitmap!!.asImageBitmap(),
                contentDescription = "Video Thumbnail Bergerak",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(10.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("LIVE", color = AccentTeal, fontSize = 7.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        } else {
            Icon(Icons.Default.Movie, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(36.dp))
        }
    }
}

// --- DYNAMIC THEMING SYSTEM & COLOR CONSTANTS ---
data class AppColors(
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val card: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val isDark: Boolean,
    val iconColor: Color
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(
        primary = Color(0xFFFF8A00),
        secondary = Color(0xFFFFB300),
        background = Color(0xFF141318),
        card = Color(0xFF22202A),
        surface = Color(0xFF1B1922),
        textPrimary = Color.White,
        textSecondary = Color(0xFFA5A1AF),
        divider = Color(0xFF383540),
        isDark = true,
        iconColor = Color(0xFFFF8A00)
    )
}

val PrimaryGold: Color
    @Composable get() = LocalAppColors.current.primary

val DarkBackground: Color
    @Composable get() = LocalAppColors.current.background

val CardBackground: Color
    @Composable get() = LocalAppColors.current.card

val DividerColor: Color
    @Composable get() = LocalAppColors.current.divider

val AccentTeal: Color
    @Composable get() = LocalAppColors.current.secondary

val UnselectedWhite: Color
    @Composable get() = LocalAppColors.current.textSecondary

val TextPrimary: Color
    @Composable get() = LocalAppColors.current.textPrimary

val IsDarkTheme: Boolean
    @Composable get() = LocalAppColors.current.isDark

val HeaderBackground: Color
    @Composable get() = LocalAppColors.current.surface

@Composable
fun WindowsFluentLogo(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 18.dp) {
    // Windows 11 Style 4-tiles Logo
    Row(
        modifier = modifier.size(size),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(modifier = Modifier.size(size / 2.3f).clip(RoundedCornerShape(2.dp)).background(Color(0xFF00ADEF)))
            Box(modifier = Modifier.size(size / 2.3f).clip(RoundedCornerShape(2.dp)).background(Color(0xFF00A4EF)))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(modifier = Modifier.size(size / 2.3f).clip(RoundedCornerShape(2.dp)).background(Color(0xFF00C7F2)))
            Box(modifier = Modifier.size(size / 2.3f).clip(RoundedCornerShape(2.dp)).background(Color(0xFF0078D7)))
        }
    }
}

@Composable
fun HarmoniMainScreen(viewModel: MediaViewModel) {
    val isSystemDark = isSystemInDarkTheme()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val selectedThemeId by viewModel.selectedThemeId.collectAsStateWithLifecycle()
    val customPrimaryColor by viewModel.customPrimaryColor.collectAsStateWithLifecycle()
    val customSecondaryColor by viewModel.customSecondaryColor.collectAsStateWithLifecycle()
    val bgTransparency by viewModel.backgroundTransparency.collectAsStateWithLifecycle()
    val backgroundStyle by viewModel.backgroundStyle.collectAsStateWithLifecycle()
    val showFileBorders by viewModel.showFileBorders.collectAsStateWithLifecycle()
    val showNavLightAnim by viewModel.showNavLightAnim.collectAsStateWithLifecycle()
    val isTextTitleUppercase by viewModel.isTextTitleUppercase.collectAsStateWithLifecycle()

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val activeScreen = viewModel.activeScreen
    val downloadStatus by viewModel.downloadStatus.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val onOpenDrawer: () -> Unit = {
        coroutineScope.launch { drawerState.open() }
    }
    val context = LocalContext.current

    // Storage file search picker for Folder navigation icon
    val storageFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importMultipleUris(context, uris)
            Toast.makeText(context, "Memuat ${uris.size} file dari penyimpanan...", Toast.LENGTH_SHORT).show()
        }
    }

    // Dynamic background light animation transition
    val bgInfiniteTransition = rememberInfiniteTransition(label = "BgLightAnim")
    val lightAnimPhase by bgInfiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "LightPhase"
    )

    // Windows Theme Accent Palette
    val (presetPrimary, presetSecondary) = when (selectedThemeId) {
        "Orange", "Sunset Orange" -> if (isDarkMode) Color(0xFFFF8A00) to Color(0xFFFFB300) else Color(0xFFE65100) to Color(0xFFFF8A00)
        "Gold", "Windows Gold" -> if (isDarkMode) Color(0xFFFF9800) to Color(0xFFFFC107) else Color(0xFFE65100) to Color(0xFFFF9800)
        "Blue", "Windows Blue" -> if (isDarkMode) Color(0xFF60CDFF) to Color(0xFF00B7C3) else Color(0xFF0078D7) to Color(0xFF005A9E)
        "Teal", "Mica Teal" -> if (isDarkMode) Color(0xFF4DD0E1) to Color(0xFF80CBC4) else Color(0xFF00838F) to Color(0xFF00695C)
        "Purple", "Cyber Purple" -> if (isDarkMode) Color(0xFFCE93D8) to Color(0xFF80D8FF) else Color(0xFF7B1FA2) to Color(0xFF0091EA)
        "Emerald", "Xbox Emerald" -> if (isDarkMode) Color(0xFF81C784) to Color(0xFF80CBC4) else Color(0xFF2E7D32) to Color(0xFF00897B)
        "Crimson", "Crimson Red" -> if (isDarkMode) Color(0xFFFF5252) to Color(0xFFFFB74D) else Color(0xFFC62828) to Color(0xFFE65100)
        "Silver", "Platinum Silver" -> if (isDarkMode) Color(0xFFE0E0E0) to Color(0xFFFF8A00) else Color(0xFF616161) to Color(0xFFE65100)
        else -> if (isDarkMode) Color(0xFFFF8A00) to Color(0xFFFFB300) else Color(0xFFE65100) to Color(0xFFFF8A00)
    }

    val primaryColor = customPrimaryColor?.let { Color(it) } ?: presetPrimary
    val secondaryColor = customSecondaryColor?.let { Color(it) } ?: presetSecondary

    // Dynamic Fluent Acrylic & Frosted Glass Box Model
    val baseDark = Color(0xFF101014)
    val baseLight = Color(0xFFF4F4F6)
    val cardDark = Color(0xFF1C1A24)
    val cardLight = Color(0xFFFFFFFF)

    // Solid default when bgTransparency is 0f; Frosted glass box with minimum 65% opacity when > 0f so text is always clear
    val glassCardAlpha = if (bgTransparency <= 0f) 1f else (1f - bgTransparency * 0.35f).coerceIn(0.65f, 1f)
    val backgroundColor = if (isDarkMode) baseDark else baseLight
    val cardColor = if (isDarkMode) cardDark.copy(alpha = glassCardAlpha) else cardLight.copy(alpha = glassCardAlpha)
    val navAlpha = if (bgTransparency <= 0f) 1f else (1f - bgTransparency * 0.30f).coerceIn(0.70f, 1f)
    val surfaceColor = if (isDarkMode) Color(0xFF131218).copy(alpha = navAlpha) else Color(0xFFFFFFFF).copy(alpha = navAlpha)
    val textPrimaryColor = if (isDarkMode) Color(0xFFFFFFFF) else Color(0xFF121118)
    val textSecondaryColor = if (isDarkMode) Color(0xFFA09CA8) else Color(0xFF555260)
    val dividerColor = if (isDarkMode) Color(0xFF423E4C).copy(alpha = 0.5f) else Color(0xFFBFB9C9).copy(alpha = 0.6f)
    val iconColor = primaryColor

    val currentAppColors = AppColors(
        primary = primaryColor,
        secondary = secondaryColor,
        background = backgroundColor,
        card = cardColor,
        surface = surfaceColor,
        textPrimary = textPrimaryColor,
        textSecondary = textSecondaryColor,
        divider = dividerColor,
        isDark = isDarkMode,
        iconColor = iconColor
    )

    CompositionLocalProvider(LocalAppColors provides currentAppColors) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = currentAppColors.surface,
                    drawerContentColor = currentAppColors.textPrimary,
                    modifier = Modifier.widthIn(max = 340.dp)
                ) {
                    StudioDrawerContent(
                        viewModel = viewModel,
                        onClose = { coroutineScope.launch { drawerState.close() } }
                    )
                }
            }
        ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (isDarkMode) Color(0xFF101014) else Color(0xFFF4F4F6)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Windows 11 Fluent Bloom / Dynamic Background Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Base Canvas Tint
                    drawRect(color = if (isDarkMode) Color(0xFF101014) else Color(0xFFF4F4F6))

                    val glowScale = if (bgTransparency <= 0f) 0.15f else (0.18f + bgTransparency * 0.45f).coerceIn(0.18f, 0.65f)

                    when (backgroundStyle) {
                        "Gradasi Sunset" -> {
                            // Warm sunset gradient from gold/orange to crimson and midnight indigo
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = if (isDarkMode) listOf(
                                        Color(0xFF2E1005).copy(alpha = 0.8f),
                                        Color(0xFF1A0A1E).copy(alpha = 0.9f),
                                        Color(0xFF101014)
                                    ) else listOf(
                                        Color(0xFFFFE0B2).copy(alpha = 0.6f),
                                        Color(0xFFFFCCBC).copy(alpha = 0.5f),
                                        Color(0xFFF4F4F6)
                                    )
                                )
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFFFF7043).copy(alpha = 0.35f * glowScale), Color.Transparent),
                                    center = Offset(size.width * 0.8f, size.height * 0.2f),
                                    radius = size.width * 0.9f
                                )
                            )
                        }
                        "Gradasi Aurora" -> {
                            // Northern Lights Aurora Green/Teal into Cosmic Violet
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = if (isDarkMode) listOf(
                                        Color(0xFF04201C),
                                        Color(0xFF140728),
                                        Color(0xFF101014)
                                    ) else listOf(
                                        Color(0xFFE0F2F1),
                                        Color(0xFFF3E5F5),
                                        Color(0xFFF4F4F6)
                                    ),
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, size.height)
                                )
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFF00E676).copy(alpha = 0.25f * glowScale), Color.Transparent),
                                    center = Offset(size.width * 0.25f, size.height * 0.35f),
                                    radius = size.width * 0.8f
                                )
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFFE040FB).copy(alpha = 0.25f * glowScale), Color.Transparent),
                                    center = Offset(size.width * 0.85f, size.height * 0.65f),
                                    radius = size.width * 0.85f
                                )
                            )
                        }
                        "Gradasi Cyber" -> {
                            // Cyberpunk neon cyan & magenta gradient
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = if (isDarkMode) listOf(
                                        Color(0xFF001B2E),
                                        Color(0xFF28002B),
                                        Color(0xFF101014)
                                    ) else listOf(
                                        Color(0xFFE1F5FE),
                                        Color(0xFFFCE4EC),
                                        Color(0xFFF4F4F6)
                                    )
                                )
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.35f * glowScale), Color.Transparent),
                                    center = Offset(size.width * 0.2f, size.height * 0.2f),
                                    radius = size.width * 0.8f
                                )
                            )
                        }
                        "Classic Modern" -> {
                            // Minimal dark slate / charcoal modern solid with refined specular lighting
                            drawRect(color = if (isDarkMode) Color(0xFF13131A) else Color(0xFFECECEF))
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(primaryColor.copy(alpha = 0.12f), Color.Transparent),
                                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                                    radius = size.width * 0.7f
                                )
                            )
                        }
                        "Solid Minimal" -> {
                            // Pure solid minimal background
                            drawRect(color = if (isDarkMode) Color(0xFF101014) else Color(0xFFF4F4F6))
                        }
                        "Animasi Cahaya" -> {
                            // Dynamic animated sweeping light beams & luminous ambient pulse
                            val lightX = (size.width * 0.5f) + (size.width * 0.45f * sin(lightAnimPhase))
                            val lightY = (size.height * 0.4f) + (size.height * 0.3f * cos(lightAnimPhase * 0.8f))
                            val sweepX = (size.width * 0.5f) - (size.width * 0.4f * sin(lightAnimPhase * 1.3f))
                            val sweepY = (size.height * 0.7f) + (size.height * 0.25f * sin(lightAnimPhase * 0.9f))

                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(primaryColor.copy(alpha = 0.40f * glowScale), Color.Transparent),
                                    center = Offset(lightX, lightY),
                                    radius = size.width * 0.95f
                                )
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(secondaryColor.copy(alpha = 0.35f * glowScale), Color.Transparent),
                                    center = Offset(sweepX, sweepY),
                                    radius = size.width * 0.85f
                                )
                            )
                        }
                        "Neon Glow" -> {
                            // Pulsing dual neon auras
                            val pulseRadius = size.width * (0.75f + 0.15f * sin(lightAnimPhase))
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(primaryColor.copy(alpha = 0.45f * glowScale), Color.Transparent),
                                    center = Offset(size.width * 0.3f, size.height * 0.25f),
                                    radius = pulseRadius
                                )
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(secondaryColor.copy(alpha = 0.40f * glowScale), Color.Transparent),
                                    center = Offset(size.width * 0.75f, size.height * 0.75f),
                                    radius = pulseRadius
                                )
                            )
                        }
                        "Mica Frosted" -> {
                            // Frosted glass acrylic style
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(primaryColor.copy(alpha = 0.35f * glowScale), Color.Transparent),
                                    center = Offset(size.width * 0.2f, size.height * 0.2f),
                                    radius = size.width * 0.85f
                                )
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(secondaryColor.copy(alpha = 0.30f * glowScale), Color.Transparent),
                                    center = Offset(size.width * 0.8f, size.height * 0.8f),
                                    radius = size.width * 0.8f
                                )
                            )
                        }
                        else -> {
                            // "Standar" Windows 11 Fluent Bloom
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(primaryColor.copy(alpha = (glowScale * 0.85f).coerceIn(0.10f, 0.55f)), Color.Transparent),
                                    center = Offset(size.width * 0.2f, size.height * 0.15f),
                                    radius = size.width * 0.85f
                                )
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(secondaryColor.copy(alpha = (glowScale * 0.70f).coerceIn(0.08f, 0.45f)), Color.Transparent),
                                    center = Offset(size.width * 0.85f, size.height * 0.75f),
                                    radius = size.width * 0.8f
                                )
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(primaryColor.copy(alpha = (glowScale * 0.50f).coerceIn(0.06f, 0.35f)), Color.Transparent),
                                    center = Offset(size.width * 0.5f, size.height * 0.95f),
                                    radius = size.width * 0.65f
                                )
                            )
                        }
                    }

                    // Frosted Glass Top Sheen
                    if (bgTransparency > 0f) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = if (isDarkMode) listOf(
                                    Color.White.copy(alpha = 0.05f * bgTransparency),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.18f * bgTransparency)
                                ) else listOf(
                                    Color.White.copy(alpha = 0.38f * bgTransparency),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.06f * bgTransparency)
                                )
                            )
                        )
                    }
                }

                Scaffold(
                    containerColor = Color.Transparent,
                    bottomBar = {
                        if (!viewModel.isVideoLocked && !(activeScreen == "Video" && viewModel.isVideoFullscreen)) {
                            val navAlpha = if (bgTransparency <= 0f) 1f else (1f - bgTransparency * 0.30f).coerceIn(0.70f, 1f)
                            val navContainerColor = if (isDarkMode) Color(0xFF131218).copy(alpha = navAlpha)
                                                    else Color(0xFFFFFFFF).copy(alpha = navAlpha)
                            // Full-Width Edge-to-Edge Solid / Frosted Acrylic Navbar
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = navContainerColor,
                                tonalElevation = if (bgTransparency <= 0f) 3.dp else 0.dp,
                                shadowElevation = if (bgTransparency <= 0f) 4.dp else 0.dp
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(0.5.dp, DividerColor.copy(alpha = 0.35f))
                                        .windowInsetsPadding(WindowInsets.navigationBars)
                                        .padding(vertical = 4.dp)
                                ) {
                                    if (showNavLightAnim) {
                                        val lightOffset = (sin(lightAnimPhase) + 1f) / 2f
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(2.dp)
                                                .align(Alignment.TopCenter)
                                                .background(
                                                    Brush.horizontalGradient(
                                                        colors = listOf(
                                                            Color.Transparent,
                                                            PrimaryGold.copy(alpha = 0.85f),
                                                            Color.Transparent
                                                        ),
                                                        startX = (lightOffset * 1000f) - 300f,
                                                        endX = (lightOffset * 1000f) + 300f
                                                    )
                                                )
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val navItems = listOf(
                                            Triple<String, ImageVector, String>("Library", Icons.Default.LibraryMusic, "Pustaka"),
                                            Triple<String, ImageVector, String>("FolderNav", Icons.Default.Folder, "Folder"),
                                            Triple<String, ImageVector, String>("Player", Icons.Default.MusicNote, "Audio"),
                                            Triple<String, ImageVector, String>("Video", Icons.Default.Movie, "Video"),
                                            Triple<String, ImageVector, String>("Playlist", Icons.Default.PlaylistPlay, "Playlist")
                                        )

                                        navItems.forEach { item ->
                                            val route = item.first
                                            val icon = item.second
                                            val label = item.third
                                            val isSelected = if (route == "FolderNav") {
                                                false
                                            } else if (route == "Library") {
                                                activeScreen == "Library" && viewModel.selectedMediaTab != "Folder"
                                            } else {
                                                activeScreen == route
                                            }

                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (isSelected) PrimaryGold.copy(alpha = 0.15f) else Color.Transparent)
                                                    .clickable {
                                                        if (route == "FolderNav") {
                                                            // Icon Folder searches file in storage directly
                                                            Toast.makeText(context, "Mencari file dalam penyimpanan...", Toast.LENGTH_SHORT).show()
                                                            try {
                                                                storageFilePickerLauncher.launch(arrayOf("audio/*", "video/*", "*/*"))
                                                            } catch (e: Exception) {
                                                                viewModel.selectedMediaTab = "Folder"
                                                                viewModel.activeScreen = "Library"
                                                            }
                                                        } else if (route == "Library") {
                                                            if (viewModel.selectedMediaTab == "Folder") {
                                                                viewModel.selectedMediaTab = "Audio"
                                                            }
                                                            viewModel.activeScreen = "Library"
                                                        } else {
                                                            viewModel.activeScreen = route
                                                        }
                                                    }
                                                    .padding(vertical = 6.dp)
                                                    .testTag("nav_item_${route.lowercase()}")
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = label,
                                                    tint = if (isSelected) PrimaryGold else TextPrimary.copy(alpha = 0.55f),
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = label,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                    color = if (isSelected) PrimaryGold else TextPrimary.copy(alpha = 0.65f)
                                                )
                                                if (isSelected) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .size(width = 16.dp, height = 2.5.dp)
                                                            .clip(RoundedCornerShape(2.dp))
                                                            .background(PrimaryGold)
                                                    )
                                                } else {
                                                    Spacer(modifier = Modifier.height(4.5.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(Color.Transparent)
                    ) {
                        // App Screens
                        AnimatedContent(
                            targetState = activeScreen,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(220))
                            },
                            label = "ScreenTransition"
                        ) { screen ->
                            when (screen) {
                                "Library" -> LibraryScreen(viewModel, onOpenDrawer)
                                "Player" -> PlayerScreen(viewModel, onOpenDrawer)
                                "Search" -> SearchScreen(viewModel, onOpenDrawer)
                                "Video" -> VideoScreen(viewModel, onOpenDrawer)
                                "Playlist" -> PlaylistScreen(viewModel, onOpenDrawer)
                                else -> LibraryScreen(viewModel, onOpenDrawer)
                            }
                        }

                        // Global Notification Toast overlay banner
                        downloadStatus?.let { status ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = PrimaryGold),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(16.dp)
                                    .shadow(8.dp, RoundedCornerShape(12.dp)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.Black,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.4.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = status,
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        // Floating mini-player if playing in background and not actively on player screen
                        if (currentTrack != null && activeScreen != "Player" && activeScreen != "Video" && !viewModel.isVideoLocked) {
                            val track = currentTrack!!
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = CardBackground),
                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clickable { viewModel.activeScreen = if (track.isVideo) "Video" else "Player" }
                                    .border(1.2.dp, PrimaryGold.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Transparent)
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DividerColor.copy(alpha = 0.25f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (track.isVideo) {
                                            Icon(Icons.Default.Movie, contentDescription = null, tint = AccentTeal)
                                        } else {
                                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = PrimaryGold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${track.artist} • ${track.format}",
                                            fontSize = 11.sp,
                                            color = UnselectedWhite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = { viewModel.togglePlayPause() }) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Mainkan",
                                            tint = PrimaryGold
                                        )
                                    }
                                }
                            }
                        }

                        // Global Add To Playlist dialog overlay
                        val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()
                        viewModel.trackToAddToPlaylist?.let { track ->
                            AddToPlaylistDialog(
                                track = track,
                                playlists = playlists,
                                onDismiss = { viewModel.trackToAddToPlaylist = null },
                                onAddTrack = { playlistId ->
                                    viewModel.addTrackToPlaylist(playlistId, track.id)
                                    viewModel.trackToAddToPlaylist = null
                                },
                                onCreatePlaylist = { name ->
                                    viewModel.createPlaylistAndAddTrack(name, track)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
}

// --- CUSTOM CIRCULAR THUMB & SLENDER TRACK SLIDER ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    activeColor: Color = PrimaryGold,
    inactiveColor: Color = DividerColor.copy(alpha = 0.45f),
    thumbSize: androidx.compose.ui.unit.Dp = 14.dp,
    trackHeight: androidx.compose.ui.unit.Dp = 3.dp
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        modifier = modifier,
        thumb = {
            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(if (enabled) activeColor else inactiveColor)
                    .border(1.5.dp, if (IsDarkTheme) Color.White.copy(alpha = 0.9f) else Color(0xFF101014), CircleShape)
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(trackHeight),
                colors = SliderDefaults.colors(
                    activeTrackColor = if (enabled) activeColor else inactiveColor.copy(alpha = 0.5f),
                    inactiveTrackColor = inactiveColor
                )
            )
        }
    )
}

// --- HAMBURGER SLIDER DRAWER CONTENT (EXPANDABLE ACCORDION MENUS) ---
@Composable
fun StudioDrawerContent(
    viewModel: MediaViewModel,
    onClose: () -> Unit
) {
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val selectedThemeId by viewModel.selectedThemeId.collectAsStateWithLifecycle()
    val bgTransparency by viewModel.backgroundTransparency.collectAsStateWithLifecycle()
    val backgroundStyle by viewModel.backgroundStyle.collectAsStateWithLifecycle()
    val showFileBorders by viewModel.showFileBorders.collectAsStateWithLifecycle()
    val showNavLightAnim by viewModel.showNavLightAnim.collectAsStateWithLifecycle()
    val isTextTitleUppercase by viewModel.isTextTitleUppercase.collectAsStateWithLifecycle()

    val isEffectsEnabled by viewModel.isEffectsEnabled.collectAsStateWithLifecycle()
    val reverbPreset by viewModel.reverbPreset.collectAsStateWithLifecycle()
    val pitchSemiTones by viewModel.pitchSemiTones.collectAsStateWithLifecycle()
    val superBassStrength by viewModel.superBassStrength.collectAsStateWithLifecycle()
    val virtualizer3DStrength by viewModel.virtualizer3DStrength.collectAsStateWithLifecycle()
    val lrAudioBalance by viewModel.lrAudioBalance.collectAsStateWithLifecycle()
    val vocalClarity by viewModel.vocalClarity.collectAsStateWithLifecycle()
    val trebleSparkle by viewModel.trebleSparkle.collectAsStateWithLifecycle()
    val warmTubeSaturation by viewModel.warmTubeSaturation.collectAsStateWithLifecycle()
    val dynamicVolumeLeveler by viewModel.dynamicVolumeLeveler.collectAsStateWithLifecycle()

    val isEqualizerEnabled by viewModel.isEqualizerEnabled.collectAsStateWithLifecycle()
    val equalizerBands by viewModel.equalizerBands.collectAsStateWithLifecycle()
    val selectedPresetName by viewModel.selectedPresetName.collectAsStateWithLifecycle()

    val volume by viewModel.volume.collectAsStateWithLifecycle()
    val videoVolume by viewModel.videoVolume.collectAsStateWithLifecycle()
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsStateWithLifecycle()
    val sleepTimerRemaining by viewModel.sleepTimerRemainingSeconds.collectAsStateWithLifecycle()
    val allTracks by viewModel.allTracks.collectAsStateWithLifecycle()

    // Accordion expand/collapse states (all closed by default)
    var isThemeExpanded by remember { mutableStateOf(false) }
    var isEffectsExpanded by remember { mutableStateOf(false) }
    var isEqualizerExpanded by remember { mutableStateOf(false) }
    var isVolumeExpanded by remember { mutableStateOf(false) }
    var isSleepTimerExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Drawer Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Pengaturan Media",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Studio DSP & Personalisasi",
                        fontSize = 11.sp,
                        color = UnselectedWhite
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Tutup Menu", tint = UnselectedWhite)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ================== DROPDOWN 1: TEMA & TAMPILAN ==================
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DividerColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isThemeExpanded = !isThemeExpanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tema & Transparansi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${(bgTransparency * 100).toInt()}% Glass",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryGold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isThemeExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = UnselectedWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = isThemeExpanded) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        // Dark / Light Fluent Mode Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Mode Tampilan", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(DividerColor.copy(alpha = 0.4f))
                                    .clickable { viewModel.toggleDarkMode() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                    contentDescription = null,
                                    tint = PrimaryGold,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isDarkMode) "Gelap" else "Terang",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Theme Color Accent Swatches Grid
                        Text("Pilihan Warna Tema", fontSize = 11.sp, color = UnselectedWhite, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        val themePalettes = listOf(
                            Triple("Gold", "Gold", Color(0xFFFFB900)),
                            Triple("Orange", "Orange", Color(0xFFF7630C)),
                            Triple("Blue", "Blue", Color(0xFF0078D7)),
                            Triple("Teal", "Teal", Color(0xFF00B7C3)),
                            Triple("Purple", "Purple", Color(0xFF881798)),
                            Triple("Emerald", "Green", Color(0xFF107C41)),
                            Triple("Crimson", "Red", Color(0xFFE81123)),
                            Triple("Silver", "Silver", Color(0xFFE1DFDD))
                        )

                        themePalettes.chunked(4).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowItems.forEach { (themeId, label, color) ->
                                    val isSelected = selectedThemeId == themeId || (selectedThemeId == "Windows $themeId")
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) color.copy(alpha = 0.25f) else DividerColor.copy(alpha = 0.15f))
                                            .border(1.dp, if (isSelected) color else DividerColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                            .clickable { viewModel.setTheme(themeId) }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = label,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) PrimaryGold else TextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Background Layout & Color Styles
                        Text("Pilihan Gaya & Background Warna", fontSize = 11.sp, color = UnselectedWhite, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        val bgStyles = listOf(
                            "Standar" to "Standar (Bloom)",
                            "Gradasi Sunset" to "Gradasi Sunset",
                            "Gradasi Aurora" to "Gradasi Aurora",
                            "Gradasi Cyber" to "Gradasi Cyber",
                            "Classic Modern" to "Classic Modern",
                            "Solid Minimal" to "Solid Minimal",
                            "Animasi Cahaya" to "Animasi Cahaya",
                            "Neon Glow" to "Neon Glow",
                            "Mica Frosted" to "Mica Frosted"
                        )
                        bgStyles.chunked(3).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.5.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                rowItems.forEach { (styleKey, styleLabel) ->
                                    val isSelected = backgroundStyle == styleKey || (styleKey == "Standar" && (backgroundStyle == "Standard" || backgroundStyle == "Standar"))
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) PrimaryGold.copy(alpha = 0.22f) else DividerColor.copy(alpha = 0.15f))
                                            .border(1.dp, if (isSelected) PrimaryGold else DividerColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                            .clickable { viewModel.setBackgroundStyle(styleKey) }
                                            .padding(vertical = 6.dp, horizontal = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = styleLabel,
                                            fontSize = 9.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) PrimaryGold else TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Background Transparency Slider (Round thumb, default 100%)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Transparansi Background", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                            Text("${(bgTransparency * 100).toInt()}%", fontSize = 12.sp, color = PrimaryGold, fontWeight = FontWeight.Bold)
                        }

                        RoundSlider(
                            value = bgTransparency,
                            onValueChange = { viewModel.setBackgroundTransparency(it) },
                            valueRange = 0f..1f,
                            activeColor = PrimaryGold,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Quick presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                0f to "Solid",
                                0.4f to "Mica 40%",
                                0.85f to "Aero 85%",
                                1.0f to "Glass 100%"
                            ).forEach { (value, label) ->
                                val isSelected = kotlin.math.abs(bgTransparency - value) < 0.05f
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) PrimaryGold.copy(alpha = 0.2f) else DividerColor.copy(alpha = 0.15f))
                                        .border(1.dp, if (isSelected) PrimaryGold else DividerColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                        .clickable { viewModel.setBackgroundTransparency(value) }
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 9.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) PrimaryGold else UnselectedWhite
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = DividerColor.copy(alpha = 0.35f), thickness = 0.8.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Switch 1: Garis Batas Berkas (Border Card)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Garis Batas Berkas", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("Tampilkan border outline pada kartu media", fontSize = 10.sp, color = UnselectedWhite)
                            }
                            Switch(
                                checked = showFileBorders,
                                onCheckedChange = { viewModel.toggleShowFileBorders() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = PrimaryGold,
                                    checkedTrackColor = PrimaryGold.copy(alpha = 0.4f),
                                    uncheckedThumbColor = UnselectedWhite,
                                    uncheckedTrackColor = DividerColor.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.scale(0.85f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Switch 2: Gaya Teks Judul (Uppercase / Normal)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Format Teks Judul", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("Ubah judul menjadi huruf besar (UPPERCASE)", fontSize = 10.sp, color = UnselectedWhite)
                            }
                            Switch(
                                checked = isTextTitleUppercase,
                                onCheckedChange = { viewModel.toggleTextTitleUppercase() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = PrimaryGold,
                                    checkedTrackColor = PrimaryGold.copy(alpha = 0.4f),
                                    uncheckedThumbColor = UnselectedWhite,
                                    uncheckedTrackColor = DividerColor.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.scale(0.85f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Switch 3: Animasi Cahaya Navigasi (Luminous Nav Animation)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Animasi Cahaya Navigasi", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("Efek gelombang cahaya pada bilah navigasi", fontSize = 10.sp, color = UnselectedWhite)
                            }
                            Switch(
                                checked = showNavLightAnim,
                                onCheckedChange = { viewModel.toggleShowNavLightAnim() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = PrimaryGold,
                                    checkedTrackColor = PrimaryGold.copy(alpha = 0.4f),
                                    uncheckedThumbColor = UnselectedWhite,
                                    uncheckedTrackColor = DividerColor.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.scale(0.85f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ================== DROPDOWN 2: EFEK AUDIO (DSP STUDIO) ==================
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DividerColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isEffectsExpanded = !isEffectsExpanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.SurroundSound, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Efek Audio (DSP)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isEffectsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = UnselectedWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Master Toggle Switch for Effects
                    Switch(
                        checked = isEffectsEnabled,
                        onCheckedChange = { viewModel.setEffectsEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF101014),
                            checkedTrackColor = AccentTeal,
                            uncheckedTrackColor = DividerColor.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }

                AnimatedVisibility(visible = isEffectsExpanded) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        if (!isEffectsEnabled) {
                            Text(
                                text = "Efek audio sedang dinonaktifkan (Bypass)",
                                fontSize = 11.sp,
                                color = UnselectedWhite,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        // 1. REVERB EFFECT
                        Text("EFEK REVERB (GEMA RUANG)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentTeal)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("Mati", "Kecil", "Sedang", "Aula", "Plate").forEach { preset ->
                                val isSelected = reverbPreset == preset
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected && isEffectsEnabled) AccentTeal.copy(alpha = 0.25f) else DividerColor.copy(alpha = 0.15f))
                                        .border(1.dp, if (isSelected && isEffectsEnabled) AccentTeal else DividerColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                        .clickable(enabled = isEffectsEnabled) { viewModel.setReverbPreset(preset) }
                                        .padding(vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = preset,
                                        fontSize = 9.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected && isEffectsEnabled) AccentTeal else TextPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 2. PITCH / NADA SUARA
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("PITCH / NADA SUARA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryGold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (pitchSemiTones > 0f) "+${String.format("%.1f", pitchSemiTones)} Nada"
                                           else if (pitchSemiTones < 0f) "${String.format("%.1f", pitchSemiTones)} Nada"
                                           else "1",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryGold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "1",
                                    fontSize = 9.sp,
                                    color = AccentTeal,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(DividerColor.copy(alpha = 0.3f))
                                        .clickable(enabled = isEffectsEnabled) { viewModel.setPitchSemiTones(0f) }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        RoundSlider(
                            value = pitchSemiTones,
                            onValueChange = { viewModel.setPitchSemiTones(it) },
                            valueRange = -6f..6f,
                            enabled = isEffectsEnabled,
                            activeColor = PrimaryGold,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 3. SUPER BASS (BASS BOOST)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SUPER BASS (BASS BOOST)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryGold)
                            Text("${(superBassStrength * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryGold)
                        }
                        RoundSlider(
                            value = superBassStrength,
                            onValueChange = { viewModel.setSuperBassStrength(it) },
                            valueRange = 0f..1f,
                            enabled = isEffectsEnabled,
                            activeColor = PrimaryGold,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 4. 3D AUDIO (VIRTUALIZER / SURROUND)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("3D AUDIO (SURROUND SPACE)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentTeal)
                            Text("${(virtualizer3DStrength * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentTeal)
                        }
                        RoundSlider(
                            value = virtualizer3DStrength,
                            onValueChange = { viewModel.setVirtualizer3DStrength(it) },
                            valueRange = 0f..1f,
                            enabled = isEffectsEnabled,
                            activeColor = AccentTeal,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 5. VOCAL CLARITY BOOST
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("KEJERNIHAN VOKAL (VOCAL CLARITY)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryGold)
                            Text("${(vocalClarity * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryGold)
                        }
                        RoundSlider(
                            value = vocalClarity,
                            onValueChange = { viewModel.setVocalClarity(it) },
                            valueRange = 0f..1f,
                            enabled = isEffectsEnabled,
                            activeColor = PrimaryGold,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 6. TREBLE AIR & SPARKLE
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TREBLE AIR & SPARKLE (DETAIL TINGGI)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentTeal)
                            Text("${(trebleSparkle * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentTeal)
                        }
                        RoundSlider(
                            value = trebleSparkle,
                            onValueChange = { viewModel.setTrebleSparkle(it) },
                            valueRange = 0f..1f,
                            enabled = isEffectsEnabled,
                            activeColor = AccentTeal,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 7. WARM ANALOG TUBE SATURATION
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("WARM TUBE SATURATION (SUARA HANGAT)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryGold)
                            Text("${(warmTubeSaturation * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryGold)
                        }
                        RoundSlider(
                            value = warmTubeSaturation,
                            onValueChange = { viewModel.setWarmTubeSaturation(it) },
                            valueRange = 0f..1f,
                            enabled = isEffectsEnabled,
                            activeColor = PrimaryGold,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 8. DYNAMIC VOLUME LEVELER
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("DYNAMIC VOLUME LEVELER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("Penyeimbang volume antar lagu", fontSize = 9.sp, color = UnselectedWhite)
                            }
                            Switch(
                                checked = dynamicVolumeLeveler,
                                onCheckedChange = { viewModel.setDynamicVolumeLeveler(it) },
                                enabled = isEffectsEnabled,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF101014),
                                    checkedTrackColor = PrimaryGold,
                                    uncheckedTrackColor = DividerColor.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.height(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 9. L / R AUDIO (STEREO PAN BALANCE)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("KESEIMBANGAN L / R AUDIO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (lrAudioBalance < -0.05f) "Kiri (${(-lrAudioBalance * 100).toInt()}%)"
                                           else if (lrAudioBalance > 0.05f) "Kanan (${(lrAudioBalance * 100).toInt()}%)"
                                           else "1",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentTeal
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "1",
                                    fontSize = 9.sp,
                                    color = PrimaryGold,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(DividerColor.copy(alpha = 0.3f))
                                        .clickable(enabled = isEffectsEnabled) { viewModel.setLrAudioBalance(0f) }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        RoundSlider(
                            value = lrAudioBalance,
                            onValueChange = { viewModel.setLrAudioBalance(it) },
                            valueRange = -1.0f..1.0f,
                            enabled = isEffectsEnabled,
                            activeColor = AccentTeal,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ================== DROPDOWN 3: EQUALISER (5-BAND GRAPHIC) ==================
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DividerColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isEqualizerExpanded = !isEqualizerExpanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Equaliser Grafis (5-Band)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isEqualizerExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = UnselectedWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Master Toggle Switch for Equalizer
                    Switch(
                        checked = isEqualizerEnabled,
                        onCheckedChange = { viewModel.setEqualizerEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF101014),
                            checkedTrackColor = PrimaryGold,
                            uncheckedTrackColor = DividerColor.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }

                AnimatedVisibility(visible = isEqualizerExpanded) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        // Presets Chips
                        Text("PRESET EQUALISER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryGold)
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val presets = listOf("Normal", "Bass Boost", "Vokal", "Rock", "Pop")
                            items(presets) { preset ->
                                val isSelected = selectedPresetName == preset
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected && isEqualizerEnabled) PrimaryGold.copy(alpha = 0.25f) else DividerColor.copy(alpha = 0.15f))
                                        .border(1.dp, if (isSelected && isEqualizerEnabled) PrimaryGold else DividerColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                        .clickable(enabled = isEqualizerEnabled) { viewModel.applyPreset(preset) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = preset,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected && isEqualizerEnabled) PrimaryGold else TextPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 5 Sliders for Bands
                        val bandLabels = listOf("60 Hz (Sub)", "230 Hz (Bass)", "910 Hz (Mid)", "4 kHz (High)", "14 kHz (Air)")
                        equalizerBands.forEachIndexed { index, gainDb ->
                            val label = bandLabels.getOrElse(index) { "Band $index" }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, fontSize = 10.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                                Text(
                                    text = if (gainDb > 0f) "+${String.format("%.1f", gainDb)} dB"
                                           else if (gainDb < 0f) "${String.format("%.1f", gainDb)} dB"
                                           else "0.0 dB",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (gainDb != 0f) PrimaryGold else UnselectedWhite
                                )
                            }
                            RoundSlider(
                                value = gainDb,
                                onValueChange = { viewModel.updateEqualizerBand(index, it) },
                                valueRange = -12f..12f,
                                enabled = isEqualizerEnabled,
                                activeColor = PrimaryGold,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ================== DROPDOWN 4: VOLUME & KONTROL SUARA ==================
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DividerColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isVolumeExpanded = !isVolumeExpanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Volume & Kontrol Suara",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${(volume * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryGold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isVolumeExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = UnselectedWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = isVolumeExpanded) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        // Volume Audio
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("VOLUME AUDIO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryGold)
                            Text("${(volume * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryGold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = { viewModel.setVolume(if (volume > 0f) 0f else 1f) }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = if (volume == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = PrimaryGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            RoundSlider(
                                value = volume,
                                onValueChange = { viewModel.setVolume(it) },
                                valueRange = 0f..1f,
                                activeColor = PrimaryGold,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Volume Video
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("VOLUME VIDEO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentTeal)
                            Text("${(videoVolume * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentTeal)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = { viewModel.setVideoVolume(if (videoVolume > 0f) 0f else 1f) }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = if (videoVolume == 0f) Icons.Default.VolumeMute else Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = AccentTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            RoundSlider(
                                value = videoVolume,
                                onValueChange = { viewModel.setVideoVolume(it) },
                                valueRange = 0f..1f,
                                activeColor = AccentTeal,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ================== DROPDOWN 5: PENGATUR WAKTU TIDUR ==================
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DividerColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isSleepTimerExpanded = !isSleepTimerExpanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bedtime, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pengatur Waktu Tidur",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (sleepTimerRemaining > 0) {
                            val mins = sleepTimerRemaining / 60
                            val secs = sleepTimerRemaining % 60
                            Text(
                                text = String.format("%02d:%02d", mins, secs),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentTeal
                            )
                        } else {
                            Text(
                                text = if (sleepTimerMinutes > 0) "${sleepTimerMinutes}m" else "Mati",
                                fontSize = 11.sp,
                                color = UnselectedWhite
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isSleepTimerExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = UnselectedWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = isSleepTimerExpanded) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val timerOptions = listOf(0 to "Mati", 15 to "15m", 30 to "30m", 45 to "45m", 60 to "60m")
                            items(timerOptions) { (min, label) ->
                                val isSelected = sleepTimerMinutes == min
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) AccentTeal 
                                            else DividerColor.copy(alpha = 0.15f)
                                        )
                                        .border(1.dp, if (isSelected) AccentTeal else DividerColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                        .clickable { viewModel.setSleepTimer(min) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color(0xFF101014) else TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // FOOTER INFO
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("NOERAE Audio Studio v2.5", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text("Total Media: ${allTracks.size} Lagu & Video", color = UnselectedWhite, fontSize = 10.sp)
                Text("Mesin: Hi-Res 32-bit DSP Engine", color = AccentTeal, fontSize = 10.sp)
            }
        }
    }
}

// --- SCREEN 1: LIBRARY (PUSTAKA) ---
@Composable
fun LibraryScreen(viewModel: MediaViewModel, onOpenDrawer: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val favoriteFolders by viewModel.favoriteFolders.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    var showHeaderMenu by remember { mutableStateOf(false) }
    val groupSelection = viewModel.selectedMediaTab
    var showFolderScannerDialog by remember { mutableStateOf(false) }
    var trackToDelete by remember { mutableStateOf<MediaTrack?>(null) }

    // Delete confirmation dialog
    if (trackToDelete != null) {
        val target = trackToDelete!!
        AlertDialog(
            onDismissRequest = { trackToDelete = null },
            containerColor = CardBackground,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .border(1.5.dp, PrimaryGold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(24.dp))
                }
            },
            title = {
                Text(
                    text = "Hapus Berkas dari Pustaka?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus \"${target.title}\" dari daftar pustaka aplikasi?",
                    color = UnselectedWhite,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTrack(target)
                        trackToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Hapus", color = Color(0xFF101014), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { trackToDelete = null }) {
                    Text("Batal", color = TextPrimary)
                }
            }
        )
    }

    val singleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importAndPlaySingleUri(context, uri)
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            viewModel.importFolderUri(context, uri)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            viewModel.autoScanMusicFolders()
        } else {
            Toast.makeText(context, "Izin penyimpanan diperlukan untuk memuat musik perpustakaan!", Toast.LENGTH_LONG).show()
        }
    }

    var isGridView by remember { mutableStateOf(true) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }

    val folderGroupedTracks = remember(tracks) {
        tracks.groupBy { track ->
            val path = track.filePath
            if (path.contains("/")) {
                val parent = path.substringBeforeLast("/")
                val folderName = parent.substringAfterLast("/")
                if (folderName.isNotBlank()) folderName else "Penyimpanan Utama"
            } else {
                "Penyimpanan Utama"
            }
        }
    }

    // Sorted folder keys with favorites pinned to the top and filtered by searchQuery
    val sortedFolderNames = remember(folderGroupedTracks, favoriteFolders, searchQuery) {
        val keys = if (searchQuery.isBlank()) {
            folderGroupedTracks.keys
        } else {
            folderGroupedTracks.keys.filter { folderName ->
                folderName.contains(searchQuery, ignoreCase = true) ||
                (folderGroupedTracks[folderName]?.any { track ->
                    track.title.contains(searchQuery, ignoreCase = true) ||
                    track.artist.contains(searchQuery, ignoreCase = true) ||
                    track.album.contains(searchQuery, ignoreCase = true) ||
                    track.genre.contains(searchQuery, ignoreCase = true)
                } == true)
            }
        }
        keys.sortedWith(
            compareByDescending<String> { favoriteFolders.contains(it) }.thenBy { it.lowercase() }
        )
    }

    // Filtered tracks with favorites pinned to the top
    val filteredTracks = remember(tracks, searchQuery, groupSelection, selectedFolder) {
        tracks.filter {
            val matchesQuery = searchQuery.isBlank() ||
                              it.title.contains(searchQuery, ignoreCase = true) ||
                              it.format.contains(searchQuery, ignoreCase = true) ||
                              it.genre.contains(searchQuery, ignoreCase = true) ||
                              it.artist.contains(searchQuery, ignoreCase = true) ||
                              it.album.contains(searchQuery, ignoreCase = true)

            val matchesGroup = when (groupSelection) {
                "Folder" -> {
                    if (selectedFolder == null) true
                    else {
                        val path = it.filePath
                        val folderName = if (path.contains("/")) path.substringBeforeLast("/").substringAfterLast("/") else "Penyimpanan Utama"
                        folderName == selectedFolder
                    }
                }
                "Audio" -> !it.isVideo
                "Video" -> it.isVideo
                else -> true
            }

            matchesQuery && matchesGroup
        }.sortedWith(
            compareByDescending<MediaTrack> { it.isFavorite }.thenByDescending { it.dateAdded }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // App Header (Clean Solid Non-Transparent Header Bar matching Nav)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = HeaderBackground,
            tonalElevation = 2.dp,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, DividerColor.copy(alpha = 0.35f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu Slider",
                            tint = PrimaryGold,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "NOERAE PLAYER",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = PrimaryGold,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (selectedFolder != null) "Folder: $selectedFolder" else "Pustaka Media • ${tracks.size} item",
                            fontSize = 11.sp,
                            color = UnselectedWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Header 3-dot dropdown popup containing Grid, Search, and Reload
                Box {
                    IconButton(
                        onClick = { showHeaderMenu = true },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opsi Pustaka",
                            tint = PrimaryGold,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showHeaderMenu,
                        onDismissRequest = { showHeaderMenu = false },
                        modifier = Modifier.background(CardBackground)
                    ) {
                        // 1. Grid/List Toggle
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isGridView) "Daftar" else "Grid",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                    contentDescription = null,
                                    tint = PrimaryGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                isGridView = !isGridView
                                showHeaderMenu = false
                            }
                        )

                        // 2. Search Toggle
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Cari",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = PrimaryGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                isSearchVisible = !isSearchVisible
                                showHeaderMenu = false
                            }
                        )

                        // 3. Reload Scan
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Pindai",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = PrimaryGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showHeaderMenu = false
                                val permissionsToRequest = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    arrayOf(
                                        android.Manifest.permission.READ_MEDIA_AUDIO,
                                        android.Manifest.permission.READ_MEDIA_VIDEO
                                    )
                                } else {
                                    arrayOf(
                                        android.Manifest.permission.READ_EXTERNAL_STORAGE
                                    )
                                }
                                
                                var allGranted = true
                                for (perm in permissionsToRequest) {
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        allGranted = false
                                        break
                                    }
                                }
                                
                                if (allGranted) {
                                    viewModel.autoScanMusicFolders()
                                } else {
                                    permissionLauncher.launch(permissionsToRequest)
                                }
                            }
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {

        // Search text area (Visible only when Search is clicked from 3-dot popup)
        AnimatedVisibility(
            visible = isSearchVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari", color = UnselectedWhite) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Hapus", tint = PrimaryGold, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBackground,
                        unfocusedContainerColor = CardBackground,
                        focusedBorderColor = PrimaryGold,
                        unfocusedBorderColor = DividerColor.copy(alpha = 0.4f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Folder scanner Dialog popup
        if (showFolderScannerDialog) {
            var customFolderPath by remember { mutableStateOf("/storage/emulated/0/Music") }
            AlertDialog(
                onDismissRequest = { showFolderScannerDialog = false },
                title = { Text("Pindai Folder Penyimpanan Internal", color = PrimaryGold, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Masukkan atau pilih alamat folder internal untuk memuat berkas audio/video secara massal ke aplikasi:",
                            color = TextPrimary,
                            fontSize = 12.sp
                        )
                        OutlinedTextField(
                            value = customFolderPath,
                            onValueChange = { customFolderPath = it },
                            label = { Text("Alamat Path Folder") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = PrimaryGold
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("PINTAS CEPAT FOLDER:", color = UnselectedWhite, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "/storage/emulated/0/Music" to "Musik",
                                "/storage/emulated/0/Download" to "Unduhan",
                                "/storage/emulated/0/Documents" to "Dokumen"
                            ).forEach { (path, label) ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CardBackground)
                                        .border(1.dp, DividerColor, RoundedCornerShape(8.dp))
                                        .clickable { customFolderPath = path }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = PrimaryGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("SISTEM PEMILIH PENGGUNA (SAF):", color = UnselectedWhite, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    singleFilePickerLauncher.launch(arrayOf("audio/*", "video/*"))
                                    showFolderScannerDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF101014))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pilih Berkas", color = Color(0xFF101014), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = {
                                    folderPickerLauncher.launch(null)
                                    showFolderScannerDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF101014))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pilih Folder", color = Color(0xFF101014), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.scanCustomFolder(customFolderPath)
                            showFolderScannerDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
                    ) {
                        Text("Pindai Sekarang", color = Color(0xFF101014), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFolderScannerDialog = false }) {
                        Text("Batal", color = TextPrimary)
                    }
                },
                containerColor = CardBackground
            )
        }

        // Filtering Chips Row: Exactly 3 chips ("Folder", "Audio", "Video")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val chips = listOf("Folder", "Audio", "Video")
            items(chips) { chip ->
                val selected = groupSelection == chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (selected) PrimaryGold 
                            else CardBackground
                        )
                        .border(
                            1.2.dp,
                            if (selected) PrimaryGold else DividerColor.copy(alpha = if (IsDarkTheme) 0.35f else 0.45f),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { 
                            viewModel.selectedMediaTab = chip
                            if (chip != "Folder") selectedFolder = null
                        }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = chip,
                        color = if (selected) Color(0xFF101014) else TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.zIndex(1f)
                    )
                }
            }
        }

        // Active folder filter indicator
        if (selectedFolder != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(PrimaryGold.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Folder: $selectedFolder", color = PrimaryGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(
                    onClick = { selectedFolder = null },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Hapus Filter Folder", tint = PrimaryGold, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Direct Clean Song List / Grid (Daftar Lagu) - Grid applies to ALL (Folder, Audio, Video)
        if (groupSelection == "Folder" && selectedFolder == null) {
            // Display folder list / grid overview
            if (folderGroupedTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Tidak ada folder ditemukan", color = UnselectedWhite, fontSize = 14.sp)
                }
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    gridItems(sortedFolderNames, key = { it }) { folderName ->
                        val count = folderGroupedTracks[folderName]?.size ?: 0
                        val isFav = favoriteFolders.contains(folderName)
                        FolderGridCard(
                            folderName = folderName,
                            count = count,
                            isFavorite = isFav,
                            onClick = { selectedFolder = folderName },
                            onToggleFavorite = { viewModel.toggleFavoriteFolder(folderName) },
                            onShare = { shareFolderPath(context, folderName) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(sortedFolderNames, key = { it }) { folderName ->
                        val count = folderGroupedTracks[folderName]?.size ?: 0
                        val isFav = favoriteFolders.contains(folderName)
                        FolderItemCard(
                            folderName = folderName,
                            count = count,
                            isFavorite = isFav,
                            onClick = { selectedFolder = folderName },
                            onToggleFavorite = { viewModel.toggleFavoriteFolder(folderName) },
                            onShare = { shareFolderPath(context, folderName) }
                        )
                    }
                }
            }
        } else if (filteredTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = DividerColor,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Daftar media tidak ditemukan", color = UnselectedWhite, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Tekan tombol pindaian di menu titik tiga untuk memuat berkas", color = UnselectedWhite.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
        } else {
            if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    gridItems(filteredTracks, key = { it.id }) { track ->
                        TrackGridCard(
                            track = track,
                            onClick = {
                                if (track.isVideo) {
                                    viewModel.playVideoTrack(track)
                                    viewModel.activeScreen = "Video"
                                } else {
                                    viewModel.playTrack(track)
                                    viewModel.activeScreen = "Player"
                                }
                            },
                            onLongClick = { trackToDelete = track },
                            onDelete = { trackToDelete = track },
                            onToggleFavorite = { viewModel.toggleFavoriteTrack(track) },
                            onShare = { shareMediaTrack(context, track) },
                            onAddToPlaylist = { viewModel.trackToAddToPlaylist = track }
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredTracks, key = { it.id }) { track ->
                        TrackItemCard(
                            track = track, 
                            onClick = { 
                                if (track.isVideo) {
                                    viewModel.playVideoTrack(track)
                                    viewModel.activeScreen = "Video"
                                } else {
                                    viewModel.playTrack(track)
                                    viewModel.activeScreen = "Player"
                                }
                            },
                            onLongClick = {
                                trackToDelete = track
                            },
                            onDelete = {
                                trackToDelete = track
                            },
                            onToggleFavorite = { viewModel.toggleFavoriteTrack(track) },
                            onShare = { shareMediaTrack(context, track) },
                            onDownload = {},
                            onAddToPlaylist = { viewModel.trackToAddToPlaylist = track }
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
fun TrackGridCard(
    track: MediaTrack,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDelete: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onShare: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (track.isFavorite) PrimaryGold.copy(alpha = 0.6f) else DividerColor.copy(alpha = if (IsDarkTheme) 0.35f else 0.45f),
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1B1613)),
                contentAlignment = Alignment.Center
            ) {
                if (track.isVideo) {
                    AnimatedVideoThumbnail(
                        filePath = track.filePath,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (!track.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = track.imageUrl,
                        contentDescription = "Cover",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = PrimaryGold,
                        modifier = Modifier.size(36.dp)
                    )
                }

                if (track.isFavorite) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .size(22.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Star, contentDescription = "Favorit", tint = PrimaryGold, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        color = UnselectedWhite,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu Opsi", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(CardBackground)
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (track.isFavorite) "Hapus Favorit" else "Tambah Favorit", color = TextPrimary, fontSize = 13.sp) },
                            leadingIcon = { Icon(if (track.isFavorite) Icons.Default.Star else Icons.Default.StarOutline, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                onToggleFavorite()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Tambah ke Playlist", color = TextPrimary, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                onAddToPlaylist()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Bagikan File", color = TextPrimary, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                onShare()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Hapus File", color = Color(0xFFEF5350), fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp)) },
                            onClick = {
                                onDelete()
                                showMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TrackItemCard(
    track: MediaTrack,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDelete: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onShare: () -> Unit = {},
    onDownload: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (track.isFavorite) PrimaryGold.copy(alpha = 0.6f) else DividerColor.copy(alpha = if (IsDarkTheme) 0.35f else 0.45f),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1B1613)),
                contentAlignment = Alignment.Center
            ) {
                if (track.isVideo) {
                    AnimatedVideoThumbnail(
                        filePath = track.filePath,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (!track.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = track.imageUrl,
                        contentDescription = "Cover",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = PrimaryGold,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.title,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.isFavorite) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Star, contentDescription = "Favorit", tint = PrimaryGold, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.artist,
                        color = UnselectedWhite,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• ${formatMs(track.duration)}",
                        color = PrimaryGold,
                        fontSize = 10.5.sp
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu Opsi", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(CardBackground)
                ) {
                    DropdownMenuItem(
                        text = { Text(if (track.isFavorite) "Hapus Favorit" else "Tambah Favorit", color = TextPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(if (track.isFavorite) Icons.Default.Star else Icons.Default.StarOutline, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            onToggleFavorite()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Tambah ke Playlist", color = TextPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            onAddToPlaylist()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Bagikan File", color = TextPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            onShare()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus File", color = Color(0xFFEF5350), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp)) },
                        onClick = {
                            onDelete()
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FolderGridCard(
    folderName: String,
    count: Int,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, if (isFavorite) PrimaryGold.copy(alpha = 0.6f) else DividerColor.copy(alpha = if (IsDarkTheme) 0.35f else 0.45f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PrimaryGold.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = PrimaryGold,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu Folder", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(CardBackground)
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (isFavorite) "Hapus Favorit" else "Favoritkan Folder", color = TextPrimary, fontSize = 13.sp) },
                            leadingIcon = { Icon(if (isFavorite) Icons.Default.Star else Icons.Default.StarOutline, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                onToggleFavorite()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Bagikan Folder", color = TextPrimary, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                onShare()
                                showMenu = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = folderName,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isFavorite) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.Star, contentDescription = "Favorit", tint = PrimaryGold, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$count file media",
                color = UnselectedWhite,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun FolderItemCard(
    folderName: String,
    count: Int,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (isFavorite) PrimaryGold.copy(alpha = 0.6f) else DividerColor.copy(alpha = if (IsDarkTheme) 0.35f else 0.45f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PrimaryGold.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(26.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(folderName, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (isFavorite) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Star, contentDescription = "Favorit", tint = PrimaryGold, modifier = Modifier.size(15.dp))
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text("$count file media", color = UnselectedWhite, fontSize = 11.sp)
            }
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu Folder", tint = TextPrimary, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(CardBackground)
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isFavorite) "Hapus Favorit" else "Favoritkan Folder", color = TextPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(if (isFavorite) Icons.Default.Star else Icons.Default.StarOutline, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            onToggleFavorite()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Bagikan Folder", color = TextPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            onShare()
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}

// --- SCREEN 2: MAIN DUAL-CONTROLLER PLAYER ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: MediaViewModel, onOpenDrawer: () -> Unit = {}) {
    LaunchedEffect(Unit) {
        viewModel.playFirstAudioTrackIfNeeded()
    }

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.playbackProgress.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val equalizerBands by viewModel.equalizerBands.collectAsStateWithLifecycle()
    val selectedPresetName by viewModel.selectedPresetName.collectAsStateWithLifecycle()
    val presets by viewModel.allPresets.collectAsStateWithLifecycle()

    val abRepeatActive by viewModel.abRepeatActive.collectAsStateWithLifecycle()
    val pointA by viewModel.pointA.collectAsStateWithLifecycle()
    val pointB by viewModel.pointB.collectAsStateWithLifecycle()
    val isShuffle by viewModel.isShuffle.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()

    val lyricsLines by viewModel.lyricsLines.collectAsStateWithLifecycle()
    val activeLyricIndex by viewModel.activeLyricIndex.collectAsStateWithLifecycle()

    val volume by viewModel.volume.collectAsStateWithLifecycle()
    val videoVolume by viewModel.videoVolume.collectAsStateWithLifecycle()
    val bgTransparency by viewModel.backgroundTransparency.collectAsStateWithLifecycle()

    val audioLabelStyle by viewModel.audioLabelStyle.collectAsStateWithLifecycle()
    val visualizerModel by viewModel.visualizerModel.collectAsStateWithLifecycle()
    val visualizerColorTheme by viewModel.visualizerColorTheme.collectAsStateWithLifecycle()

    var showEditorDrawer by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSpectrumDialog by remember { mutableStateOf(false) }
    var showAbRepeatDialog by remember { mutableStateOf(false) }
    var showMenuDropdown by remember { mutableStateOf(false) }
    var showLabelDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    var tempPickUrl by remember { mutableStateOf<String?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            tempPickUrl = it.toString()
        }
    }

    if (currentTrack == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = DividerColor, modifier = Modifier.size(80.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Tidak Ada Lagu Diputar", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Pilih lagu favoritmu di Pustaka untuk mulai mendengarkan.", color = UnselectedWhite, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
            }
        }
        return
    }

    val track = currentTrack!!
    val duration = track.duration

    // Scroll lyrics automatically
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(activeLyricIndex) {
        if (activeLyricIndex >= 0 && lyricsLines.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(activeLyricIndex, scrollOffset = -150)
            }
        }
    }

    // Dynamic High-Definition Backgrounds according to Genre or track image tagging
    val backgroundUrl = if (!track.imageUrl.isNullOrBlank()) {
        track.imageUrl!!
    } else {
        when (track.genre.lowercase()) {
            "pop" -> "https://images.unsplash.com/photo-1487180142328-0c4e37023af5?auto=format&fit=crop&q=80&w=600"
            "keroncong", "kerocong" -> "https://images.unsplash.com/photo-1511192336575-5a79af67a629?auto=format&fit=crop&q=80&w=600"
            "cinematic", "nature" -> "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?auto=format&fit=crop&q=80&w=600"
            "ambient" -> "https://images.unsplash.com/photo-1505118380757-91f5f5632de0?auto=format&fit=crop&q=80&w=600"
            else -> "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&q=80&w=600"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Subtle album artwork wallpaper
        AsyncImage(
            model = backgroundUrl,
            contentDescription = "Background Blur Album Artwork",
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            alpha = (0.22f * (1f - bgTransparency * 0.6f)).coerceAtLeast(0.04f)
        )
        // Transparent soft overlay gradient for lyrics readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = (0.45f * (1f - bgTransparency)).coerceIn(0f, 0.7f))
                        )
                    )
                )
        )

        // Main layout container (Fixed-bottom layout)
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ================== SECTION 1: HEADER & SELECTOR ==================
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Action Toolbar with Playing Title and 3-dot Menu (Matching Nav Bar)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = HeaderBackground,
                    tonalElevation = 2.dp,
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, DividerColor.copy(alpha = 0.35f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onOpenDrawer,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu Slider", tint = PrimaryGold, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = track.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (track.artist.isNotBlank() && track.artist != "<unknown>") track.artist else "Audio • ${track.format.uppercase()}",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = PrimaryGold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    viewModel.selectedMediaTab = "Audio"
                                    viewModel.activeScreen = "Library"
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(Icons.Default.QueueMusic, contentDescription = "Daftar Lagu", tint = PrimaryGold, modifier = Modifier.size(22.dp))
                            }
                            Box {
                                IconButton(
                                    onClick = { showMenuDropdown = true },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Pilihan Menu", tint = PrimaryGold, modifier = Modifier.size(22.dp))
                                }
                                DropdownMenu(
                                    expanded = showMenuDropdown,
                                    onDismissRequest = { showMenuDropdown = false },
                                    modifier = Modifier.background(CardBackground)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Tampilan Label Cover", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            showMenuDropdown = false
                                            showLabelDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp)) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Gaya & Warna Spektrum", color = TextPrimary, fontSize = 13.sp) },
                                        onClick = {
                                            showMenuDropdown = false
                                            showSpectrumDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp)) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Kecepatan Tempo (Speed)", color = TextPrimary, fontSize = 13.sp) },
                                        onClick = {
                                            showMenuDropdown = false
                                            showSpeedDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp)) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Pengulangan Segmen A-B", color = TextPrimary, fontSize = 13.sp) },
                                        onClick = {
                                            showMenuDropdown = false
                                            showAbRepeatDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp)) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Edit Tag Musik (MP3 Info)", color = TextPrimary, fontSize = 13.sp) },
                                        onClick = {
                                            showMenuDropdown = false
                                            showEditorDrawer = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp)) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Tambahkan ke Playlist", color = TextPrimary, fontSize = 13.sp) },
                                        onClick = {
                                            showMenuDropdown = false
                                            viewModel.trackToAddToPlaylist = track
                                        },
                                        leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp)) }
                                    )
                                    HorizontalDivider(color = DividerColor.copy(alpha = 0.4f), thickness = 0.8.dp)
                                    DropdownMenuItem(
                                        text = { Text("Bagikan Lagu", color = PrimaryGold, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            showMenuDropdown = false
                                            shareMediaTrack(context, track)
                                        },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ================== SECTION 2: FULL-HEIGHT SPECTACULAR SPECTRUM & OPTIONAL ARTWORK ==================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            if (dragAmount < -35) {
                                viewModel.activeScreen = "Library"
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Full Height Sound Spectrum Visualizer (from main control up to header)
                WaveVisualizer(
                    isPlaying = isPlaying, 
                    model = visualizerModel, 
                    colorTheme = visualizerColorTheme,
                    modifier = Modifier.fillMaxSize()
                )

                // Optional Album Artwork Overlay when not set to "Sembunyi"
                if (audioLabelStyle != "Sembunyi") {
                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .shadow(16.dp, RoundedCornerShape(20.dp), ambientColor = PrimaryGold.copy(alpha = 0.35f), spotColor = PrimaryGold.copy(alpha = 0.3f))
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                when (audioLabelStyle) {
                                    "Solid", "Putih", "Solid Minimal" -> Color.White.copy(alpha = 0.95f)
                                    "Hitam" -> Color.Black.copy(alpha = 0.85f)
                                    else -> DividerColor.copy(alpha = 0.25f)
                                }
                            )
                            .border(1.dp, PrimaryGold.copy(alpha = 0.45f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!track.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = track.imageUrl,
                                contentDescription = "Cover Album",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else if (track.isVideo) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = "Video Cover",
                                tint = PrimaryGold,
                                modifier = Modifier.size(60.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "Music Cover",
                                tint = PrimaryGold,
                                modifier = Modifier.size(60.dp)
                            )
                        }
                    }
                }
            }

            // ================== SECTION 3: FIXED BOTTOM CONTROLLER CONSOLE ==================
            Surface(
                shape = RectangleShape,
                color = CardBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, DividerColor.copy(alpha = if (IsDarkTheme) 0.35f else 0.45f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val currentDur = duration.coerceAtLeast(1L)
                    val currentProg = playbackProgress.coerceIn(0L, currentDur)

                    // Detik waktu dan total waktu sejajar kanan dan kiri Progress Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatMs(currentProg),
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(42.dp),
                            textAlign = TextAlign.Center
                        )
                        Slider(
                            value = currentProg.toFloat(),
                            onValueChange = { viewModel.seekTo(it.toLong()) },
                            valueRange = 0f..currentDur.toFloat(),
                            colors = SliderDefaults.colors(
                                activeTrackColor = PrimaryGold,
                                inactiveTrackColor = DividerColor.copy(alpha = 0.5f),
                                thumbColor = PrimaryGold
                            ),
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(PrimaryGold, CircleShape)
                                        .border(1.5.dp, if (IsDarkTheme) Color.White else Color(0xFF101014), CircleShape)
                                )
                            },
                            track = { sliderState ->
                                SliderDefaults.Track(
                                    sliderState = sliderState,
                                    modifier = Modifier.height(3.dp),
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = PrimaryGold,
                                        inactiveTrackColor = DividerColor.copy(alpha = 0.5f)
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatMs(currentDur),
                            color = UnselectedWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(42.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    // Action media buttons row (Shuffle, Prev, Play, Next, Repeat)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.setShuffle(!isShuffle) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Acak",
                                tint = if (isShuffle) PrimaryGold else TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.playPreviousTrack() },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Sebelumnya", tint = TextPrimary, modifier = Modifier.size(24.dp))
                        }

                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(PrimaryGold)
                                .clickable { viewModel.togglePlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Mainkan/Jeda",
                                tint = Color(0xFF101014),
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.playNextTrack() },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Selanjutnya", tint = TextPrimary, modifier = Modifier.size(24.dp))
                        }

                        IconButton(
                            onClick = { viewModel.toggleRepeatMode() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            if (repeatMode == 1) {
                                Icon(
                                    imageVector = Icons.Default.RepeatOne,
                                    contentDescription = "Looping Satu Lagu",
                                    tint = PrimaryGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = "Looping Semua Lagu",
                                    tint = if (repeatMode == 2) PrimaryGold else TextPrimary.copy(alpha = 0.65f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ================== POPUP SUB-DIALOGS FOR HIDDEN OPTIONS MENU ==================
        // AB Repeat Dialog
        if (showAbRepeatDialog) {
            AlertDialog(
                onDismissRequest = { showAbRepeatDialog = false },
                title = { Text("PENGULANGAN SEGMEN A-B", color = PrimaryGold, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Latih bagian/bait lagu tertentu secara terus menerus dengan mengunci titik mulai (A) dan titik selesai (B).",
                            color = UnselectedWhite,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("TITIK A (Mulai)", color = UnselectedWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = DividerColor.copy(alpha = 0.5f)),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                        Text(
                                            text = pointA?.let { formatMs(it) } ?: "Belum Atur",
                                            color = if (pointA != null) AccentTeal else UnselectedWhite,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("TITIK B (Selesai)", color = UnselectedWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = DividerColor.copy(alpha = 0.5f)),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                        Text(
                                            text = pointB?.let { formatMs(it) } ?: "Belum Atur",
                                            color = if (pointB != null) PrimaryGold else UnselectedWhite,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }

                        if (abRepeatActive) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AccentTeal.copy(alpha = 0.2f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("STATUS: PENGULANGAN SEGMEN AKTIF", color = AccentTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.setPointA() },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Set Titik A", color = Color(0xFF101014), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            Button(
                                onClick = { viewModel.setPointB() },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                                enabled = pointA != null,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Set Titik B", color = Color(0xFF101014), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }

                        Button(
                            onClick = { viewModel.clearAbRepeat() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Reset Loop A-B", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAbRepeatDialog = false }) {
                        Text("Selesai", color = PrimaryGold, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = CardBackground,
                iconContentColor = PrimaryGold
            )
        }

        // Speed dialog (Ultra simple: no title, no close button, 1 row preset grid, small progress bar with round slider thumb below)
        if (showSpeedDialog) {
            AlertDialog(
                onDismissRequest = { showSpeedDialog = false },
                confirmButton = {},
                dismissButton = {},
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            speeds.forEach { spd ->
                                val isSelected = kotlin.math.abs(playbackSpeed - spd) < 0.05f
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) PrimaryGold else DividerColor.copy(alpha = 0.35f))
                                        .clickable { viewModel.setTempo(spd) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${spd}x",
                                        color = if (isSelected) Color(0xFF101014) else TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Slider(
                            value = playbackSpeed,
                            onValueChange = { viewModel.setTempo(it) },
                            valueRange = 0.5f..2.0f,
                            colors = SliderDefaults.colors(
                                activeTrackColor = PrimaryGold,
                                inactiveTrackColor = DividerColor.copy(alpha = 0.5f),
                                thumbColor = PrimaryGold
                            ),
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .background(PrimaryGold, CircleShape)
                                        .border(1.5.dp, if (IsDarkTheme) Color.White else Color(0xFF101014), CircleShape)
                                )
                            },
                            track = { sliderState ->
                                SliderDefaults.Track(
                                    sliderState = sliderState,
                                    modifier = Modifier.height(3.dp),
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = PrimaryGold,
                                        inactiveTrackColor = DividerColor.copy(alpha = 0.5f)
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                containerColor = CardBackground
            )
        }

        // Dialog Label Cover Album (Transparan, Solid, Sembunyi)
        if (showLabelDialog) {
            AlertDialog(
                onDismissRequest = { showLabelDialog = false },
                title = { Text("Tampilan Label Cover Album", color = PrimaryGold, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val labelOptions = listOf(
                            Triple("Transparan", "Transparan (Default)", "Cover album dengan bingkai transparan artistik"),
                            Triple("Solid", "Solid (Putih Bersih)", "Cover album dengan latar belakang solid putih kontras"),
                            Triple("Sembunyi", "Sembunyi (Hanya Spektrum Penuh)", "Sembunyikan cover untuk tampilan spektrum audio 100% penuh")
                        )
                        labelOptions.forEach { (key, title, subtitle) ->
                            val isSelected = audioLabelStyle == key || (key == "Transparan" && audioLabelStyle.isBlank())
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isSelected) PrimaryGold.copy(alpha = 0.18f) else DividerColor.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, if (isSelected) PrimaryGold else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.setAudioLabelStyle(key)
                                        showLabelDialog = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.setAudioLabelStyle(key)
                                            showLabelDialog = false
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = PrimaryGold, unselectedColor = UnselectedWhite)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(title, fontWeight = FontWeight.Bold, color = if (isSelected) PrimaryGold else TextPrimary, fontSize = 13.sp)
                                        Text(subtitle, color = UnselectedWhite, fontSize = 10.5.sp)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLabelDialog = false }) {
                        Text("Tutup", color = PrimaryGold, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = CardBackground
            )
        }

        // Spectrum / Visualizer Options Dialog (Featuring Random + Multiple Visualizer Models)
        if (showSpectrumDialog) {
            AlertDialog(
                onDismissRequest = { showSpectrumDialog = false },
                title = { Text("Gaya & Warna Spektrum", color = PrimaryGold, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column {
                            Text("GAYA VISUALISASI SPEKTRUM:", color = UnselectedWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            val spectrumOptions = listOf(
                                "Wave" to "Gelombang Sinus (Wave)",
                                "Random" to "Acak Dinamis (Random Spectrum)",
                                "Bars" to "Batang Spektrum (Bars)",
                                "Mirror" to "Spektrum Cermin (Mirror)",
                                "Circular" to "Lingkaran Ring Radial (Circular)",
                                "Particles" to "Partikel Suara (Particles)",
                                "Dots" to "Titik Frekuensi (Dots)",
                                "NeonPulse" to "Pita Neon Cyber (Neon Pulse)",
                                "LaserBeam" to "Laser Beam Spectrum",
                                "FireFlame" to "Api Menyala (Fire Flame)",
                                "CyberWave" to "Cyberpunk Matrix Wave"
                            )
                            spectrumOptions.forEach { (id, label) ->
                                val isSelected = visualizerModel == id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) PrimaryGold.copy(alpha = 0.15f) else Color.Transparent)
                                        .clickable { viewModel.setVisualizerModel(id) }
                                        .padding(vertical = 8.dp, horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.setVisualizerModel(id) },
                                        colors = RadioButtonDefaults.colors(selectedColor = PrimaryGold, unselectedColor = UnselectedWhite)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(label, color = if (isSelected) PrimaryGold else TextPrimary, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }

                        Column {
                            Text("TEMA WARNA SPEKTRUM:", color = UnselectedWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            listOf(
                                "Gold" to "Emas Premium (Gold)",
                                "Teal" to "Toska Neon (Teal)",
                                "Aurora" to "Sinar Senja (Aurora)",
                                "Emerald" to "Hijau Zamrud (Emerald)",
                                "Cyber" to "Cyber Magenta (Cyber)",
                                "Fire" to "Lava Oranye (Fire)"
                            ).forEach { (id, label) ->
                                val isSelected = visualizerColorTheme == id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) PrimaryGold.copy(alpha = 0.15f) else Color.Transparent)
                                        .clickable { viewModel.setVisualizerColorTheme(id) }
                                        .padding(vertical = 8.dp, horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.setVisualizerColorTheme(id) },
                                        colors = RadioButtonDefaults.colors(selectedColor = PrimaryGold, unselectedColor = UnselectedWhite)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(label, color = if (isSelected) PrimaryGold else TextPrimary, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSpectrumDialog = false }) {
                        Text("Selesai", color = PrimaryGold, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = CardBackground,
                iconContentColor = PrimaryGold
            )
        }
    }

    // Modal drawer or bottom sheet for metadata tag editing
    if (showEditorDrawer) {
        AlertDialog(
            onDismissRequest = { showEditorDrawer = false },
            title = { Text("Edit Tag Musik NOERAE", color = PrimaryGold, fontWeight = FontWeight.Bold) },
            text = {
                var newTitle by remember { mutableStateOf(track.title) }
                var newArtist by remember { mutableStateOf(track.artist) }
                var newAlbum by remember { mutableStateOf(track.album) }
                var newGenre by remember { mutableStateOf(track.genre) }
                var newLyrics by remember { mutableStateOf(track.lyricsLrc ?: "") }
                var newImageUrl by remember { mutableStateOf(track.imageUrl ?: "") }

                LaunchedEffect(tempPickUrl) {
                    tempPickUrl?.let {
                        newImageUrl = it
                    }
                }

                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Judul Lagu") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                    OutlinedTextField(
                        value = newArtist,
                        onValueChange = { newArtist = it },
                        label = { Text("Penyanyi") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                    OutlinedTextField(
                        value = newAlbum,
                        onValueChange = { newAlbum = it },
                        label = { Text("Sampul Album") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                    OutlinedTextField(
                        value = newGenre,
                        onValueChange = { newGenre = it },
                        label = { Text("Genre") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = newImageUrl,
                            onValueChange = { newImageUrl = it },
                            label = { Text("URL Foto Musik (Background)") },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF101014), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pilih Gambar Dari Galeri HP", color = Color(0xFF101014), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                    OutlinedTextField(
                        value = newLyrics,
                        onValueChange = { newLyrics = it },
                        label = { Text("Lirik Sychronized (LRC)") },
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )

                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { 
                            showEditorDrawer = false 
                            tempPickUrl = null
                        }) {
                            Text("Batal", color = TextPrimary)
                        }
                        Button(
                            onClick = {
                                viewModel.editTrackMetadata(track, newTitle, newArtist, newAlbum, newGenre, newLyrics, newImageUrl.takeIf { it.isNotBlank() }, context = context)
                                showEditorDrawer = false
                                tempPickUrl = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
                        ) {
                            Text("Simpan", color = Color(0xFF101014), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = CardBackground
        )
    }
}

// --- SCREEN: SEARCH (PENCARIAN) ---
@Composable
fun SearchScreen(viewModel: MediaViewModel, onOpenDrawer: () -> Unit = {}) {
    val tracks by viewModel.allTracks.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Semua") } // "Semua", "Audio", "Video", "Lossless"

    val filteredList = remember(tracks, query, selectedFilter) {
        tracks.filter { track ->
            val matches = query.isBlank() || 
                track.title.contains(query, ignoreCase = true) || 
                track.format.contains(query, ignoreCase = true) ||
                track.genre.contains(query, ignoreCase = true) ||
                track.artist.contains(query, ignoreCase = true) ||
                track.album.contains(query, ignoreCase = true)

            when (selectedFilter) {
                "Audio" -> matches && !track.isVideo
                "Video" -> matches && track.isVideo
                "Lossless" -> matches && (track.format == "FLAC" || track.format == "WAV")
                else -> matches
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Search Header (Solid Non-Transparent Header Bar matching Nav)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = HeaderBackground,
            tonalElevation = 2.dp,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, DividerColor.copy(alpha = 0.35f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu Slider",
                        tint = PrimaryGold,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Pencarian Media",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Cari lagu, video, atau format",
                        fontSize = 11.sp,
                        color = UnselectedWhite
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Prominent Search Bar
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Ketik judul lagu, video, atau format...", color = UnselectedWhite, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryGold) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Hapus", tint = UnselectedWhite)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGold,
                    unfocusedBorderColor = DividerColor.copy(alpha = 0.45f),
                    focusedContainerColor = CardBackground,
                    unfocusedContainerColor = CardBackground,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("search_field_main")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val filters = listOf("Semua", "Audio", "Video", "Lossless")
                items(filters) { filter ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) PrimaryGold 
                                else CardBackground
                            )
                            .border(
                                1.2.dp,
                                if (isSelected) PrimaryGold else DividerColor.copy(alpha = if (IsDarkTheme) 0.35f else 0.45f),
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter,
                            color = if (isSelected) Color(0xFF101014) else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.zIndex(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Results info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (query.isBlank()) "Semua Media (${filteredList.size})" else "Hasil Pencarian (${filteredList.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = UnselectedWhite
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = DividerColor,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (query.isBlank()) "Tidak ada media ditemukan" else "Tidak ada hasil untuk \"$query\"",
                            color = UnselectedWhite,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredList) { track ->
                        TrackItemCard(
                            track = track,
                            onClick = {
                                if (track.isVideo) {
                                    viewModel.playVideoTrack(track)
                                    viewModel.activeScreen = "Video"
                                } else {
                                    viewModel.playTrack(track)
                                    viewModel.activeScreen = "Player"
                                }
                            },
                            onDownload = {},
                            onAddToPlaylist = { viewModel.trackToAddToPlaylist = track }
                        )
                    }
                }
            }
        }
    }
}

// --- BULLETPROOF VIDEO PLAYER VIEW ---
@Composable
fun VideoPlayerView(
    filePath: String,
    isPlaying: Boolean,
    volume: Float,
    isLooping: Boolean,
    scaleMode: String = "FIT",
    onPrepared: (MediaPlayer) -> Unit,
    onCompletion: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlayerPrepared by remember { mutableStateOf(false) }
    var surfaceTextureState by remember { mutableStateOf<SurfaceTexture?>(null) }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }
    var hasError by remember { mutableStateOf(false) }

    fun updateTextureTransform() {
        val tv = textureViewRef ?: return
        val vw = tv.width
        val vh = tv.height
        if (vw <= 0 || vh <= 0) return
        val vidW = if (videoWidth > 0) videoWidth else vw
        val vidH = if (videoHeight > 0) videoHeight else vh

        val matrix = android.graphics.Matrix()
        val sx: Float
        val sy: Float

        when (scaleMode.uppercase()) {
            "FILL", "ZOOM" -> {
                val scale = maxOf(vw.toFloat() / vidW, vh.toFloat() / vidH)
                sx = (scale * vidW) / vw
                sy = (scale * vidH) / vh
            }
            "STRETCH" -> {
                sx = 1f
                sy = 1f
            }
            "16:9" -> {
                val targetRatio = 16f / 9f
                val currentRatio = vw.toFloat() / vh
                if (currentRatio > targetRatio) {
                    sx = (vh * targetRatio) / vw
                    sy = 1f
                } else {
                    sx = 1f
                    sy = (vw / targetRatio) / vh
                }
            }
            "4:3" -> {
                val targetRatio = 4f / 3f
                val currentRatio = vw.toFloat() / vh
                if (currentRatio > targetRatio) {
                    sx = (vh * targetRatio) / vw
                    sy = 1f
                } else {
                    sx = 1f
                    sy = (vw / targetRatio) / vh
                }
            }
            else -> { // "FIT" - Fit video to maintain exact aspect ratio within screen dimensions
                val scale = minOf(vw.toFloat() / vidW, vh.toFloat() / vidH)
                sx = (scale * vidW) / vw
                sy = (scale * vidH) / vh
            }
        }

        matrix.setScale(sx, sy, vw / 2f, vh / 2f)
        tv.setTransform(matrix)
    }

    fun setupMediaPlayer(st: SurfaceTexture) {
        try {
            isPlayerPrepared = false
            mediaPlayer?.let { oldMp ->
                try {
                    oldMp.setOnErrorListener(null)
                    oldMp.setOnPreparedListener(null)
                    oldMp.setOnCompletionListener(null)
                    oldMp.setOnVideoSizeChangedListener(null)
                    if (oldMp.isPlaying) oldMp.stop()
                } catch (e: Exception) {}
                try {
                    oldMp.release()
                } catch (e: Exception) {}
            }
            hasError = false
            val mp = MediaPlayer()
            mp.setOnErrorListener { _, what, extra ->
                android.util.Log.e("VideoPlayer", "Safe error caught: what=$what, extra=$extra")
                if (what != -38) {
                    hasError = true
                }
                true
            }
            mp.setOnVideoSizeChangedListener { _, w, h ->
                if (w > 0 && h > 0) {
                    videoWidth = w
                    videoHeight = h
                    updateTextureTransform()
                }
            }
            mp.setOnPreparedListener { preparedMp ->
                try {
                    isPlayerPrepared = true
                    hasError = false
                    preparedMp.isLooping = isLooping
                    preparedMp.setVolume(volume, volume)
                    if (preparedMp.videoWidth > 0 && preparedMp.videoHeight > 0) {
                        videoWidth = preparedMp.videoWidth
                        videoHeight = preparedMp.videoHeight
                        updateTextureTransform()
                    }
                    if (isPlaying) {
                        preparedMp.start()
                    }
                    onPrepared(preparedMp)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            mp.setOnCompletionListener {
                try {
                    onCompletion()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            val s = Surface(st)
            mp.setSurface(s)
            if (filePath.startsWith("content://") || filePath.startsWith("file://") || filePath.startsWith("http")) {
                mp.setDataSource(context, Uri.parse(filePath))
            } else {
                val f = java.io.File(filePath)
                if (f.exists()) {
                    mp.setDataSource(filePath)
                } else {
                    mp.setDataSource(context, Uri.parse(filePath))
                }
            }
            mp.prepareAsync()
            mediaPlayer = mp
        } catch (e: Exception) {
            android.util.Log.e("VideoPlayer", "setupMediaPlayer failed: ${e.message}")
            hasError = true
        }
    }

    LaunchedEffect(filePath, surfaceTextureState) {
        surfaceTextureState?.let { st ->
            setupMediaPlayer(st)
        }
    }

    LaunchedEffect(scaleMode, videoWidth, videoHeight) {
        updateTextureTransform()
    }

    DisposableEffect(Unit) {
        onDispose {
            isPlayerPrepared = false
            try {
                mediaPlayer?.let { mp ->
                    mp.setOnErrorListener(null)
                    mp.setOnPreparedListener(null)
                    mp.setOnCompletionListener(null)
                    mp.setOnVideoSizeChangedListener(null)
                    try {
                        if (mp.isPlaying) mp.stop()
                    } catch (e: Exception) {}
                    mp.release()
                }
            } catch (e: Exception) {}
            mediaPlayer = null
        }
    }

    LaunchedEffect(isPlaying, isPlayerPrepared) {
        if (isPlayerPrepared) {
            mediaPlayer?.let { mp ->
                try {
                    if (isPlaying && !mp.isPlaying) {
                        mp.start()
                    } else if (!isPlaying && mp.isPlaying) {
                        mp.pause()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    LaunchedEffect(volume, isPlayerPrepared) {
        if (isPlayerPrepared) {
            mediaPlayer?.let { mp ->
                try {
                    mp.setVolume(volume, volume)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    LaunchedEffect(isLooping, isPlayerPrepared) {
        if (isPlayerPrepared) {
            mediaPlayer?.let { mp ->
                try {
                    mp.isLooping = isLooping
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    textureViewRef = this
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                            surfaceTextureState = surface
                            updateTextureTransform()
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                            updateTextureTransform()
                        }

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            surfaceTextureState = null
                            try {
                                mediaPlayer?.setSurface(null)
                            } catch (e: Exception) {}
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                    }
                    if (isAvailable && surfaceTexture != null) {
                        surfaceTextureState = surfaceTexture
                        updateTextureTransform()
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (hasError) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(24.dp)
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Video tidak dapat diputar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Format berkas belum didukung atau berkas dipindahkan", color = UnselectedWhite, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

// --- SCREEN 3: VIDEO THEATER PLAYER ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen(viewModel: MediaViewModel, onOpenDrawer: () -> Unit = {}) {
    val tracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val currentVideoTrack by viewModel.currentVideoTrack.collectAsStateWithLifecycle()
    val isVideoPlaying by viewModel.isVideoPlaying.collectAsStateWithLifecycle()
    val videoVolume by viewModel.videoVolume.collectAsStateWithLifecycle()
    val videoRepeatMode by viewModel.videoRepeatMode.collectAsStateWithLifecycle()
    val isVideoShuffle by viewModel.isVideoShuffle.collectAsStateWithLifecycle()
    val videoPlaybackSpeed by viewModel.videoPlaybackSpeed.collectAsStateWithLifecycle()
    val videoProgress by viewModel.videoProgress.collectAsStateWithLifecycle()
    val videoDuration by viewModel.videoDuration.collectAsStateWithLifecycle()
    val videoAbRepeatActive by viewModel.videoAbRepeatActive.collectAsStateWithLifecycle()
    val videoPointA by viewModel.videoPointA.collectAsStateWithLifecycle()
    val videoPointB by viewModel.videoPointB.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var areControlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableStateOf(0) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showVideoAbRepeatDialog by remember { mutableStateOf(false) }

    var isLockWarningVisible by remember { mutableStateOf(true) }
    var lockInteractionTick by remember { mutableStateOf(0) }

    val videoTracks = remember(tracks) { tracks.filter { it.isVideo } }
    val videoScaleMode by viewModel.videoScaleMode.collectAsStateWithLifecycle()

    val videoSingleFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importAndPlaySingleUri(context, uri) }
    }

    val videoFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            viewModel.importFolderUri(context, uri)
        }
    }

    // Auto-hide controls after 5 seconds of inactivity while playing
    LaunchedEffect(areControlsVisible, interactionTick, isVideoPlaying) {
        if (areControlsVisible && isVideoPlaying) {
            delay(5000)
            areControlsVisible = false
        }
    }

    // Lock screen auto-hide warning after 2 seconds
    LaunchedEffect(viewModel.isVideoLocked) {
        if (viewModel.isVideoLocked) {
            isLockWarningVisible = true
            lockInteractionTick++
        }
    }

    LaunchedEffect(isLockWarningVisible, lockInteractionTick, viewModel.isVideoLocked) {
        if (viewModel.isVideoLocked && isLockWarningVisible) {
            delay(2000)
            isLockWarningVisible = false
        }
    }

    // Clean up fullscreen, keep screen on, and orientation when leaving screen
    DisposableEffect(Unit) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            viewModel.isVideoFullscreen = false
            viewModel.isVideoAutoRotate = false
            activity?.let { act ->
                val window = act.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    // Speed Selection Dialog (Ultra simple: no title, no close button, 1 row preset grid, small progress bar with round slider thumb below)
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            confirmButton = {},
            dismissButton = {},
            containerColor = CardBackground,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val presets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        presets.forEach { speed ->
                            val isSelected = (videoPlaybackSpeed - speed).let { kotlin.math.abs(it) < 0.04f }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) PrimaryGold else DividerColor.copy(alpha = 0.35f))
                                    .clickable {
                                        viewModel.setVideoPlaybackSpeed(speed)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${speed}x",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color(0xFF101014) else TextPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Slider(
                        value = videoPlaybackSpeed,
                        onValueChange = { viewModel.setVideoPlaybackSpeed(it) },
                        valueRange = 0.1f..3.0f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = PrimaryGold,
                            inactiveTrackColor = DividerColor.copy(alpha = 0.5f),
                            thumbColor = PrimaryGold
                        ),
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(PrimaryGold, CircleShape)
                                    .border(1.5.dp, if (IsDarkTheme) Color.White else Color(0xFF101014), CircleShape)
                            )
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                modifier = Modifier.height(3.dp),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = PrimaryGold,
                                    inactiveTrackColor = DividerColor.copy(alpha = 0.5f)
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

    // Video A-B Repeat Dialog
    if (showVideoAbRepeatDialog) {
        AlertDialog(
            onDismissRequest = { showVideoAbRepeatDialog = false },
            title = { Text("PENGULANGAN SEGMEN A-B (VIDEO)", color = PrimaryGold, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Ulangi bagian video terus menerus antara titik A dan titik B.",
                        color = UnselectedWhite,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("TITIK A", color = UnselectedWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DividerColor.copy(alpha = 0.5f)),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text(
                                        text = videoPointA?.let { formatMs(it) } ?: "Belum Atur",
                                        color = if (videoPointA != null) AccentTeal else UnselectedWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("TITIK B", color = UnselectedWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DividerColor.copy(alpha = 0.5f)),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text(
                                        text = videoPointB?.let { formatMs(it) } ?: "Belum Atur",
                                        color = if (videoPointB != null) PrimaryGold else UnselectedWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }

                    if (videoAbRepeatActive) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AccentTeal.copy(alpha = 0.2f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("STATUS: PENGULANGAN SEGMEN AKTIF", color = AccentTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.setVideoPointA() },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Set Titik A", color = Color(0xFF101014), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Button(
                            onClick = { viewModel.setVideoPointB() },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                            enabled = videoPointA != null,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Set Titik B", color = Color(0xFF101014), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }

                    Button(
                        onClick = { viewModel.clearVideoAbRepeat() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Reset Loop A-B", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVideoAbRepeatDialog = false }) {
                    Text("Selesai", color = PrimaryGold, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = CardBackground,
            iconContentColor = PrimaryGold
        )
    }

    if (currentVideoTrack == null) {
        val firstVideo = videoTracks.firstOrNull()
        if (firstVideo == null) {
            Box(modifier = Modifier.fillMaxSize()) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu Slider",
                        tint = PrimaryGold,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                ) {
                    Icon(Icons.Default.MovieFilter, contentDescription = null, tint = DividerColor, modifier = Modifier.size(80.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Pustaka Video Kosong", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Pilih video atau folder video menggunakan tombol SAF di bawah:", color = UnselectedWhite, fontSize = 12.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { videoSingleFilePicker.launch(arrayOf("video/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
                        ) {
                            Text("Buka Berkas", color = Color(0xFF101014), fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { videoFolderPicker.launch(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
                        ) {
                            Text("Pilih Folder", color = Color(0xFF101014), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            return
        } else {
            // Auto select a video
            SideEffect { viewModel.playVideoTrack(firstVideo) }
            return
        }
    }

    val track = currentVideoTrack!!

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (viewModel.isVideoFullscreen) Color.Black else Color.Transparent)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount < -35 && !viewModel.isVideoLocked) {
                        viewModel.selectedMediaTab = "Video"
                        viewModel.activeScreen = "Library"
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                areControlsVisible = !areControlsVisible
                interactionTick++
            }
    ) {
        // Safe, crash-proof Video Player using VideoPlayerView (Full Screen Canvas)
        VideoPlayerView(
            filePath = track.filePath,
            isPlaying = isVideoPlaying,
            volume = videoVolume,
            isLooping = videoRepeatMode,
            scaleMode = videoScaleMode,
            onPrepared = { mp -> viewModel.setVideoMediaPlayer(mp) },
            onCompletion = { viewModel.playNextVideo() },
            modifier = Modifier.fillMaxSize()
        )

        // Screen Lock overlays with double-tap detection & 2-second auto-hide warning
        if (viewModel.isVideoLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                isLockWarningVisible = true
                                lockInteractionTick++
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = isLockWarningVisible,
                    enter = fadeIn(animationSpec = tween(200)),
                    exit = fadeOut(animationSpec = tween(250))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Transparent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    isLockWarningVisible = true
                                    lockInteractionTick++
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { viewModel.isVideoLocked = false },
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(PrimaryGold)
                                .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Buka Kunci Layar", tint = Color(0xFF101014), modifier = Modifier.size(34.dp))
                        }
                    }
                }
            }
        } else {
            // Animated Visibility for Controls (Top Header & Bottom Bar)
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(250)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top Bar: Row 1 (File Name Bar), Row 2 (Quick Actions Bar: A-B, Acak, Rotasi, Rasio, Kecepatan, Kunci)
                    // Zero padding/margins so it's always flush and visible at the top (only hidden in fullscreen)
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // ROW 1: Bar Nama File & Menu (Flush edge-to-edge top header, title aligned with menu icon)
                        Surface(
                            color = HeaderBackground,
                            tonalElevation = 2.dp,
                            shadowElevation = 1.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(0.5.dp, DividerColor.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { interactionTick++ },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            interactionTick++
                                            onOpenDrawer()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Menu, contentDescription = "Menu Slider", tint = PrimaryGold, modifier = Modifier.size(22.dp))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = track.title,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (track.artist.isNotBlank() && track.artist != "<unknown>") track.artist else "Video • ${track.format}",
                                            color = PrimaryGold,
                                            fontSize = 10.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Right Action Buttons: List & Folder
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            interactionTick++
                                            viewModel.selectedMediaTab = "Video"
                                            viewModel.activeScreen = "Library"
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.QueueMusic, contentDescription = "Daftar Video", tint = TextPrimary, modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            interactionTick++
                                            videoFolderPicker.launch(null)
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = "Pindai Folder Video", tint = PrimaryGold, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }

                        // ROW 2: Bar Aksi Cepat (A-B, Acak, Rotasi, Rasio, Kecepatan, Kunci - Flush bar)
                        Surface(
                            color = CardBackground,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(0.5.dp, DividerColor.copy(alpha = if (IsDarkTheme) 0.35f else 0.45f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { interactionTick++ },
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Tombol A-B Repeat (Ganti Progress Bar Volume)
                                IconButton(
                                    onClick = {
                                        interactionTick++
                                        showVideoAbRepeatDialog = true
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (videoAbRepeatActive) PrimaryGold else DividerColor.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "A-B",
                                            color = if (videoAbRepeatActive) Color(0xFF101014) else TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }

                                // 2. Icon Acak (Shuffle Video)
                                IconButton(
                                    onClick = {
                                        interactionTick++
                                        viewModel.toggleVideoShuffle()
                                        val msg = if (!isVideoShuffle) "Mode Acak Video Diaktifkan" else "Mode Acak Dinonaktifkan"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = "Acak Video",
                                        tint = if (isVideoShuffle) PrimaryGold else TextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // 3. Icon Rotasi Otomatis (Sensor / User)
                                IconButton(
                                    onClick = {
                                        interactionTick++
                                        val newAutoRotate = !viewModel.isVideoAutoRotate
                                        viewModel.isVideoAutoRotate = newAutoRotate
                                        activity?.let { act ->
                                            if (newAutoRotate) {
                                                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                                                Toast.makeText(context, "Rotasi Otomatis (Sensor) Diaktifkan", Toast.LENGTH_SHORT).show()
                                            } else {
                                                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
                                                Toast.makeText(context, "Rotasi Otomatis Dinonaktifkan", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (viewModel.isVideoAutoRotate) Icons.Default.ScreenRotation else Icons.Default.ScreenLockRotation,
                                        contentDescription = "Rotasi Otomatis",
                                        tint = if (viewModel.isVideoAutoRotate) PrimaryGold else TextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // 4. Icon Fit/Rasio Video (Fit Ukuran Layar, Penuh Zoom, Rentang, 16:9)
                                IconButton(
                                    onClick = {
                                        interactionTick++
                                        val newMode = viewModel.cycleVideoScaleMode()
                                        val label = when (newMode) {
                                            "FIT" -> "Rasio: Fit Layar (Sesuai Ukuran Video)"
                                            "FILL" -> "Rasio: Penuh Layar / Zoom (FILL)"
                                            "STRETCH" -> "Rasio: Rentang Layar Penuh (STRETCH)"
                                            "16:9" -> "Rasio: Format Bioskop (16:9)"
                                            else -> "Rasio: $newMode"
                                        }
                                        Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = when (videoScaleMode) {
                                            "FIT" -> Icons.Default.FitScreen
                                            "FILL" -> Icons.Default.AspectRatio
                                            "16:9" -> Icons.Default.Tv
                                            else -> Icons.Default.FitScreen
                                        },
                                        contentDescription = "Fit Rasio Video",
                                        tint = if (videoScaleMode != "FIT") PrimaryGold else TextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // 5. Icon Kecepatan Putar (Seragam tanpa border)
                                IconButton(
                                    onClick = {
                                        interactionTick++
                                        showSpeedDialog = true
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = "Kecepatan Video",
                                        tint = if (videoPlaybackSpeed != 1.0f) PrimaryGold else TextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // 6. Icon Kunci Layar (Seragam tanpa border)
                                IconButton(
                                    onClick = {
                                        interactionTick++
                                        viewModel.isVideoLocked = true
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = "Kunci Layar",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // COMPACT BOTTOM PLAYER BAR & CONTROLS (Flush edge-to-edge, reduced height, time aligned left & right of slider)
                    Surface(
                        shape = RectangleShape,
                        color = CardBackground,
                        shadowElevation = 4.dp,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .border(0.5.dp, DividerColor.copy(alpha = if (IsDarkTheme) 0.35f else 0.45f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { interactionTick++ },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val currentDur = if (videoDuration > 0) videoDuration else track.duration.coerceAtLeast(1L)
                            val currentProg = videoProgress.coerceIn(0L, currentDur)

                            // Thinner Progress Seekbar Slider with Left & Right Time labels aligned
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatMs(currentProg),
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(42.dp),
                                    textAlign = TextAlign.Center
                                )
                                Slider(
                                    value = currentProg.toFloat(),
                                    onValueChange = {
                                        interactionTick++
                                        viewModel.seekVideoTo(it.toLong())
                                    },
                                    valueRange = 0f..currentDur.toFloat(),
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = PrimaryGold,
                                        inactiveTrackColor = DividerColor.copy(alpha = 0.5f),
                                        thumbColor = PrimaryGold
                                    ),
                                    thumb = {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .background(PrimaryGold, CircleShape)
                                                .border(1.5.dp, if (IsDarkTheme) Color.White else Color(0xFF101014), CircleShape)
                                        )
                                    },
                                    track = { sliderState ->
                                        SliderDefaults.Track(
                                            sliderState = sliderState,
                                            modifier = Modifier.height(3.dp),
                                            colors = SliderDefaults.colors(
                                                activeTrackColor = PrimaryGold,
                                                inactiveTrackColor = DividerColor.copy(alpha = 0.5f)
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatMs(currentDur),
                                    color = UnselectedWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(42.dp),
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Main Media Buttons Row (Repeat, -10s, Prev, Play/Pause, Next, +10s, Portrait-Aware Fullscreen)
                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Repeat / Loop Video Toggle
                                IconButton(onClick = {
                                    interactionTick++
                                    viewModel.toggleVideoRepeatMode()
                                }) {
                                    Icon(
                                        imageVector = if (videoRepeatMode) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                        contentDescription = "Ulang Video",
                                        tint = if (videoRepeatMode) PrimaryGold else TextPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // Rewind 10 Seconds
                                IconButton(onClick = {
                                    interactionTick++
                                    viewModel.seekVideoRelative(-10000L)
                                }) {
                                    Icon(Icons.Default.Replay10, contentDescription = "Mundur 10 Detik", tint = TextPrimary, modifier = Modifier.size(24.dp))
                                }

                                // Previous Video
                                IconButton(onClick = {
                                    interactionTick++
                                    viewModel.playPreviousVideo()
                                }) {
                                    Icon(Icons.Default.SkipPrevious, contentDescription = "Video Sebelumnya", tint = TextPrimary, modifier = Modifier.size(28.dp))
                                }

                                // Play / Pause Circular Center Button (Theme Primary Color)
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryGold)
                                        .border(1.5.dp, PrimaryGold.copy(alpha = 0.6f), CircleShape)
                                        .clickable {
                                            interactionTick++
                                            viewModel.toggleVideoPlayPause()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Main/Jeda",
                                        tint = Color(0xFF101014),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                // Next Video
                                IconButton(onClick = {
                                    interactionTick++
                                    viewModel.playNextVideo()
                                }) {
                                    Icon(Icons.Default.SkipNext, contentDescription = "Video Selanjutnya", tint = TextPrimary, modifier = Modifier.size(28.dp))
                                }

                                // Forward 10 Seconds
                                IconButton(onClick = {
                                    interactionTick++
                                    viewModel.seekVideoRelative(10000L)
                                }) {
                                    Icon(Icons.Default.Forward10, contentDescription = "Maju 10 Detik", tint = TextPrimary, modifier = Modifier.size(24.dp))
                                }

                                // Responsive Fullscreen & Video Aspect Ratio Fit Button
                                IconButton(
                                    onClick = {
                                        interactionTick++
                                        val newFullscreen = !viewModel.isVideoFullscreen
                                        viewModel.isVideoFullscreen = newFullscreen
                                        activity?.let { act ->
                                            val window = act.window
                                            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                                            if (newFullscreen) {
                                                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                                                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
                                                Toast.makeText(context, "Layar Penuh • Rasio: ${viewModel.videoScaleMode.value}", Toast.LENGTH_SHORT).show()
                                            } else {
                                                insetsController.show(WindowInsetsCompat.Type.systemBars())
                                                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (viewModel.isVideoFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = "Layar Penuh / Fit Rasio",
                                        tint = PrimaryGold,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- SCREEN 4: PLAYLIST MANAGER & AUTO GENERATOR ---
@Composable
fun PlaylistScreen(viewModel: MediaViewModel, onOpenDrawer: () -> Unit = {}) {
    val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val tracks by viewModel.allTracks.collectAsStateWithLifecycle()

    var showPlaylistNameDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }
    var expandedPlaylistId by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header (Solid Non-Transparent Header Bar matching Nav)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = HeaderBackground,
            tonalElevation = 2.dp,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, DividerColor.copy(alpha = 0.35f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu Slider",
                            tint = PrimaryGold,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Playlist NOERAE", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PrimaryGold, letterSpacing = 0.5.sp)
                        Text("Kategori otomatis & manual", fontSize = 11.sp, color = UnselectedWhite)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { viewModel.generateAutoPlaylists() },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Rekomendasi Genre", tint = PrimaryGold, modifier = Modifier.size(22.dp))
                    }
                    IconButton(
                        onClick = { showPlaylistNameDialog = true },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Tambah Manual",
                            tint = PrimaryGold,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = DividerColor, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Belum ada playlist", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Ketuk lambang bintang di atas untuk membuat playlist sesuai genre dipilih secara otomatis!", color = UnselectedWhite, textAlign = TextAlign.Center, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(playlists) { playlist ->
                    val isExpanded = expandedPlaylistId == playlist.id
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, DividerColor.copy(alpha = if (IsDarkTheme) 0.35f else 0.45f), RoundedCornerShape(12.dp))
                            .clickable { expandedPlaylistId = if (isExpanded) null else playlist.id }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Transparent)
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (playlist.isAutoGenerated) Icons.Default.AutoAwesome else Icons.Default.PlaylistPlay,
                                        contentDescription = null,
                                        tint = if (playlist.isAutoGenerated) AccentTeal else PrimaryGold
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(playlist.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        if (playlist.isAutoGenerated) {
                                            Text("Sesuai Genre: ${playlist.targetGenre}", fontSize = 11.sp, color = AccentTeal)
                                        }
                                    }
                                }
                                Row {
                                    IconButton(onClick = { viewModel.removePlaylist(playlist.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Hapus Playlist", tint = Color.Red.copy(alpha = 0.7f))
                                    }
                                }
                            }

                            // Show nested tracks when clicked
                            if (isExpanded) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                                Spacer(modifier = Modifier.height(8.dp))

                                val pTracksState = viewModel.getTracksForPlaylistFlow(playlist.id).collectAsStateWithLifecycle(initialValue = emptyList<MediaTrack>())
                                val pTracks = pTracksState.value

                                if (pTracks.isEmpty()) {
                                    Text("Tidak ada lagu di dalam playlist.", color = UnselectedWhite, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Tambah lagu melalui menu Pustaka pertama kali.", color = UnselectedWhite.copy(alpha = 0.5f), fontSize = 11.sp)
                                } else {
                                    pTracks.forEach { pTrack ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.playTrack(pTrack) }
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(pTrack.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(" - ${pTrack.artist}", color = UnselectedWhite, fontSize = 12.sp, maxLines = 1)
                                            }
                                            IconButton(onClick = { viewModel.removeTrackFromPlaylist(playlist.id, pTrack.id) }) {
                                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Hapus dari Playlist", tint = Color.Red.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (showPlaylistNameDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistNameDialog = false },
            title = { Text("Buat Playlist Baru", color = PrimaryGold, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = playlistNameInput,
                    onValueChange = { playlistNameInput = it },
                    placeholder = { Text("Nama Playlist...") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (playlistNameInput.isNotBlank()) {
                            viewModel.addNewPlaylist(playlistNameInput)
                            playlistNameInput = ""
                            showPlaylistNameDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
                ) {
                    Text("Buat", color = Color(0xFF101014), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPlaylistNameDialog = false }) {
                    Text("Batal", color = TextPrimary)
                }
            },
            containerColor = CardBackground
        )
    }
}

// --- WAVE OSCILLOSCOPE CANVAS VISUALIZER ---
@Composable
fun WaveVisualizer(
    isPlaying: Boolean,
    model: String = "Wave",
    colorTheme: String = "Teal",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Oscillograph")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PhaseSpeed"
    )

    val amplitudeMultiplier by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "AmplitudeScale"
    )

    val finalModifier = if (modifier == Modifier) {
        Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .border(1.dp, DividerColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
    } else {
        modifier
    }

    val primaryGold = PrimaryGold
    val accentTeal = AccentTeal

    Canvas(
        modifier = finalModifier
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        // Dynamic theme colors pairing
        val colorPrimary = when (colorTheme) {
            "Gold" -> primaryGold
            "Teal" -> accentTeal
            "Aurora" -> Color(0xFFE040FB)
            "Emerald" -> Color(0xFF00E676)
            "Cyber" -> Color(0xFFFF007F)
            "Fire" -> Color(0xFFFF5722)
            else -> accentTeal
        }
        val colorSecondary = when (colorTheme) {
            "Gold" -> Color(0xFFFFA000)
            "Teal" -> Color(0xFF00E5FF)
            "Aurora" -> Color(0xFFFF4081)
            "Emerald" -> Color(0xFFCCFF00)
            "Cyber" -> Color(0xFF00F0FF)
            "Fire" -> Color(0xFFFFEB3B)
            else -> Color(0xFF00D2FF)
        }
        val colorTertiary = when (colorTheme) {
            "Gold" -> Color(0xFFFFD54F).copy(alpha = 0.5f)
            "Teal" -> Color(0xFF80DEEA).copy(alpha = 0.5f)
            "Aurora" -> Color(0xFFEA80FC).copy(alpha = 0.5f)
            "Emerald" -> Color(0xFFA7FFEB).copy(alpha = 0.5f)
            "Cyber" -> Color(0xFFFF80AB).copy(alpha = 0.5f)
            "Fire" -> Color(0xFFFFAB91).copy(alpha = 0.5f)
            else -> Color(0xFF80DEEA).copy(alpha = 0.5f)
        }

        val effectiveModel = if (model == "Random") {
            // Dynamic cycling based on phase or time
            val cycle = ((phase / (2f * Math.PI.toFloat()) * 7f).toInt()) % 7
            when (cycle) {
                0 -> "Wave"
                1 -> "Bars"
                2 -> "Mirror"
                3 -> "NeonPulse"
                4 -> "Circular"
                5 -> "CyberWave"
                else -> "Particles"
            }
        } else {
            model
        }

        when (effectiveModel) {
            "Bars" -> {
                // Interactive Equalization bars moving with phase and amplitude
                val barCount = 32
                val padding = 4f
                val totalPadding = padding * (barCount - 1)
                val barWidth = (width - totalPadding) / barCount

                for (i in 0 until barCount) {
                    val waveVal = sin(i.toFloat() * 0.28f + phase)
                    val normalized = (waveVal + 1f) / 2f
                    val maxH = height * 0.85f
                    val minH = 14f
                    val barHeight = minH + (maxH - minH) * normalized * amplitudeMultiplier

                    val rx = i * (barWidth + padding)
                    val ry = height - barHeight - 12f

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(colorPrimary, colorSecondary.copy(alpha = 0.7f), colorTertiary)
                        ),
                        topLeft = Offset(rx, ry),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
            }
            "Mirror" -> {
                // Mirrored center-out spectrum bars
                val barCount = 36
                val padding = 4f
                val totalPadding = padding * (barCount - 1)
                val barWidth = (width - totalPadding) / barCount

                for (i in 0 until barCount) {
                    val distFromCenter = kotlin.math.abs(i - barCount / 2f) / (barCount / 2f)
                    val waveVal = sin((i * 0.35f) + phase * 1.2f)
                    val normalized = ((waveVal + 1f) / 2f) * (1f - distFromCenter * 0.4f)
                    val maxH = (height / 2f) * 0.9f
                    val halfH = (8f + maxH * normalized * amplitudeMultiplier)

                    val rx = i * (barWidth + padding)
                    val ry = centerY - halfH

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(colorPrimary, colorSecondary, colorPrimary)
                        ),
                        topLeft = Offset(rx, ry),
                        size = Size(barWidth, halfH * 2f),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
            }
            "Particles" -> {
                // Floating dynamic audio particles
                val particleCount = 50
                for (i in 0 until particleCount) {
                    val seed = i.toFloat() * 17.3f
                    val px = ((seed * 37f + phase * 40f) % width + width) % width
                    val py = centerY + sin(phase * 1.5f + i.toFloat()) * (height * 0.4f * amplitudeMultiplier)
                    val pRadius = 3.5f + (sin(phase + i) + 1f) * 3f * amplitudeMultiplier
                    val color = if (i % 2 == 0) colorPrimary else colorSecondary

                    drawCircle(
                        color = color.copy(alpha = (0.5f + 0.5f * sin(phase + i * 0.5f)).coerceIn(0.2f, 1f)),
                        radius = pRadius,
                        center = Offset(px, py)
                    )
                }
            }
            "Dots" -> {
                // Dotted frequency curve
                val dotCount = 40
                val step = width / dotCount
                for (i in 0 until dotCount) {
                    val dx = i * step + step / 2f
                    val dy = centerY + sin(i.toFloat() * 0.25f + phase) * (height * 0.38f * amplitudeMultiplier)
                    val radius = 5f + (sin(phase * 2f + i) + 1f) * 2.5f * amplitudeMultiplier

                    drawCircle(
                        color = colorPrimary,
                        radius = radius,
                        center = Offset(dx, dy)
                    )
                    // Echo shadow dot
                    drawCircle(
                        color = colorSecondary.copy(alpha = 0.4f),
                        radius = radius * 1.8f,
                        center = Offset(dx, dy)
                    )
                }
            }
            "NeonPulse" -> {
                // Cyber glowing ribbon pulse
                val ribbonCount = 6
                for (r in 0 until ribbonCount) {
                    val rPhase = phase + r * 0.5f
                    val rAmp = (height * 0.35f - r * 6f) * amplitudeMultiplier
                    val rColor = if (r % 2 == 0) colorPrimary.copy(alpha = (0.85f - r * 0.1f).coerceAtLeast(0.2f)) else colorSecondary.copy(alpha = (0.85f - r * 0.1f).coerceAtLeast(0.2f))

                    val path = Path()
                    path.moveTo(0f, centerY)
                    for (x in 0..width.toInt() step 6) {
                        val y = centerY + sin(x.toFloat() * 0.02f + rPhase) * rAmp * cos(x.toFloat() * 0.005f + rPhase * 0.5f)
                        path.lineTo(x.toFloat(), y)
                    }
                    drawPath(
                        path = path,
                        color = rColor,
                        style = Stroke(width = (6f - r * 0.7f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
                    )
                }
            }
            "LaserBeam" -> {
                // High-energy laser beam visualizer with horizontal pulses
                val beamCount = 8
                for (b in 0 until beamCount) {
                    val bPhase = phase * 2f + b * 0.8f
                    val bAmp = (height * 0.4f) * amplitudeMultiplier
                    val startY = centerY + sin(bPhase) * bAmp
                    val endY = centerY - sin(bPhase + 1f) * bAmp

                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, colorPrimary, colorSecondary, Color.Transparent)
                        ),
                        start = Offset(0f, startY),
                        end = Offset(width, endY),
                        strokeWidth = (4f + sin(phase + b) * 2f).coerceAtLeast(1.5f),
                        cap = StrokeCap.Round
                    )
                }
            }
            "FireFlame" -> {
                // Upward flame effect
                val flameCount = 30
                val colWidth = width / flameCount
                for (f in 0 until flameCount) {
                    val fVal = (sin(f * 0.4f + phase * 2.5f) + 1f) / 2f
                    val fHeight = (height * 0.8f) * fVal * amplitudeMultiplier
                    val fx = f * colWidth
                    val fy = height - fHeight

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(colorPrimary, colorSecondary, Color(0xFFFF5722).copy(alpha = 0.2f))
                        ),
                        topLeft = Offset(fx, fy),
                        size = Size(colWidth * 0.85f, fHeight),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                }
            }
            "CyberWave" -> {
                // Matrix cyber wave
                val steps = 40
                val stepX = width / steps
                for (s in 0 until steps) {
                    val angle = s * 0.3f + phase * 1.5f
                    val sy = centerY + sin(angle) * (height * 0.35f * amplitudeMultiplier)
                    val barH = (20f + 30f * cos(angle * 0.5f) * amplitudeMultiplier).coerceAtLeast(8f)

                    drawLine(
                        color = if (s % 2 == 0) colorPrimary else colorSecondary,
                        start = Offset(s * stepX, sy - barH),
                        end = Offset(s * stepX, sy + barH),
                        strokeWidth = 3.5f,
                        cap = StrokeCap.Round
                    )
                }
            }
            "Circular" -> {
                // Circular artistic pulsing neon ring
                val radius = (height * 0.25f).coerceIn(35f, 100f) + (20f * amplitudeMultiplier * (sin(phase * 1.5f) + 1f) / 2f)
                val center = Offset(width / 2, centerY)

                // Glow aura circles
                drawCircle(
                    color = colorPrimary.copy(alpha = 0.18f),
                    radius = radius + 30f,
                    center = center
                )
                // Outer ring
                drawCircle(
                    color = colorPrimary,
                    radius = radius,
                    center = center,
                    style = Stroke(width = 4f)
                )
                // Inner core orb
                drawCircle(
                    color = colorSecondary.copy(alpha = 0.8f),
                    radius = radius * 0.5f,
                    center = center,
                    style = Fill
                )

                // Outer stellar sound rays
                val rayCount = 20
                for (i in 0 until rayCount) {
                    val angle = (i * (360f / rayCount)) * Math.PI / 180.0
                    val len = 12f + (20f * amplitudeMultiplier * (sin(phase * 1.8f + i) + 1f) / 2f)
                    val sx = center.x + cos(angle).toFloat() * (radius + 6f)
                    val sy = center.y + sin(angle).toFloat() * (radius + 6f)
                    val ex = center.x + cos(angle).toFloat() * (radius + 6f + len)
                    val ey = center.y + sin(angle).toFloat() * (radius + 6f + len)

                    drawLine(
                        color = colorSecondary.copy(alpha = 0.85f),
                        start = Offset(sx, sy),
                        end = Offset(ex, ey),
                        strokeWidth = 3.5f,
                        cap = StrokeCap.Round
                    )
                }
            }
            else -> {
                // Classical overlapping sine waves oscilloscope
                drawSineWave(
                    width = width,
                    centerY = centerY,
                    phase = phase,
                    frequency = 0.02f,
                    amplitude = (height * 0.25f) * amplitudeMultiplier,
                    color = colorTertiary,
                    strokeWidth = 3.5f,
                    isDotted = true
                )

                drawSineWave(
                    width = width,
                    centerY = centerY,
                    phase = phase - 1.5f,
                    frequency = 0.012f,
                    amplitude = (height * 0.35f) * amplitudeMultiplier,
                    color = colorSecondary.copy(alpha = 0.65f),
                    strokeWidth = 4.5f
                )

                drawSineWave(
                    width = width,
                    centerY = centerY,
                    phase = phase + 1.2f,
                    frequency = 0.035f,
                    amplitude = (height * 0.22f) * amplitudeMultiplier,
                    color = colorPrimary,
                    strokeWidth = 6.5f
                )
            }
        }
    }
}

private fun DrawScope.drawSineWave(
    width: Float,
    centerY: Float,
    phase: Float,
    frequency: Float,
    amplitude: Float,
    color: Color,
    strokeWidth: Float,
    isDotted: Boolean = false
) {
    val path = Path()
    path.moveTo(0f, centerY)

    for (x in 0..width.toInt() step 6) {
        val y = centerY + sin(x.toFloat() * frequency + phase) * amplitude
        path.lineTo(x.toFloat(), y)
    }

    drawPath(
        path = path,
        color = color,
        style = if (isDotted) {
            Stroke(
                width = strokeWidth,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 15f), 0f)
            )
        } else {
            Stroke(width = strokeWidth)
        }
    )
}

// --- UTILITY FORMAT DATE/MS HELPER ---
fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}

// --- GLOBAL ADD TO PLAYLIST DIALOG ---
@Composable
fun AddToPlaylistDialog(
    track: MediaTrack,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onAddTrack: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambahkan ke Playlist", color = PrimaryGold, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pilih playlist untuk lagu: \"${track.title}\"", color = TextPrimary, fontSize = 12.sp)
                
                // Button to create a new playlist locally
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCreateDialog = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = PrimaryGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Buat Playlist Baru & Hubungkan", color = PrimaryGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                if (showCreateDialog) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DividerColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            placeholder = { Text("Nama playlist baru...", color = UnselectedWhite) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryGold, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { showCreateDialog = false }) {
                                Text("Batal", color = TextPrimary, fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    if (newPlaylistName.isNotBlank()) {
                                        onCreatePlaylist(newPlaylistName)
                                        showCreateDialog = false
                                        newPlaylistName = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
                            ) {
                                Text("Buat", color = Color(0xFF101014), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("DAFTAR PLAYLIST:", color = UnselectedWhite, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(playlists) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardBackground)
                                .clickable { onAddTrack(playlist.id) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = PrimaryGold)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(playlist.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = UnselectedWhite)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Kembali", color = TextPrimary)
            }
        },
        containerColor = CardBackground
    )
}
