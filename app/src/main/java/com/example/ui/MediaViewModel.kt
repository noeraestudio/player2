package com.example.ui

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MediaRepository
    private var mediaPlayer: MediaPlayer? = null
    private var nativeEq: Equalizer? = null
    private var nativeBassBoost: BassBoost? = null
    private var nativeVirtualizer: Virtualizer? = null
    private var nativeReverb: PresetReverb? = null

    // UI Navigation State
    var activeScreen by mutableStateOf("Library")

    // Theme Management State (Light/Dark Mode, Presets, Custom Accent, Icon & Text Colors)
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _selectedThemeId = MutableStateFlow("Gold") // Gold, Teal, Purple, Crimson, Emerald, Amoled, Blue, Orange
    val selectedThemeId: StateFlow<String> = _selectedThemeId.asStateFlow()

    private val _customPrimaryColor = MutableStateFlow<Long?>(null)
    val customPrimaryColor: StateFlow<Long?> = _customPrimaryColor.asStateFlow()

    private val _customSecondaryColor = MutableStateFlow<Long?>(null)
    val customSecondaryColor: StateFlow<Long?> = _customSecondaryColor.asStateFlow()

    private val _customTextColor = MutableStateFlow<Long?>(null)
    val customTextColor: StateFlow<Long?> = _customTextColor.asStateFlow()

    private val _customIconColor = MutableStateFlow<Long?>(null)
    val customIconColor: StateFlow<Long?> = _customIconColor.asStateFlow()

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun setDarkMode(dark: Boolean) {
        _isDarkMode.value = dark
    }

    fun setTheme(themeId: String) {
        _selectedThemeId.value = themeId
        _customPrimaryColor.value = null
        _customSecondaryColor.value = null
    }

    fun setCustomPrimaryColor(colorLong: Long?) {
        _customPrimaryColor.value = colorLong
    }

    fun setCustomSecondaryColor(colorLong: Long?) {
        _customSecondaryColor.value = colorLong
    }

    fun setCustomTextColor(colorLong: Long?) {
        _customTextColor.value = colorLong
    }

    fun setCustomIconColor(colorLong: Long?) {
        _customIconColor.value = colorLong
    }

    fun resetTheme() {
        _isDarkMode.value = false
        _selectedThemeId.value = "Gold"
        _customPrimaryColor.value = null
        _customSecondaryColor.value = null
        _customTextColor.value = null
        _customIconColor.value = null
        _backgroundTransparency.value = 1.0f
    }

    // Background Transparency Setting (0f = opaque solid, 1f = fully transparent glass, default 100% / 1.0f)
    private val _backgroundTransparency = MutableStateFlow(1.0f)
    val backgroundTransparency: StateFlow<Float> = _backgroundTransparency.asStateFlow()

    fun setBackgroundTransparency(transparency: Float) {
        _backgroundTransparency.value = transparency.coerceIn(0f, 1f)
    }

    // Sleep Timer State
    private var sleepTimerJob: Job? = null
    private val _sleepTimerMinutes = MutableStateFlow(0)
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()

    private val _sleepTimerRemainingSeconds = MutableStateFlow(0)
    val sleepTimerRemainingSeconds: StateFlow<Int> = _sleepTimerRemainingSeconds.asStateFlow()

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutes.value = minutes
        if (minutes <= 0) {
            _sleepTimerRemainingSeconds.value = 0
            _downloadStatus.value = "Pengatur waktu tidur dinonaktifkan"
            viewModelScope.launch {
                delay(2000)
                _downloadStatus.value = null
            }
            return
        }
        _sleepTimerRemainingSeconds.value = minutes * 60
        _downloadStatus.value = "Waktu tidur diatur: $minutes Menit"
        viewModelScope.launch {
            delay(2500)
            _downloadStatus.value = null
        }

        sleepTimerJob = viewModelScope.launch {
            while (_sleepTimerRemainingSeconds.value > 0) {
                delay(1000)
                _sleepTimerRemainingSeconds.value -= 1
            }
            // Timer expired: stop playback
            pauseMedia()
            _sleepTimerMinutes.value = 0
            _downloadStatus.value = "Waktu tidur selesai: Pemutaran dihentikan."
            delay(3000)
            _downloadStatus.value = null
        }
    }

    fun pauseMedia() {
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.pause()
            }
            _isPlaying.value = false
            videoMediaPlayer?.let { vmp ->
                if (vmp.isPlaying) vmp.pause()
            }
            _isVideoPlaying.value = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Database Flows
    val allTracks: StateFlow<List<MediaTrack>>
    val allPlaylists: StateFlow<List<Playlist>>
    val allPresets: StateFlow<List<EqualizerPreset>>

    // Active Track & Player States
    private val _currentTrack = MutableStateFlow<MediaTrack?>(null)
    val currentTrack: StateFlow<MediaTrack?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0L)
    val playbackProgress: StateFlow<Long> = _playbackProgress.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // Equalizer State
    private val _isEqualizerEnabled = MutableStateFlow(true)
    val isEqualizerEnabled: StateFlow<Boolean> = _isEqualizerEnabled.asStateFlow()

    private val _equalizerBands = MutableStateFlow(listOf(0f, 0f, 0f, 0f, 0f)) // 60Hz, 230Hz, 910Hz, 4kHz, 14kHz
    val equalizerBands: StateFlow<List<Float>> = _equalizerBands.asStateFlow()

    private val _selectedPresetName = MutableStateFlow("Normal")
    val selectedPresetName: StateFlow<String> = _selectedPresetName.asStateFlow()

    // Audio DSP Effects State (Reverb, Pitch, Super Bass, 3D Audio, L-R Audio Balance)
    private val _isEffectsEnabled = MutableStateFlow(true)
    val isEffectsEnabled: StateFlow<Boolean> = _isEffectsEnabled.asStateFlow()

    private val _reverbPreset = MutableStateFlow("Sedang") // Mati, Kecil, Sedang, Aula, Plate
    val reverbPreset: StateFlow<String> = _reverbPreset.asStateFlow()

    private val _pitchSemiTones = MutableStateFlow(0f) // -6f to +6f semitones
    val pitchSemiTones: StateFlow<Float> = _pitchSemiTones.asStateFlow()

    private val _superBassStrength = MutableStateFlow(0.4f) // 0.0f to 1.0f
    val superBassStrength: StateFlow<Float> = _superBassStrength.asStateFlow()

    private val _virtualizer3DStrength = MutableStateFlow(0.4f) // 0.0f to 1.0f
    val virtualizer3DStrength: StateFlow<Float> = _virtualizer3DStrength.asStateFlow()

    private val _lrAudioBalance = MutableStateFlow(0.0f) // -1.0f (Full Left) to +1.0f (Full Right), 0.0f Center
    val lrAudioBalance: StateFlow<Float> = _lrAudioBalance.asStateFlow()

    // AB Repeat State
    private val _abRepeatActive = MutableStateFlow(false)
    val abRepeatActive: StateFlow<Boolean> = _abRepeatActive.asStateFlow()

    private val _pointA = MutableStateFlow<Long?>(null)
    val pointA: StateFlow<Long?> = _pointA.asStateFlow()

    private val _pointB = MutableStateFlow<Long?>(null)
    val pointB: StateFlow<Long?> = _pointB.asStateFlow()

    // Shuffle state
    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    // Repeat Mode State: 0 = Play/Repeat All, 1 = Repeat One
    private val _repeatMode = MutableStateFlow(0)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    fun toggleRepeatMode() {
        _repeatMode.value = if (_repeatMode.value == 0) 1 else 0
    }

    // Volume State (0.0f to 1.0f)
    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    fun setVolume(vol: Float) {
        _volume.value = vol.coerceIn(0f, 1f)
        applyVolumeAndBalance()
    }

    private fun applyVolumeAndBalance() {
        try {
            val baseVol = _volume.value
            val bal = _lrAudioBalance.value
            val left = if (bal <= 0f) baseVol else (baseVol * (1f - bal)).coerceIn(0f, 1f)
            val right = if (bal >= 0f) baseVol else (baseVol * (1f + bal)).coerceIn(0f, 1f)
            mediaPlayer?.setVolume(left, right)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Lyrics State
    data class LyricLine(val timeMs: Long, val text: String)
    private val _lyricsLines = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyricsLines: StateFlow<List<LyricLine>> = _lyricsLines.asStateFlow()

    private val _activeLyricIndex = MutableStateFlow(-1)
    val activeLyricIndex: StateFlow<Int> = _activeLyricIndex.asStateFlow()

    // Download / Offline processing status
    private val _downloadStatus = MutableStateFlow<String?>(null)
    val downloadStatus: StateFlow<String?> = _downloadStatus.asStateFlow()

    // Video Screen States
    var isVideoLocked by mutableStateOf(false)
    var isVideoBgPlaying by mutableStateOf(false)
    var isVideoFullscreen by mutableStateOf(false)
    var isVideoAutoRotate by mutableStateOf(false)

    // Dedicated Independent Video Player States
    private val _currentVideoTrack = MutableStateFlow<MediaTrack?>(null)
    val currentVideoTrack: StateFlow<MediaTrack?> = _currentVideoTrack.asStateFlow()

    private val _isVideoPlaying = MutableStateFlow(false)
    val isVideoPlaying: StateFlow<Boolean> = _isVideoPlaying.asStateFlow()

    private val _videoVolume = MutableStateFlow(0.8f)
    val videoVolume: StateFlow<Float> = _videoVolume.asStateFlow()

    private val _videoRepeatMode = MutableStateFlow(false) // true = loop/repeat video, false = play once
    val videoRepeatMode: StateFlow<Boolean> = _videoRepeatMode.asStateFlow()

    private val _isVideoShuffle = MutableStateFlow(false)
    val isVideoShuffle: StateFlow<Boolean> = _isVideoShuffle.asStateFlow()

    fun toggleVideoShuffle() {
        _isVideoShuffle.value = !_isVideoShuffle.value
    }

    private val _videoPlaybackSpeed = MutableStateFlow(1.0f)
    val videoPlaybackSpeed: StateFlow<Float> = _videoPlaybackSpeed.asStateFlow()

    private val _videoProgress = MutableStateFlow(0L)
    val videoProgress: StateFlow<Long> = _videoProgress.asStateFlow()

    private val _videoDuration = MutableStateFlow(0L)
    val videoDuration: StateFlow<Long> = _videoDuration.asStateFlow()

    private var videoProgressJob: Job? = null

    var videoMediaPlayer: MediaPlayer? = null
        private set

    fun setVideoMediaPlayer(mp: MediaPlayer?) {
        videoMediaPlayer = mp
        if (mp != null) {
            try {
                mp.isLooping = _videoRepeatMode.value
                mp.setVolume(_videoVolume.value, _videoVolume.value)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    try {
                        val params = mp.playbackParams
                        params.speed = _videoPlaybackSpeed.value
                        mp.playbackParams = params
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                if (mp.duration > 0) {
                    _videoDuration.value = mp.duration.toLong()
                }
                startVideoProgressTracker()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            videoProgressJob?.cancel()
        }
    }

    fun startVideoProgressTracker() {
        videoProgressJob?.cancel()
        videoProgressJob = viewModelScope.launch {
            while (true) {
                videoMediaPlayer?.let { mp ->
                    try {
                        if (mp.isPlaying) {
                            _videoProgress.value = mp.currentPosition.toLong()
                            if (mp.duration > 0) {
                                _videoDuration.value = mp.duration.toLong()
                            }
                        }
                    } catch (e: Exception) {
                        // safe catch
                    }
                }
                delay(200)
            }
        }
    }

    // Playlist dialog trigger state
    var trackToAddToPlaylist by mutableStateOf<MediaTrack?>(null)

    // Periodic position updater job
    private var progressJob: Job? = null

    init {
        val database = AppDatabase.getDatabase(application)
        repository = MediaRepository(database.mediaDao())

        allTracks = repository.allTracksFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allPlaylists = repository.allPlaylistsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allPresets = repository.allPresetsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        viewModelScope.launch {
            repository.seedDemoDatabase()
            // Make sure Equalizer settings match initial state
            applyPreset("Normal")
        }
    }

    fun playTrack(track: MediaTrack) {
        if (track.isVideo) {
            playVideoTrack(track)
            activeScreen = "Video"
            return
        }

        progressJob?.cancel()
        _currentTrack.value = track
        _playbackProgress.value = 0L
        _pointA.value = null
        _pointB.value = null
        _abRepeatActive.value = false

        // Parse lyrics LRC
        parseLyrics(track.lyricsLrc)

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        mediaPlayer = MediaPlayer().apply {
            try {
                applyVolumeAndBalance()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                if (track.filePath.startsWith("http")) {
                    setDataSource(track.filePath)
                } else if (track.filePath.startsWith("content://") || track.filePath.startsWith("android.resource://")) {
                    setDataSource(getApplication<Application>(), Uri.parse(track.filePath))
                } else {
                    setDataSource(getApplication<Application>(), Uri.fromFile(File(track.filePath)))
                }
                prepareAsync()
                setOnPreparedListener { mp ->
                    mp.start()
                    // Set current speed & pitch
                    setTempo(_playbackSpeed.value)
                    applyPitch()
                    _isPlaying.value = true
                    setupNativeAudioEffects(mp.audioSessionId)
                    startProgressTracker()
                }
                setOnCompletionListener { mp ->
                    if (_repeatMode.value == 1) {
                        try {
                            seekTo(0)
                            mp.start()
                            _isPlaying.value = true
                        } catch (e: Exception) {
                            playNextTrack()
                        }
                    } else {
                        playNextTrack()
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaViewModel", "Error playing audio file", e)
                _isPlaying.value = false
            }
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.pause()
                _isPlaying.value = false
            } else {
                mp.start()
                _isPlaying.value = true
                startProgressTracker()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let { mp ->
            mp.seekTo(positionMs.toInt())
            _playbackProgress.value = positionMs
            updateActiveLyricIndex(positionMs)
        }
    }

    fun setTempo(speed: Float) {
        _playbackSpeed.value = speed
        mediaPlayer?.let { mp ->
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val params = mp.playbackParams
                    params.speed = speed
                    mp.playbackParams = params
                }
            } catch (e: Exception) {
                Log.e("MediaViewModel", "Cannot adjust speed/tempo on this track", e)
            }
        }
    }

    fun setShuffle(enabled: Boolean) {
        _isShuffle.value = enabled
    }

    // Set A AB Repeat point
    fun setPointA() {
        _pointA.value = _playbackProgress.value
        if (_pointB.value != null && _pointA.value!! >= _pointB.value!!) {
            _pointB.value = null
        }
    }

    // Set B AB Repeat point
    fun setPointB() {
        val current = _playbackProgress.value
        val aVal = _pointA.value
        if (aVal != null && current > aVal) {
            _pointB.value = current
            _abRepeatActive.value = true
        }
    }

    fun clearAbRepeat() {
        _pointA.value = null
        _pointB.value = null
        _abRepeatActive.value = false
    }

    fun playNextTrack() {
        val tracksList = allTracks.value.filter { !it.isVideo }
        if (tracksList.isEmpty()) return

        val current = _currentTrack.value
        var nextIdx = 0
        if (_isShuffle.value) {
            nextIdx = (tracksList.indices).random()
        } else if (current != null) {
            val idx = tracksList.indexOfFirst { it.id == current.id }
            if (idx != -1 && idx < tracksList.size - 1) {
                nextIdx = idx + 1
            }
        }
        playTrack(tracksList[nextIdx])
    }

    fun playPreviousTrack() {
        val tracksList = allTracks.value.filter { !it.isVideo }
        if (tracksList.isEmpty()) return

        val current = _currentTrack.value
        var nextIdx = 0
        if (current != null) {
            val idx = tracksList.indexOfFirst { it.id == current.id }
            if (idx > 0) {
                nextIdx = idx - 1
            } else {
                nextIdx = tracksList.size - 1
            }
        }
        playTrack(tracksList[nextIdx])
    }

    // Dynamic Lyrics Parser
    private fun parseLyrics(lrcText: String?) {
        if (lrcText.isNullOrBlank()) {
            _lyricsLines.value = emptyList()
            return
        }

        val parsedLines = mutableListOf<LyricLine>()
        // Match regex [minute:second.millisecond]
        val pattern = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2})](.*)")
        val simplePattern = Regex("\\[(\\d{2}):(\\d{2})](.*)")

        lrcText.lines().forEach { rawLine ->
            val trimLine = rawLine.trim()
            var matched = false

            pattern.matchEntire(trimLine)?.let { match ->
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val ms = match.groupValues[3].toLongOrNull() ?: 0L
                val text = match.groupValues[4].trim()
                val totalMs = (min * 60 + sec) * 1000 + ms * 10
                parsedLines.add(LyricLine(totalMs, text))
                matched = true
            }

            if (!matched) {
                simplePattern.matchEntire(trimLine)?.let { match ->
                    val min = match.groupValues[1].toLongOrNull() ?: 0L
                    val sec = match.groupValues[2].toLongOrNull() ?: 0L
                    val text = match.groupValues[3].trim()
                    val totalMs = (min * 60 + sec) * 1000
                    parsedLines.add(LyricLine(totalMs, text))
                }
            }
        }

        _lyricsLines.value = parsedLines.sortedBy { it.timeMs }
        _activeLyricIndex.value = -1
    }

    private fun updateActiveLyricIndex(currentProgress: Long) {
        val lines = _lyricsLines.value
        if (lines.isEmpty()) return

        var activeIndex = -1
        for (i in lines.indices) {
            if (currentProgress >= lines[i].timeMs) {
                activeIndex = i
            } else {
                break
            }
        }
        _activeLyricIndex.value = activeIndex
    }

    // Synchronize or generate LRC lyrics on request using Gemini AI API! Uses direct model execution.
    fun requestAiLyricGeneration(track: MediaTrack) {
        viewModelScope.launch {
            _downloadStatus.value = "Menghasilkan lirik dengan Gemini AI..."
            val generated = GeminiLyricsClient.generateSyncedLyrics(track.title, track.artist)
            track.lyricsLrc = generated
            repository.updateTrack(track)
            
            // If currently playing, instantly reload the lyric view state
            if (_currentTrack.value?.id == track.id) {
                _currentTrack.value = track.copy(lyricsLrc = generated)
                parseLyrics(generated)
            }
            _downloadStatus.value = "Lirik AI berhasil dipasang!"
            delay(3000)
            _downloadStatus.value = null
        }
    }

    // Manual metadata tag editor that saves instantly to Room Database and exports Lyric Card to Device Gallery
    fun editTrackMetadata(
        track: MediaTrack, 
        testTagNewTitle: String, 
        newArtist: String, 
        newAlbum: String, 
        newGenre: String, 
        newLyrics: String, 
        newImageUrl: String? = null,
        context: android.content.Context? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            track.title = testTagNewTitle
            track.artist = newArtist
            track.album = newAlbum
            track.genre = newGenre
            track.lyricsLrc = newLyrics
            track.imageUrl = newImageUrl
            repository.updateTrack(track)
            
            withContext(Dispatchers.Main) {
                if (_currentTrack.value?.id == track.id) {
                    _currentTrack.value = track.copy()
                    parseLyrics(newLyrics)
                }
            }
            repository.generateAutoPlaylistsByGenres()

            // Save beautiful lyric visual card to phone gallery if context provided
            if (context != null) {
                try {
                    val width = 1080
                    val height = 1440
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)

                    // Background Gradient
                    val bgPaint = Paint().apply {
                        isAntiAlias = true
                        shader = LinearGradient(
                            0f, 0f, width.toFloat(), height.toFloat(),
                            intArrayOf(0xFF1E1035.toInt(), 0xFF0D0B14.toInt(), 0xFF1B2A4A.toInt()),
                            null,
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

                    // Top Header: NOERAE AUDIO STUDIO
                    val headerPaint = Paint().apply {
                        color = 0xFFD0BCFF.toInt()
                        textSize = 36f
                        isAntiAlias = true
                        isFakeBoldText = true
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText("NOERAE AUDIO STUDIO", width / 2f, 100f, headerPaint)

                    // Track Title
                    val titlePaint = Paint().apply {
                        color = 0xFFFFFFFF.toInt()
                        textSize = 56f
                        isAntiAlias = true
                        isFakeBoldText = true
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText(testTagNewTitle, width / 2f, 190f, titlePaint)

                    // Artist & Album
                    val artistPaint = Paint().apply {
                        color = 0xFFEFB8C8.toInt()
                        textSize = 38f
                        isAntiAlias = true
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText("$newArtist • $newAlbum", width / 2f, 250f, artistPaint)

                    // Line
                    val linePaint = Paint().apply {
                        color = 0x55D0BCFF.toInt()
                        strokeWidth = 3f
                        isAntiAlias = true
                    }
                    canvas.drawLine(100f, 290f, (width - 100).toFloat(), 290f, linePaint)

                    // Lyrics
                    val lyricsPaint = Paint().apply {
                        color = 0xFFECE6F0.toInt()
                        textSize = 32f
                        isAntiAlias = true
                        textAlign = Paint.Align.CENTER
                    }
                    val rawLines = if (newLyrics.isNotBlank()) {
                        newLyrics.lines().map { line ->
                            line.replace(Regex("^\\[\\d{2}:\\d{2}(\\.\\d{2})?\\]"), "").trim()
                        }.filter { it.isNotBlank() }
                    } else {
                        listOf("Tersimpan dari NOERAE Music Player", "Format Audio: ${track.format}")
                    }

                    var currentY = 360f
                    val maxLines = 17
                    for (line in rawLines.take(maxLines)) {
                        if (currentY > height - 100) break
                        canvas.drawText(line, width / 2f, currentY, lyricsPaint)
                        currentY += 56f
                    }

                    val footerPaint = Paint().apply {
                        color = 0x99938F99.toInt()
                        textSize = 26f
                        isAntiAlias = true
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText("Genre: $newGenre • Disimpan ke Galeri HP", width / 2f, height - 50f, footerPaint)

                    val contentValues = ContentValues().apply {
                        val cleanName = testTagNewTitle.replace("[^a-zA-Z0-9_]".toRegex(), "_")
                        put(MediaStore.Images.Media.DISPLAY_NAME, "NOERAE_${cleanName}_${System.currentTimeMillis()}.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NOERAE_Lyrics")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }

                    val imageUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (imageUri != null) {
                        context.contentResolver.openOutputStream(imageUri)?.use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            contentValues.clear()
                            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                            context.contentResolver.update(imageUri, contentValues, null, null)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        _downloadStatus.value = "Tag & Kartu Lirik berhasil disimpan ke Galeri HP!"
                    }
                    delay(3000)
                    withContext(Dispatchers.Main) {
                        _downloadStatus.value = null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        _downloadStatus.value = "Tag berhasil disimpan ke pustaka!"
                    }
                    delay(2500)
                    withContext(Dispatchers.Main) {
                        _downloadStatus.value = null
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    _downloadStatus.value = "Tag berhasil diperbarui!"
                }
                delay(2000)
                withContext(Dispatchers.Main) {
                    _downloadStatus.value = null
                }
            }
        }
    }

    // Background playback of video audio helper
    fun playVideoAudioInBackground(track: MediaTrack) {
        isVideoBgPlaying = true
        activeScreen = "Player"
        playTrack(track)
    }

    fun stopVideoBackgroundPlay() {
        isVideoBgPlaying = false
    }

    // Offline Downloader
    fun downloadTrackOffline(track: MediaTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _downloadStatus.value = "Mengunduh ${track.title}..."
                }

                val client = OkHttpClient()
                val request = Request.Builder().url(track.filePath).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        _downloadStatus.value = "Gagal mengunduh: Kode kesalahan HTTP"
                    }
                    delay(3000)
                    withContext(Dispatchers.Main) { _downloadStatus.value = null }
                    return@launch
                }

                val body = response.body
                if (body == null) {
                    withContext(Dispatchers.Main) {
                        _downloadStatus.value = "Gagal mengunduh: File kosong"
                    }
                    delay(3000)
                    withContext(Dispatchers.Main) { _downloadStatus.value = null }
                    return@launch
                }

                // Write file to internal app storage Music directory
                val targetDir = File(getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MUSIC), "HarmoniDownloads")
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                val extension = if (track.format.isNotBlank() && !track.format.contains(" ")) track.format.lowercase() else (if (track.isVideo) "mp4" else "flac")
                val safeFilename = track.title.replace("[^a-zA-Z0-9_]".toRegex(), "_")
                val targetFile = File(targetDir, "${safeFilename}_offline.$extension")
                
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(targetFile)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()

                // Save reference to Room
                val updatedTrack = track.copy(
                    filePath = targetFile.absolutePath,
                    isDownloaded = true,
                    bitRate = if (track.isVideo) "Offline Video (${track.bitRate})" else "Lossless (${track.bitRate})",
                    sourceUrl = track.filePath
                )
                if (track.id == 0L) {
                    repository.insertTrack(updatedTrack)
                } else {
                    repository.updateTrack(updatedTrack)
                }

                withContext(Dispatchers.Main) {
                    _downloadStatus.value = "Berhasil mengunduh offline!"
                    if (_currentTrack.value?.id == track.id) {
                        _currentTrack.value = updatedTrack
                    }
                }
                delay(3200)
                withContext(Dispatchers.Main) {
                    _downloadStatus.value = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _downloadStatus.value = "Kesalahan unduhan: / ${e.localizedMessage}"
                }
                delay(3000)
                withContext(Dispatchers.Main) { _downloadStatus.value = null }
            }
        }
    }

    // Playlists control
    fun addNewPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(Playlist(name = name))
        }
    }

    fun createPlaylistAndAddTrack(name: String, track: MediaTrack) {
        viewModelScope.launch {
            val playlistId = repository.createPlaylist(Playlist(name = name))
            repository.addTrackToPlaylist(playlistId, track.id)
            trackToAddToPlaylist = null
        }
    }

    fun removePlaylist(id: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(id)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, trackId)
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun generateAutoPlaylists() {
        viewModelScope.launch {
            repository.generateAutoPlaylistsByGenres()
        }
    }

    fun getTracksForPlaylistFlow(playlistId: Long): kotlinx.coroutines.flow.Flow<List<com.example.data.MediaTrack>> {
        return repository.getTracksForPlaylistFlow(playlistId)
    }

    // Storage Scanner triggering
    fun autoScanMusicFolders() {
        viewModelScope.launch {
            _downloadStatus.value = "Memindai pustaka & berkas perangkat..."
            
            // 1. Scan everything via Android MediaStore (standard, secure, scoped storage proof!)
            try {
                repository.scanMediaStore(getApplication())
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Walk standard directories as fallback
            try {
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                val internalMusic = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.absolutePath

                repository.scanLocalDirectory(musicDir)
                repository.scanLocalDirectory(downloadDir)
                if (internalMusic != null) {
                    repository.scanLocalDirectory(internalMusic)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            _downloadStatus.value = "Pemindaian selesai!"
            delay(3000)
            _downloadStatus.value = null
        }
    }

    // Equalizer & DSP Effects Controls
    fun setEqualizerEnabled(enabled: Boolean) {
        _isEqualizerEnabled.value = enabled
        try {
            nativeEq?.enabled = enabled
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setEffectsEnabled(enabled: Boolean) {
        _isEffectsEnabled.value = enabled
        try {
            nativeBassBoost?.enabled = enabled
            nativeVirtualizer?.enabled = enabled
            nativeReverb?.enabled = enabled && _reverbPreset.value != "Mati"
            applyPitch()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setReverbPreset(preset: String) {
        _reverbPreset.value = preset
        try {
            nativeReverb?.let { reverb ->
                reverb.enabled = _isEffectsEnabled.value && preset != "Mati"
                applyReverbPresetInternal(reverb, preset)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyReverbPresetInternal(reverb: PresetReverb, preset: String) {
        try {
            when (preset) {
                "Kecil" -> reverb.preset = PresetReverb.PRESET_SMALLROOM
                "Sedang" -> reverb.preset = PresetReverb.PRESET_MEDIUMROOM
                "Aula" -> reverb.preset = PresetReverb.PRESET_LARGEROOM
                "Plate" -> reverb.preset = PresetReverb.PRESET_PLATE
                else -> reverb.preset = PresetReverb.PRESET_NONE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setPitchSemiTones(semitones: Float) {
        _pitchSemiTones.value = semitones.coerceIn(-6f, 6f)
        applyPitch()
    }

    private fun applyPitch() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mediaPlayer?.let { mp ->
                    val params = mp.playbackParams
                    val factor = if (_isEffectsEnabled.value) {
                        Math.pow(2.0, (_pitchSemiTones.value / 12.0).toDouble()).toFloat()
                    } else 1.0f
                    params.pitch = factor.coerceIn(0.5f, 2.0f)
                    mp.playbackParams = params
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setSuperBassStrength(strength: Float) {
        _superBassStrength.value = strength.coerceIn(0f, 1f)
        try {
            nativeBassBoost?.let { bb ->
                bb.enabled = _isEffectsEnabled.value
                val mB = (_superBassStrength.value * 1000).toInt().toShort()
                bb.setStrength(mB)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setVirtualizer3DStrength(strength: Float) {
        _virtualizer3DStrength.value = strength.coerceIn(0f, 1f)
        try {
            nativeVirtualizer?.let { virt ->
                virt.enabled = _isEffectsEnabled.value
                val mB = (_virtualizer3DStrength.value * 1000).toInt().toShort()
                virt.setStrength(mB)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setLrAudioBalance(balance: Float) {
        _lrAudioBalance.value = balance.coerceIn(-1.0f, 1.0f)
        applyVolumeAndBalance()
    }

    private fun setupNativeAudioEffects(audioSessionId: Int) {
        try {
            if (audioSessionId != 0) {
                // Native Equalizer
                try {
                    nativeEq?.release()
                    nativeEq = Equalizer(0, audioSessionId).apply {
                        enabled = _isEqualizerEnabled.value
                    }
                    _equalizerBands.value.forEachIndexed { index, gainDb ->
                        setNativeBandLevel(index, gainDb)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Native Bass Boost
                try {
                    nativeBassBoost?.release()
                    nativeBassBoost = BassBoost(0, audioSessionId).apply {
                        enabled = _isEffectsEnabled.value
                        setStrength((_superBassStrength.value * 1000).toInt().toShort())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Native 3D Virtualizer
                try {
                    nativeVirtualizer?.release()
                    nativeVirtualizer = Virtualizer(0, audioSessionId).apply {
                        enabled = _isEffectsEnabled.value
                        setStrength((_virtualizer3DStrength.value * 1000).toInt().toShort())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Native Reverb
                try {
                    nativeReverb?.release()
                    nativeReverb = PresetReverb(0, audioSessionId).apply {
                        enabled = _isEffectsEnabled.value && _reverbPreset.value != "Mati"
                        applyReverbPresetInternal(this, _reverbPreset.value)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                applyPitch()
                applyVolumeAndBalance()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun applyPreset(presetName: String) {
        _selectedPresetName.value = presetName
        viewModelScope.launch {
            val preset = repository.getPreset(presetName)
            if (preset != null) {
                val bands = listOf(preset.band60Hz, preset.band230Hz, preset.band910Hz, preset.band4kHz, preset.band14kHz)
                _equalizerBands.value = bands
                bands.forEachIndexed { idx, levelDb ->
                    setNativeBandLevel(idx, levelDb)
                }
            }
        }
    }

    fun updateEqualizerBand(index: Int, levelDb: Float) {
        val current = _equalizerBands.value.toMutableList()
        if (index in current.indices) {
            current[index] = levelDb
            _equalizerBands.value = current
            _selectedPresetName.value = "Kustom"
            setNativeBandLevel(index, levelDb)
        }
    }

    private fun setNativeBandLevel(bandIdx: Int, levelDb: Float) {
        nativeEq?.let { eq ->
            try {
                if (bandIdx < eq.numberOfBands) {
                    val bandRange = eq.bandLevelRange
                    val minLevel = bandRange[0] // e.g. -1500 millibels (-15dB)
                    val maxLevel = bandRange[1] // e.g. 1500 millibels (15dB)
                    val mB = (levelDb * 100).toInt().coerceIn(minLevel.toInt(), maxLevel.toInt())
                    eq.setBandLevel(bandIdx.toShort(), mB.toShort())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isPlaying.value) {
                mediaPlayer?.let { mp ->
                    try {
                        if (mp.isPlaying) {
                            val curPos = mp.currentPosition.toLong()
                            _playbackProgress.value = curPos
                            updateActiveLyricIndex(curPos)

                            // AB Repeat check
                            val a = _pointA.value
                            val b = _pointB.value
                            if (_abRepeatActive.value && a != null && b != null) {
                                if (curPos >= b) {
                                    mp.seekTo(a.toInt())
                                    _playbackProgress.value = a
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                delay(100)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        videoProgressJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            videoMediaPlayer?.stop()
            videoMediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            nativeEq?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            nativeBassBoost?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            nativeVirtualizer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            nativeReverb?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun scanCustomFolder(path: String) {
        viewModelScope.launch {
            _downloadStatus.value = "Memindai folder: $path"
            val file = File(path)
            if (file.exists() && file.isDirectory) {
                repository.scanLocalDirectory(path)
                _downloadStatus.value = "Hasil pindai folder berhasil dimuat!"
            } else {
                _downloadStatus.value = "Kesalahan: Folder tidak ditemukan!"
            }
            delay(3000)
            _downloadStatus.value = null
        }
    }

    fun addAndGenerateOnlineTrack(title: String, artist: String, imageUrl: String?, isVideo: Boolean, streamUrl: String) {
        viewModelScope.launch {
            _downloadStatus.value = "Membuat lirik karaoke otomatis via Gemini AI..."
            
            // Try to generate lrc via Gemini
            val generatedLrc = try {
                GeminiLyricsClient.generateSyncedLyrics(title, artist)
            } catch (e: Exception) {
                "[00:00.00]Menyiapkan karaoke untuk $title\n[00:05.00]Gagal generate lirik via AI, silakan edit manual"
            }

            val newTrack = MediaTrack(
                title = title,
                artist = artist,
                album = "Karaoke Online",
                genre = "Karaoke / Online",
                filePath = streamUrl,
                isVideo = isVideo,
                duration = 210000L,
                format = if (isVideo) "MP4" else "FLAC Stream",
                sampleRate = "48.0 kHz",
                bitRate = "Kualitas Studio AI",
                isDownloaded = false,
                lyricsLrc = generatedLrc,
                imageUrl = imageUrl
            )

            // Insert into Database
            repository.insertTrack(newTrack)
            
            _downloadStatus.value = "Lirik Karaoke & Lagu siap dinyanyikan!"
            delay(2200)
            _downloadStatus.value = null

            // Instantly play
            playTrack(newTrack)
        }
    }

    // --- NEW: Dedicated Video Controls and SAF Importers ---

    fun playVideoTrack(track: MediaTrack) {
        _currentVideoTrack.value = track
        _isVideoPlaying.value = true
        _videoProgress.value = 0L
    }

    fun toggleVideoPlayPause() {
        _isVideoPlaying.value = !_isVideoPlaying.value
    }

    fun setVideoVolume(vol: Float) {
        _videoVolume.value = vol.coerceIn(0f, 1f)
        videoMediaPlayer?.let { mp ->
            try {
                mp.setVolume(vol, vol)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleVideoRepeatMode() {
        _videoRepeatMode.value = !_videoRepeatMode.value
        videoMediaPlayer?.let { mp ->
            try {
                mp.isLooping = _videoRepeatMode.value
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setVideoPlaybackSpeed(speed: Float) {
        _videoPlaybackSpeed.value = speed
        videoMediaPlayer?.let { mp ->
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val params = mp.playbackParams
                    params.speed = speed
                    mp.playbackParams = params
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun seekVideoTo(positionMs: Long) {
        videoMediaPlayer?.let { mp ->
            try {
                mp.seekTo(positionMs.toInt())
                _videoProgress.value = positionMs
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun seekVideoRelative(deltaMs: Long) {
        videoMediaPlayer?.let { mp ->
            try {
                val cur = mp.currentPosition.toLong()
                val dur = if (mp.duration > 0) mp.duration.toLong() else _videoDuration.value
                val target = (cur + deltaMs).coerceIn(0L, dur.coerceAtLeast(0L))
                mp.seekTo(target.toInt())
                _videoProgress.value = target
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playNextVideo() {
        val videosList = allTracks.value.filter { it.isVideo }
        if (videosList.isEmpty()) return

        val current = _currentVideoTrack.value
        var nextIdx = 0
        if (_isVideoShuffle.value && videosList.size > 1) {
            val validIndices = videosList.indices.filter { idx -> current == null || videosList[idx].id != current.id }
            nextIdx = if (validIndices.isNotEmpty()) validIndices.random() else (videosList.indices).random()
        } else if (current != null) {
            val idx = videosList.indexOfFirst { it.id == current.id }
            if (idx != -1 && idx < videosList.size - 1) {
                nextIdx = idx + 1
            } else {
                nextIdx = 0 // loop to first video
            }
        }
        playVideoTrack(videosList[nextIdx])
    }

    fun playPreviousVideo() {
        val videosList = allTracks.value.filter { it.isVideo }
        if (videosList.isEmpty()) return

        val current = _currentVideoTrack.value
        var nextIdx = 0
        if (current != null) {
            val idx = videosList.indexOfFirst { it.id == current.id }
            if (idx != -1 && idx > 0) {
                nextIdx = idx - 1
            } else {
                nextIdx = videosList.size - 1 // loop to last video
            }
        }
        playVideoTrack(videosList[nextIdx])
    }

    fun importAndPlaySingleUri(context: android.content.Context, uri: Uri) {
        viewModelScope.launch {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _downloadStatus.value = "Memuat berkas pilihan..."
            val displayName = getFileNameFromUri(context, uri) ?: "Berkas Lokal Terpilih"
            val ext = displayName.substringAfterLast('.', "MP3").uppercase()
            val cleanTitle = displayName.substringBeforeLast('.').replace("_", " ").replace("-", " ")
            val isVideo = ext in listOf("MP4", "MKV", "AVI", "3GP")

            val mediaTrack = MediaTrack(
                title = cleanTitle,
                artist = "Penyimpanan Utama",
                album = "Berkas Terpilih",
                genre = if (isVideo) "Video Pilihan" else "Musik Pilihan",
                filePath = uri.toString(),
                isVideo = isVideo,
                duration = 180000L,
                format = ext,
                isDownloaded = true,
                lyricsLrc = "[00:00.00]Lirik lokal untuk $cleanTitle\n[00:10.00]Sedang diputar dari berkas pilihan Anda."
            )
            val trackId = repository.insertTrack(mediaTrack)
            val savedTrack = mediaTrack.copy(id = trackId)

            // Auto refresh state
            _downloadStatus.value = "Berhasil memuat berkas lokal!"
            delay(1500)
            _downloadStatus.value = null

            if (isVideo) {
                playVideoTrack(savedTrack)
                activeScreen = "Video"
            } else {
                playTrack(savedTrack)
                activeScreen = "Player"
            }
        }
    }

    fun importFolderUri(context: android.content.Context, treeUri: Uri) {
        viewModelScope.launch {
            _downloadStatus.value = "Memindai folder pilihan..."
            val tracksBefore = repository.allTracksFlow.firstOrNull() ?: emptyList()

            try {
                repository.scanDocumentTree(context, treeUri)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val tracksAfter = repository.allTracksFlow.firstOrNull() ?: emptyList()
            val newTracks = tracksAfter.filter { track -> tracksBefore.none { it.filePath == track.filePath } }

            _downloadStatus.value = "Pemindaian selesai! Berhasil memuat ${newTracks.size} berkas."
            delay(2500)
            _downloadStatus.value = null

            if (newTracks.isNotEmpty()) {
                val first = newTracks.first()
                if (first.isVideo) {
                    playVideoTrack(first)
                    activeScreen = "Video"
                } else {
                    // Filter the scanned music to set queue
                    val scannedMusic = newTracks.filter { !it.isVideo }
                    if (scannedMusic.isNotEmpty()) {
                        playTrack(scannedMusic.first())
                    } else {
                        playVideoTrack(first)
                    }
                    activeScreen = if (first.isVideo) "Video" else "Player"
                }
            }
        }
    }

    fun deleteTrack(track: MediaTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // If it's currently playing audio
                if (_currentTrack.value?.id == track.id) {
                    withContext(Dispatchers.Main) {
                        try {
                            mediaPlayer?.pause()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        _isPlaying.value = false
                        _currentTrack.value = null
                    }
                }
                // If it's currently playing video
                if (_currentVideoTrack.value?.id == track.id) {
                    withContext(Dispatchers.Main) {
                        try {
                            videoMediaPlayer?.pause()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        _isVideoPlaying.value = false
                        _currentVideoTrack.value = null
                    }
                }
                
                repository.deleteTrackById(track.id)
                withContext(Dispatchers.Main) {
                    _downloadStatus.value = "Berkas \"${track.title}\" berhasil dihapus dari pustaka"
                }
                delay(2500)
                withContext(Dispatchers.Main) {
                    _downloadStatus.value = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getFileNameFromUri(context: android.content.Context, uri: Uri): String? {
        var name: String? = null
        try {
            val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return name ?: uri.lastPathSegment
    }
}
