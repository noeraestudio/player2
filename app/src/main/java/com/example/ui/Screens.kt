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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
        primary = Color(0xFFFFD54F),
        secondary = Color(0xFF00E5FF),
        background = Color(0xFF16151A),
        card = Color(0xFF24222A),
        surface = Color(0xFF1D1B22),
        textPrimary = Color.White,
        textSecondary = Color(0xFF9E9AA6),
        divider = Color(0xFF383540),
        isDark = true,
        iconColor = Color(0xFFFFD54F)
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

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val activeScreen = viewModel.activeScreen
    val downloadStatus by viewModel.downloadStatus.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val onOpenDrawer: () -> Unit = {
        coroutineScope.launch { drawerState.open() }
    }

    // Windows Theme Accent Palette
    val (presetPrimary, presetSecondary) = when (selectedThemeId) {
        "Blue", "Windows Blue" -> if (isDarkMode) Color(0xFF60CDFF) to Color(0xFF00B7C3) else Color(0xFF0078D7) to Color(0xFF005A9E)
        "Gold", "Windows Gold" -> if (isDarkMode) Color(0xFFFFD54F) to Color(0xFF00E5FF) else Color(0xFFD48B00) to Color(0xFF0097A7)
        "Teal", "Mica Teal" -> if (isDarkMode) Color(0xFF4DD0E1) to Color(0xFF80CBC4) else Color(0xFF00838F) to Color(0xFF00695C)
        "Purple", "Cyber Purple" -> if (isDarkMode) Color(0xFFCE93D8) to Color(0xFF80D8FF) else Color(0xFF7B1FA2) to Color(0xFF0091EA)
        "Emerald", "Xbox Emerald" -> if (isDarkMode) Color(0xFF81C784) to Color(0xFF80CBC4) else Color(0xFF2E7D32) to Color(0xFF00897B)
        "Crimson", "Crimson Red" -> if (isDarkMode) Color(0xFFFF5252) to Color(0xFFFFB74D) else Color(0xFFC62828) to Color(0xFFE65100)
        "Orange", "Sunset Orange" -> if (isDarkMode) Color(0xFFFFB74D) to Color(0xFFFF8A80) else Color(0xFFE65100) to Color(0xFFD84315)
        "Silver", "Platinum Silver" -> if (isDarkMode) Color(0xFFE0E0E0) to Color(0xFF60CDFF) else Color(0xFF616161) to Color(0xFF0078D7)
        else -> if (isDarkMode) Color(0xFF60CDFF) to Color(0xFF00E5FF) else Color(0xFF0078D7) to Color(0xFF005A9E)
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
                // Windows 11 Fluent Bloom / Frosted Acrylic Blur Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Base Canvas Tint
                    drawRect(color = if (isDarkMode) Color(0xFF101014) else Color(0xFFF4F4F6))
                    
                    // Acrylic Ambient Diffuse Glow Blobs (Reveals glowing ambient bloom as transparency increases)
                    val glowScale = if (bgTransparency <= 0f) 0.15f else (0.18f + bgTransparency * 0.45f).coerceIn(0.18f, 0.65f)
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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val navItems = listOf(
                                            Triple<String, ImageVector, String>("Library", Icons.Default.LibraryMusic, "Pustaka"),
                                            Triple<String, ImageVector, String>("Player", Icons.Default.MusicNote, "Audio"),
                                            Triple<String, ImageVector, String>("Search", Icons.Default.Search, "Cari"),
                                            Triple<String, ImageVector, String>("Video", Icons.Default.Movie, "Video"),
                                            Triple<String, ImageVector, String>("Playlist", Icons.Default.PlaylistPlay, "Playlist")
                                        )

                                        navItems.forEach { item ->
                                            val route = item.first
                                            val icon = item.second
                                            val label = item.third
                                            val isSelected = activeScreen == route

                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (isSelected) PrimaryGold.copy(alpha = 0.15f) else Color.Transparent)
                                                    .clickable { viewModel.activeScreen = route }
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

    val isEffectsEnabled by viewModel.isEffectsEnabled.collectAsStateWithLifecycle()
    val reverbPreset by viewModel.reverbPreset.collectAsStateWithLifecycle()
    val pitchSemiTones by viewModel.pitchSemiTones.collectAsStateWithLifecycle()
    val superBassStrength by viewModel.superBassStrength.collectAsStateWithLifecycle()
    val virtualizer3DStrength by viewModel.virtualizer3DStrength.collectAsStateWithLifecycle()
    val lrAudioBalance by viewModel.lrAudioBalance.collectAsStateWithLifecycle()

    val isEqualizerEnabled by viewModel.isEqualizerEnabled.collectAsStateWithLifecycle()
    val equalizerBands by viewModel.equalizerBands.collectAsStateWithLifecycle()
    val selectedPresetName by viewModel.selectedPresetName.collectAsStateWithLifecycle()

    val volume by viewModel.volume.collectAsStateWithLifecycle()
    val videoVolume by viewModel.videoVolume.collectAsStateWithLifecycle()
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsStateWithLifecycle()
    val sleepTimerRemaining by viewModel.sleepTimerRemainingSeconds.collectAsStateWithLifecycle()
    val allTracks by viewModel.allTracks.collectAsStateWithLifecycle()

    // Accordion expand/collapse states
    var isThemeExpanded by remember { mutableStateOf(false) }
    var isEffectsExpanded by remember { mutableStateOf(true) }
    var isEqualizerExpanded by remember { mutableStateOf(true) }
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
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PrimaryGold.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(20.dp))
                }
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
                        Text("Pilihan Warna", fontSize = 11.sp, color = UnselectedWhite, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        val themePalettes = listOf(
                            Triple("Gold", "Gold", Color(0xFFFFB900)),
                            Triple("Blue", "Blue", Color(0xFF0078D7)),
                            Triple("Teal", "Teal", Color(0xFF00B7C3)),
                            Triple("Purple", "Purple", Color(0xFF881798)),
                            Triple("Emerald", "Green", Color(0xFF107C41)),
                            Triple("Crimson", "Red", Color(0xFFE81123)),
                            Triple("Orange", "Orange", Color(0xFFF7630C)),
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
                                           else "Normal (0)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryGold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Reset",
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

                        // 5. L / R AUDIO (STEREO PAN BALANCE)
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
                                           else "Tengah (Center)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentTeal
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tengah",
                                    fontSize = 9.sp,
                                    color = PrimaryGold,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(DividerColor.copy(alpha = 0.3f))
                                        .clickable(enabled = isEffectsEnabled) { viewModel.setLrAudioBalance(0f) }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
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
    var searchQuery by remember { mutableStateOf("") }
    var groupSelection by remember { mutableStateOf("Semua") } // Semua, Audio, Video, Lossless
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

    val filteredTracks = remember(tracks, searchQuery, groupSelection) {
        tracks.filter {
            val matchesQuery = searchQuery.isBlank() ||
                              it.title.contains(searchQuery, ignoreCase = true) ||
                              it.format.contains(searchQuery, ignoreCase = true) ||
                              it.genre.contains(searchQuery, ignoreCase = true) ||
                              it.artist.contains(searchQuery, ignoreCase = true) ||
                              it.album.contains(searchQuery, ignoreCase = true)

            when (groupSelection) {
                "Semua" -> matchesQuery
                "Lossless" -> matchesQuery && (it.format == "FLAC" || it.format == "WAV")
                "Video" -> matchesQuery && it.isVideo
                "Audio" -> matchesQuery && !it.isVideo
                else -> matchesQuery
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // App Header (Solid Non-Transparent Header Bar matching Nav)
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
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (IsDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                            .border(1.dp, DividerColor.copy(alpha = 0.35f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu Slider",
                            tint = PrimaryGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "NOERAE PLAYER",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = PrimaryGold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Pustaka Lagu • ${tracks.size} item",
                            fontSize = 11.sp,
                            color = UnselectedWhite
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Folder scanner button
                    IconButton(
                        onClick = { showFolderScannerDialog = true },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (IsDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                            .border(1.dp, DividerColor.copy(alpha = 0.35f), CircleShape)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = "Muat Folder Penyimpanan", tint = PrimaryGold, modifier = Modifier.size(20.dp))
                    }

                    // Automatic scan button (Reload Pustaka)
                    IconButton(
                        onClick = {
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
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (IsDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                            .border(1.dp, DividerColor.copy(alpha = 0.35f), CircleShape)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Pindai Perangkat", tint = PrimaryGold, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {

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
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
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

        Spacer(modifier = Modifier.height(12.dp))

        // Filtering Chips Row (Ensured crystal clear text above background on any transparency)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val chips = listOf("Semua", "Audio", "Video", "Lossless")
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
                        .clickable { groupSelection = chip }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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

        Spacer(modifier = Modifier.height(12.dp))

        // Direct Clean Song List (Daftar Lagu)
        if (filteredTracks.isEmpty()) {
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
                    Text("Daftar lagu tidak ditemukan", color = UnselectedWhite, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Tekan tombol segarkan untuk memindai berkas otomatis", color = UnselectedWhite.copy(alpha = 0.6f), fontSize = 12.sp)
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
                        onDownload = {},
                        onAddToPlaylist = { viewModel.trackToAddToPlaylist = track }
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
    onDownload: () -> Unit = {},
    onAddToPlaylist: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DividerColor.copy(alpha = if (IsDarkTheme) 0.35f else 0.45f), RoundedCornerShape(12.dp))
            .pointerInput(track.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            }
            .testTag("track_card_${track.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
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

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.title,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // Lossless tag decoration
                    if (track.format == "FLAC" || track.format == "WAV") {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .border(1.dp, PrimaryGold.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .background(PrimaryGold.copy(alpha = 0.1f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("LOSSLESS", fontSize = 8.sp, color = PrimaryGold, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.format,
                        color = AccentTeal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (track.sampleRate.isNotBlank()) {
                        Text(" • ", color = DividerColor, fontSize = 11.sp)
                        Text(
                            text = track.sampleRate,
                            color = UnselectedWhite,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Delete quick icon button
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Hapus Berkas",
                    tint = UnselectedWhite.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Add to Playlist Button
            if (onAddToPlaylist != null) {
                IconButton(onClick = onAddToPlaylist) {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = "Tambahkan ke Playlist", tint = PrimaryGold)
                }
            }
        }
    }
}

// --- SCREEN 2: MAIN DUAL-CONTROLLER PLAYER ---
@Composable
fun PlayerScreen(viewModel: MediaViewModel, onOpenDrawer: () -> Unit = {}) {
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

    var showEditorDrawer by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSpectrumDialog by remember { mutableStateOf(false) }
    var showAbRepeatDialog by remember { mutableStateOf(false) }
    var showMenuDropdown by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("Karaoke") }
    var spectrumModel by remember { mutableStateOf("Wave") }
    var spectrumColor by remember { mutableStateOf("Teal") }

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
                // Top Action Toolbar (Solid Non-Transparent Header Bar matching Nav)
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
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (IsDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                                .border(1.dp, DividerColor.copy(alpha = 0.35f), CircleShape)
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu Pengaturan", tint = PrimaryGold, modifier = Modifier.size(20.dp))
                        }
                        Text("NOERAE PLAYER", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PrimaryGold, letterSpacing = 1.sp)
                        Box {
                            IconButton(
                                onClick = { showMenuDropdown = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (IsDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                                    .border(1.dp, DividerColor.copy(alpha = 0.35f), CircleShape)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Pilihan Menu", tint = TextPrimary, modifier = Modifier.size(20.dp))
                            }
                            DropdownMenu(
                                expanded = showMenuDropdown,
                                onDismissRequest = { showMenuDropdown = false },
                                modifier = Modifier.background(CardBackground)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit Tag Musik (MP3 Info)", color = TextPrimary, fontSize = 13.sp) },
                                    onClick = {
                                        showMenuDropdown = false
                                        showEditorDrawer = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp)) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Equaliser Studio 5-Band", color = TextPrimary, fontSize = 13.sp) },
                                    onClick = {
                                        showMenuDropdown = false
                                        showEqualizerDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Album, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp)) }
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
                                    text = { Text("Gaya & Warna Spektrum", color = TextPrimary, fontSize = 13.sp) },
                                    onClick = {
                                        showMenuDropdown = false
                                        showSpectrumDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp)) }
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
                                    text = { Text("Tambahkan ke Playlist", color = TextPrimary, fontSize = 13.sp) },
                                    onClick = {
                                        showMenuDropdown = false
                                        viewModel.trackToAddToPlaylist = track
                                    },
                                    leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(18.dp)) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // SWITCH TABS: Lirik Karaoke vs Spektrum Musik (Glass card background responds to transparency slider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(CardBackground, RoundedCornerShape(24.dp))
                        .border(1.dp, DividerColor.copy(alpha = if (IsDarkTheme) 0.35f else 0.45f), RoundedCornerShape(24.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("Karaoke", "Spektrum").forEach { tab ->
                        val isSelected = activeTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) PrimaryGold else Color.Transparent)
                                .clickable { activeTab = tab }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (tab == "Karaoke") "Lirik Karaoke" else "Spektrum Musik",
                                color = if (isSelected) Color(0xFF101014) else TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.zIndex(1f)
                            )
                        }
                    }
                }
            }

            // ================== SECTION 2: DYNAMIC MIDDLE CONTENT ==================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (activeTab == "Karaoke") {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Title & Artist ontop of lyric card
                        Text(
                            text = track.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Text(
                            text = track.artist,
                            fontSize = 13.sp,
                            color = UnselectedWhite,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        )
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .border(1.dp, DividerColor.copy(alpha = if (IsDarkTheme) 0.35f else 0.45f), RoundedCornerShape(16.dp))
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (lyricsLines.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "Belum ada lirik lagu terpasang.",
                                            color = UnselectedWhite,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = { viewModel.requestAiLyricGeneration(track) },
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                                            shape = RoundedCornerShape(50.dp)
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF101014), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Pembuat Lirik AI Gemini", color = Color(0xFF101014), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        itemsIndexed(lyricsLines) { index, line ->
                                            val isActive = index == activeLyricIndex
                                            val textColor = if (isActive) PrimaryGold else TextPrimary.copy(alpha = 0.5f)
                                            val textWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Normal

                                            Text(
                                                text = line.text,
                                                color = textColor,
                                                fontSize = if (isActive) 16.sp else 13.sp,
                                                fontWeight = textWeight,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 20.dp)
                                                    .clickable { viewModel.seekTo(line.timeMs) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Spektrum Musik View (Includes album cover & oscillating WaveVisualizer)
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceAround
                    ) {
                        Text(
                            text = track.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Text(
                            text = track.artist,
                            fontSize = 13.sp,
                            color = UnselectedWhite,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        )

                        // Album artwork with dynamic shadow and glow and beautiful center icons with overlaying translucent spectrum!
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .shadow(20.dp, RoundedCornerShape(28.dp), ambientColor = Color(0xFF4F378B), spotColor = Color(0xFF21005D)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color(0xFF4F378B), Color(0xFF21005D))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!track.imageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = track.imageUrl,
                                        contentDescription = "Cover Album Dinamis",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else if (track.isVideo) {
                                    Icon(
                                        imageVector = Icons.Default.Movie,
                                        contentDescription = "Video Cover",
                                        tint = PrimaryGold,
                                        modifier = Modifier.size(64.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = "Music Cover",
                                        tint = PrimaryGold,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }

                            // Transparent background sound spectrum layered overlay on top of cover art
                            WaveVisualizer(
                                isPlaying = isPlaying, 
                                model = spectrumModel, 
                                colorTheme = spectrumColor,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(Color.Black.copy(alpha = 0.35f))
                            )
                        }
                    }
                }
            }

            // ================== SECTION 3: FIXED BOTTOM CONTROLLER CONSOLE ==================
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .border(1.dp, DividerColor.copy(alpha = if (IsDarkTheme) 0.35f else 0.45f), RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Seekbar slider progress
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = playbackProgress.toFloat(),
                            onValueChange = { viewModel.seekTo(it.toLong()) },
                            valueRange = 0f..duration.toFloat(),
                            colors = SliderDefaults.colors(
                                activeTrackColor = PrimaryGold,
                                inactiveTrackColor = DividerColor.copy(alpha = 0.4f),
                                thumbColor = PrimaryGold
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatMs(playbackProgress), color = UnselectedWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(formatMs(duration), color = UnselectedWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Action media buttons row (Shuffle, Prev, Play, Next, Repeat)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shuffle icon
                        IconButton(
                            onClick = { viewModel.setShuffle(!isShuffle) },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isShuffle) PrimaryGold.copy(alpha = 0.2f) else if (IsDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                                .border(1.dp, if (isShuffle) PrimaryGold.copy(alpha = 0.5f) else DividerColor.copy(alpha = 0.35f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Acak",
                                tint = if (isShuffle) AccentTeal else TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Skip previous icon
                        IconButton(
                            onClick = { viewModel.playPreviousTrack() },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (IsDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                                .border(1.dp, DividerColor.copy(alpha = 0.35f), CircleShape)
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Sebelumnya", tint = TextPrimary, modifier = Modifier.size(26.dp))
                        }

                        // Play/Pause circular container (Theme Primary Color)
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(PrimaryGold)
                                .border(1.5.dp, PrimaryGold.copy(alpha = 0.6f), CircleShape)
                                .clickable { viewModel.togglePlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Mainkan/Jeda",
                                tint = Color(0xFF101014),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Skip next icon
                        IconButton(
                            onClick = { viewModel.playNextTrack() },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (IsDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                                .border(1.dp, DividerColor.copy(alpha = 0.35f), CircleShape)
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Selanjutnya", tint = TextPrimary, modifier = Modifier.size(26.dp))
                        }

                        // Standard Repeat Mode button
                        IconButton(
                            onClick = { viewModel.toggleRepeatMode() },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (repeatMode != 0) PrimaryGold.copy(alpha = 0.2f) else if (IsDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                                .border(1.dp, if (repeatMode != 0) PrimaryGold.copy(alpha = 0.5f) else DividerColor.copy(alpha = 0.35f), CircleShape)
                        ) {
                            if (repeatMode == 1) {
                                // Repeat One
                                Icon(
                                    imageVector = Icons.Default.RepeatOne,
                                    contentDescription = "Looping Satu Lagu",
                                    tint = PrimaryGold,
                                    modifier = Modifier.size(22.dp)
                                )
                            } else {
                                // Repeat All
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = "Looping Semua Lagu",
                                    tint = if (repeatMode == 2) AccentTeal else TextPrimary.copy(alpha = 0.65f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ================== POPUP SUB-DIALOGS FOR HIDDEN OPTIONS MENU ==================
        // Equalizer dialog
        if (showEqualizerDialog) {
            AlertDialog(
                onDismissRequest = { showEqualizerDialog = false },
                title = {
                    Text(
                        "MIXER & EQUALIZER STUDIO (NOERAE)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = PrimaryGold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Preset options selection
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val presetNames = listOf("Normal", "Pop", "Rock", "Jazz", "Klasik", "Bass Boost")
                            items(presetNames) { preset ->
                                val selected = selectedPresetName == preset
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (selected) PrimaryGold else DividerColor)
                                        .border(1.dp, if (selected) PrimaryGold else Color.Transparent, RoundedCornerShape(20.dp))
                                        .clickable { viewModel.applyPreset(preset) }
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = preset,
                                        fontSize = 12.sp,
                                        color = if (selected) Color(0xFF101014) else TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Text("EQUALIZER 5-BAND (VERTIKAL):", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = UnselectedWhite)

                        // Five Vertical Slider Bands Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bands = listOf("60Hz", "230Hz", "910Hz", "4kHz", "14kHz")
                            bands.forEachIndexed { index, bandName ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(bandName, fontSize = 10.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(115.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Slider(
                                            value = equalizerBands[index],
                                            onValueChange = { viewModel.updateEqualizerBand(index, it) },
                                            valueRange = -15f..15f,
                                            modifier = Modifier
                                                .width(105.dp)
                                                .rotate(-90f),
                                            colors = SliderDefaults.colors(
                                                activeTrackColor = PrimaryGold,
                                                inactiveTrackColor = DividerColor,
                                                thumbColor = PrimaryGold
                                            )
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${equalizerBands[index].toInt()} dB", fontSize = 9.sp, color = AccentTeal, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                                             // Master Volume Controllers Section (Dual adjusters)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DividerColor.copy(alpha = 0.3f))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 1. Audio Master Volume
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (volume == 0f) Icons.Default.VolumeMute else if (volume < 0.5f) Icons.Default.VolumeDown else Icons.Default.VolumeUp,
                                            contentDescription = "Volume Mixer Audio",
                                            tint = PrimaryGold,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("VOLUME AUDIO UTAMA", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                                    }
                                    Text("${(volume * 100).toInt()}%", fontSize = 11.sp, color = PrimaryGold, fontWeight = FontWeight.ExtraBold)
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Slider(
                                    value = volume,
                                    onValueChange = { viewModel.setVolume(it) },
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = AccentTeal,
                                        inactiveTrackColor = DividerColor,
                                        thumbColor = AccentTeal
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Divider line
                            HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 1.dp)

                            // 2. Video Volume
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (videoVolume == 0f) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                            contentDescription = "Volume Mixer Video",
                                            tint = AccentTeal,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("VOLUME VIDEO LATAR", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                                    }
                                    Text("${(videoVolume * 100).toInt()}%", fontSize = 11.sp, color = AccentTeal, fontWeight = FontWeight.ExtraBold)
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Slider(
                                    value = videoVolume,
                                    onValueChange = { viewModel.setVideoVolume(it) },
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = PrimaryGold,
                                        inactiveTrackColor = DividerColor,
                                        thumbColor = PrimaryGold
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }     }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showEqualizerDialog = false }) {
                        Text("Tutup Mixer", color = PrimaryGold, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = CardBackground,
                iconContentColor = PrimaryGold
            )
        }

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

        // Speed dialog
        if (showSpeedDialog) {
            AlertDialog(
                onDismissRequest = { showSpeedDialog = false },
                title = {
                    Text(
                        "TEMPO SPEED CONTROL",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = PrimaryGold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("KECEPATAN TEMPO", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                            Text("${playbackSpeed}x", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentTeal)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Slider(
                            value = playbackSpeed,
                            onValueChange = { viewModel.setTempo(it) },
                            valueRange = 0.5f..2.0f,
                            steps = 5,
                            colors = SliderDefaults.colors(
                                activeTrackColor = AccentTeal,
                                inactiveTrackColor = DividerColor,
                                thumbColor = AccentTeal
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSpeedDialog = false }) {
                        Text("Selesai", color = PrimaryGold, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = CardBackground,
                iconContentColor = PrimaryGold
            )
        }

        // Spectrum / Visualizer Options Dialog
        if (showSpectrumDialog) {
            AlertDialog(
                onDismissRequest = { showSpectrumDialog = false },
                title = { Text("Gaya & Warna Spektrum", color = PrimaryGold, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text("GAYA VISUALISASI:", color = UnselectedWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            listOf("Wave" to "Gelombang Sino", "Bars" to "Balok Spektrum", "Circular" to "Lingkaran Ring").forEach { (id, label) ->
                                val isSelected = spectrumModel == id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) DividerColor else Color.Transparent)
                                        .clickable { spectrumModel = id }
                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { spectrumModel = id },
                                        colors = RadioButtonDefaults.colors(selectedColor = PrimaryGold, unselectedColor = UnselectedWhite)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(label, color = if (isSelected) PrimaryGold else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Column {
                            Text("TEMA WARNA SPEKTRUM:", color = UnselectedWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            listOf("Gold" to "Emas Premium (Gold)", "Teal" to "Toska Neon (Teal)", "Aurora" to "Sinar Senja (Aurora)", "Emerald" to "Hijau Zamrud").forEach { (id, label) ->
                                val isSelected = spectrumColor == id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) DividerColor else Color.Transparent)
                                        .clickable { spectrumColor = id }
                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { spectrumColor = id },
                                        colors = RadioButtonDefaults.colors(selectedColor = PrimaryGold, unselectedColor = UnselectedWhite)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(label, color = if (isSelected) PrimaryGold else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (IsDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                        .border(1.dp, DividerColor.copy(alpha = 0.35f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu Slider",
                        tint = PrimaryGold,
                        modifier = Modifier.size(20.dp)
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
    onPrepared: (MediaPlayer) -> Unit,
    onCompletion: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlayerPrepared by remember { mutableStateOf(false) }
    var surfaceTextureState by remember { mutableStateOf<SurfaceTexture?>(null) }
    var hasError by remember { mutableStateOf(false) }

    fun setupMediaPlayer(st: SurfaceTexture) {
        try {
            isPlayerPrepared = false
            mediaPlayer?.let { oldMp ->
                try {
                    oldMp.setOnErrorListener(null)
                    oldMp.setOnPreparedListener(null)
                    oldMp.setOnCompletionListener(null)
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
            mp.setOnPreparedListener { preparedMp ->
                try {
                    isPlayerPrepared = true
                    hasError = false
                    preparedMp.isLooping = isLooping
                    preparedMp.setVolume(volume, volume)
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

    DisposableEffect(Unit) {
        onDispose {
            isPlayerPrepared = false
            try {
                mediaPlayer?.let { mp ->
                    mp.setOnErrorListener(null)
                    mp.setOnPreparedListener(null)
                    mp.setOnCompletionListener(null)
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
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                            surfaceTextureState = surface
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

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
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (hasError) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
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
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var areControlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableStateOf(0) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showHeaderVolumeSlider by remember { mutableStateOf(false) }

    var isLockWarningVisible by remember { mutableStateOf(true) }
    var lockInteractionTick by remember { mutableStateOf(0) }

    val videoTracks = remember(tracks) { tracks.filter { it.isVideo } }

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

    // Auto-hide controls after 3 seconds of inactivity while playing
    LaunchedEffect(areControlsVisible, interactionTick, isVideoPlaying) {
        if (areControlsVisible && isVideoPlaying) {
            delay(3000)
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

    // Speed Selection Dialog with extensive slow motion presets
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            containerColor = CardBackground,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Kecepatan Putar Video", color = PrimaryGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Pilih preset kecepatan lambat/cepat atau sesuaikan slider:",
                        color = UnselectedWhite,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Preset Speed Buttons with 0.1x and 0.25x slow motion
                    val presets = listOf(
                        0.1f to "0.1x (Super Lambat)",
                        0.25f to "0.25x (Sangat Lambat)",
                        0.5f to "0.5x (Lambat)",
                        0.75f to "0.75x (Sedang)",
                        1.0f to "1.0x (Normal)",
                        1.25f to "1.25x (Cepat)",
                        1.5f to "1.5x (Lebih Cepat)",
                        2.0f to "2.0x (Sangat Cepat)",
                        3.0f to "3.0x (Maksimal)"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        presets.chunked(2).forEach { rowPresets ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowPresets.forEach { (speed, label) ->
                                    val isSelected = (videoPlaybackSpeed - speed).let { kotlin.math.abs(it) < 0.04f }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.setVideoPlaybackSpeed(speed)
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSelected) AccentTeal.copy(alpha = 0.25f) else Color.Transparent,
                                            contentColor = if (isSelected) AccentTeal else TextPrimary
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) AccentTeal else DividerColor
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            label,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    }
                                }
                                if (rowPresets.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = DividerColor.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Slider for granular speed adjustment from 0.1x to 3.0x
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Kustom Kecepatan:", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "%.2fx".format(videoPlaybackSpeed),
                            color = PrimaryGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Slider(
                        value = videoPlaybackSpeed,
                        onValueChange = { viewModel.setVideoPlaybackSpeed(it) },
                        valueRange = 0.1f..3.0f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = PrimaryGold,
                            inactiveTrackColor = DividerColor,
                            thumbColor = PrimaryGold
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSpeedDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
                ) {
                    Text("Tutup", color = Color(0xFF101014), fontWeight = FontWeight.Bold)
                }
            }
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
                        .clip(CircleShape)
                        .background(if (IsDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                        .border(1.dp, DividerColor.copy(alpha = 0.35f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu Slider",
                        tint = PrimaryGold,
                        modifier = Modifier.size(20.dp)
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    // Top Bar: Row 1 (File Name Bar), Row 2 (Quick Actions Bar: Volume, Acak, Rotasi, Kecepatan, Kunci), Row 3 (Expandable Volume Slider)
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // ROW 1: Bar Nama File & Menu
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, DividerColor.copy(alpha = if (IsDarkTheme) 0.35f else 0.45f), RoundedCornerShape(14.dp))
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
                                IconButton(
                                    onClick = {
                                        interactionTick++
                                        onOpenDrawer()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu Slider", tint = PrimaryGold, modifier = Modifier.size(22.dp))
                                }

                                // File Name / Track Title
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = track.title,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = if (track.artist.isNotBlank() && track.artist != "<unknown>") track.artist else "Video • ${track.format}",
                                        color = PrimaryGold,
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                // SAF Folder Scanner Button
                                IconButton(
                                    onClick = {
                                        interactionTick++
                                        videoFolderPicker.launch(null)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = "Pindai Folder Video", tint = PrimaryGold, modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        // ROW 2: Bar Baru di Bawah Nama File (Volume, Acak, Rotasi, Kecepatan, Kunci - Seragam tanpa border berlebih)
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, DividerColor.copy(alpha = if (IsDarkTheme) 0.35f else 0.45f), RoundedCornerShape(12.dp))
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
                                // 1. Icon Volume (Klik muncul garis volume)
                                IconButton(
                                    onClick = {
                                        interactionTick++
                                        showHeaderVolumeSlider = !showHeaderVolumeSlider
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (videoVolume == 0f) Icons.Default.VolumeOff else if (videoVolume < 0.5f) Icons.Default.VolumeDown else Icons.Default.VolumeUp,
                                        contentDescription = "Pengaturan Volume",
                                        tint = if (showHeaderVolumeSlider) AccentTeal else TextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
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
                                        tint = if (isVideoShuffle) AccentTeal else TextPrimary,
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
                                        tint = if (viewModel.isVideoAutoRotate) AccentTeal else TextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // 4. Icon Kecepatan Putar (Seragam tanpa border)
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
                                        tint = if (videoPlaybackSpeed != 1.0f) AccentTeal else TextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // 5. Icon Kunci Layar (Seragam tanpa border)
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

                        // ROW 3: Expandable Header Volume Slider Row
                        AnimatedVisibility(
                            visible = showHeaderVolumeSlider,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = CardBackground),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, AccentTeal.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { interactionTick++ }
                                ) {
                                    IconButton(
                                        onClick = {
                                            interactionTick++
                                            if (videoVolume > 0f) {
                                                viewModel.setVideoVolume(0f)
                                            } else {
                                                viewModel.setVideoVolume(1f)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (videoVolume == 0f) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                            contentDescription = "Mute/Unmute",
                                            tint = AccentTeal,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Slider(
                                        value = videoVolume,
                                        onValueChange = {
                                            interactionTick++
                                            viewModel.setVideoVolume(it)
                                        },
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = AccentTeal,
                                            inactiveTrackColor = DividerColor,
                                            thumbColor = AccentTeal
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "${(videoVolume * 100).toInt()}%",
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }

                    // COMPACT BOTTOM PLAYER BAR & CONTROLS (Thinner bar, rounded thumb, matching audio console styling)
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .border(1.dp, DividerColor.copy(alpha = if (IsDarkTheme) 0.35f else 0.45f), RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { interactionTick++ },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                        // Thinner Progress Seekbar Slider with Rounded Circle Thumb
                        val currentDur = if (videoDuration > 0) videoDuration else track.duration.coerceAtLeast(1L)
                        val currentProg = videoProgress.coerceIn(0L, currentDur)

                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
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
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(formatMs(currentProg), color = TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                Text(formatMs(currentDur), color = UnselectedWhite, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(1.dp))

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

                            // Responsive Orientation-Aware Fullscreen Button (Preserves current portrait or landscape orientation)
                            IconButton(onClick = {
                                interactionTick++
                                val newFullscreen = !viewModel.isVideoFullscreen
                                viewModel.isVideoFullscreen = newFullscreen
                                activity?.let { act ->
                                    val window = act.window
                                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                                    if (newFullscreen) {
                                        insetsController.hide(WindowInsetsCompat.Type.systemBars())
                                        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                        // Do not force landscape - preserve user's current screen orientation (portrait or landscape)
                                        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
                                    } else {
                                        insetsController.show(WindowInsetsCompat.Type.systemBars())
                                        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    }
                                }
                            }) {
                                Icon(
                                    imageVector = if (viewModel.isVideoFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Layar Penuh",
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
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (IsDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                            .border(1.dp, DividerColor.copy(alpha = 0.35f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu Slider",
                            tint = PrimaryGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Playlist NOERAE", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PrimaryGold, letterSpacing = 0.5.sp)
                        Text("Kategori otomatis & manual", fontSize = 11.sp, color = UnselectedWhite)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { viewModel.generateAutoPlaylists() },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (IsDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                            .border(1.dp, DividerColor.copy(alpha = 0.35f), CircleShape)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Rekomendasi Genre", tint = PrimaryGold, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = { showPlaylistNameDialog = true },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(PrimaryGold)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Tambah Manual",
                            tint = Color(0xFF101014),
                            modifier = Modifier.size(20.dp)
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
            else -> accentTeal
        }
        val colorSecondary = when (colorTheme) {
            "Gold" -> Color(0xFFFFA000)
            "Teal" -> Color(0xFF00E5FF)
            "Aurora" -> Color(0xFFFF4081)
            "Emerald" -> Color(0xFFCCFF00)
            else -> Color(0xFF00D2FF)
        }
        val colorTertiary = when (colorTheme) {
            "Gold" -> Color(0xFFFFD54F).copy(alpha = 0.4f)
            "Teal" -> Color(0xFF80DEEA).copy(alpha = 0.4f)
            "Aurora" -> Color(0xFFEA80FC).copy(alpha = 0.4f)
            "Emerald" -> Color(0xFFA7FFEB).copy(alpha = 0.4f)
            else -> Color(0xFF80DEEA).copy(alpha = 0.4f)
        }

        when (model) {
            "Bars" -> {
                // Interactive Equalization bars moving with phase and amplitude
                val barCount = 28
                val padding = 5f
                val totalPadding = padding * (barCount - 1)
                val barWidth = (width - totalPadding) / barCount

                for (i in 0 until barCount) {
                    val waveVal = sin(i.toFloat() * 0.28f + phase)
                    val normalized = (waveVal + 1f) / 2f
                    val maxH = height * 0.75f
                    val minH = 12f
                    val barHeight = minH + (maxH - minH) * normalized * amplitudeMultiplier

                    val rx = i * (barWidth + padding)
                    val ry = height - barHeight - 12f

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(colorPrimary, colorSecondary.copy(alpha = 0.4f))
                        ),
                        topLeft = Offset(rx, ry),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }
            "Circular" -> {
                // Circular artistic pulsing neon ring
                val radius = 28f + (16f * amplitudeMultiplier * (sin(phase * 1.5f) + 1f) / 2f)
                val center = Offset(width / 2, height / 2)

                // Glow aura circles
                drawCircle(
                    color = colorPrimary.copy(alpha = 0.15f),
                    radius = radius + 25f,
                    center = center
                )
                // Outer ring
                drawCircle(
                    color = colorPrimary,
                    radius = radius,
                    center = center,
                    style = Stroke(width = 3.5f)
                )
                // Inner core orb
                drawCircle(
                    color = colorSecondary,
                    radius = radius * 0.55f,
                    center = center,
                    style = Fill
                )

                // Outer stellar sound rays
                val rayCount = 16
                for (i in 0 until rayCount) {
                    val angle = (i * (360f / rayCount)) * Math.PI / 180.0
                    val len = 10f + (14f * amplitudeMultiplier * (sin(phase + i) + 1f) / 2f)
                    val sx = center.x + cos(angle).toFloat() * (radius + 6f)
                    val sy = center.y + sin(angle).toFloat() * (radius + 6f)
                    val ex = center.x + cos(angle).toFloat() * (radius + 6f + len)
                    val ey = center.y + sin(angle).toFloat() * (radius + 6f + len)

                    drawLine(
                        color = colorSecondary.copy(alpha = 0.85f),
                        start = Offset(sx, sy),
                        end = Offset(ex, ey),
                        strokeWidth = 3f,
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
                    amplitude = 32f * amplitudeMultiplier,
                    color = colorTertiary,
                    strokeWidth = 3f,
                    isDotted = true
                )

                drawSineWave(
                    width = width,
                    centerY = centerY,
                    phase = phase - 1.5f,
                    frequency = 0.012f,
                    amplitude = 42f * amplitudeMultiplier,
                    color = colorSecondary.copy(alpha = 0.55f),
                    strokeWidth = 4f
                )

                drawSineWave(
                    width = width,
                    centerY = centerY,
                    phase = phase + 1.2f,
                    frequency = 0.035f,
                    amplitude = 25f * amplitudeMultiplier,
                    color = colorPrimary,
                    strokeWidth = 6f
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
