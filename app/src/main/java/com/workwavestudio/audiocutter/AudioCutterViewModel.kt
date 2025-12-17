package com.workwavestudio.audiocutter

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Environment
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class OutputFormat(val extension: String, val label: String) {
    M4A("m4a", "AAC (m4a)"),
    MP3("mp3", "MP3"),
    OGG("ogg", "OGG"),
    FLAC("flac", "FLAC"),
    WAV("wav", "WAV");
}

enum class QualityPreset(val label: String, val bitrateKbps: Int) {
    HIGH("High", 320),
    MEDIUM("Standard", 192),
    LOW("Compact", 128);
}

data class AudioUiState(
    val uri: Uri? = null,
    val fileName: String = "",
    val durationMs: Long = 0L,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val isProcessing: Boolean = false,
    val isPlaying: Boolean = false,
    val message: String? = null,
    val trimmedOutput: Uri? = null,
    val outputFormat: OutputFormat = OutputFormat.MP3,
    val quality: QualityPreset = QualityPreset.HIGH,
    val waveform: List<Float> = emptyList(),
    val playbackPositionMs: Long = 0L,
    val lastSavedPath: String? = null,
    val showFullScreenBanner: Boolean = false,
    val showResultDialog: Boolean = false,
    val speed: Float = 1.0f
)

class AudioCutterViewModel(
    private val trimProcessor: AudioTrimProcessor = AudioTrimProcessor()
) : ViewModel() {

    var uiState by mutableStateOf(AudioUiState())
        private set

    private var mediaPlayer: MediaPlayer? = null
    private var positionJob: Job? = null

    fun onAudioPicked(uri: Uri, context: Context) {
        val resolver = context.contentResolver
        val durationMs = loadDurationMs(resolver, uri)
        val safeDuration = durationMs.coerceAtLeast(1_000L)
        val fileName = resolver.readDisplayName(uri)

        uiState = uiState.copy(
            uri = uri,
            fileName = fileName,
            durationMs = safeDuration,
            startMs = 0L,
            endMs = safeDuration,
            trimmedOutput = null,
            waveform = emptyList(),
            playbackPositionMs = 0L,
            message = "File ready: $fileName",
            speed = 1.0f
        )

        // Carica la forma d'onda in background
        loadWaveform(context, uri)
    }

    fun onRangeChange(range: ClosedFloatingPointRange<Float>) {
        val start = range.start.toLong()
        val end = range.endInclusive.toLong().coerceAtMost(uiState.durationMs)
        uiState = uiState.copy(
            startMs = start,
            endMs = end
        )
    }

    fun trim(context: Context) {
        val audioUri = uiState.uri ?: return
        if (uiState.isProcessing) return
        val start = uiState.startMs
        val end = uiState.endMs
        val format = uiState.outputFormat
        val quality = uiState.quality
        val speed = uiState.speed

        uiState = uiState.copy(isProcessing = true, message = "Trimming...")
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    trimProcessor.trimAudio(context, audioUri, start, end, format, quality, speed)
                }
            }
            result.onSuccess { output ->
                val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                    ?: context.filesDir
                val friendlyPath = "${baseDir.absolutePath}/${output.lastPathSegment ?: output.path}"
                uiState = uiState.copy(
                    isProcessing = false,
                    trimmedOutput = output,
                    playbackPositionMs = uiState.startMs,
                    lastSavedPath = friendlyPath,
                    showFullScreenBanner = true,
                    showResultDialog = false,
                    message = "Saved to $friendlyPath"
                )
            }.onFailure { error ->
                uiState = uiState.copy(
                    isProcessing = false,
                    message = "Error: ${error.localizedMessage}"
                )
            }
        }
    }

    fun updateFormat(format: OutputFormat) {
        uiState = uiState.copy(outputFormat = format)
    }

    fun updateQuality(preset: QualityPreset) {
        uiState = uiState.copy(quality = preset)
    }

    fun updateSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.1f, 4f)
        uiState = uiState.copy(speed = clamped)
        mediaPlayer?.let { player ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                runCatching {
                    val params = player.playbackParams ?: PlaybackParams()
                    player.playbackParams = params.setSpeed(clamped)
                    if (player.isPlaying) player.start()
                }
            }
        }
    }

    fun onFullScreenBannerFinished() {
        uiState = uiState.copy(
            showFullScreenBanner = false,
            showResultDialog = false
        )
    }

    fun dismissResultDialog() {
        uiState = uiState.copy(showResultDialog = false)
    }

    private fun loadWaveform(context: Context, uri: Uri) {
        viewModelScope.launch {
            val wave = runCatching {
                withContext(Dispatchers.IO) {
                    trimProcessor.generateWaveform(context, uri)
                }
            }.getOrElse {
                emptyList()
            }
            if (wave.isEmpty()) {
                uiState = uiState.copy(waveform = wave, message = "Waveform not available for this file")
            } else {
                uiState = uiState.copy(waveform = wave)
            }
        }
    }

    fun togglePlayback(context: Context) {
        val audioUri = uiState.uri ?: return
        if (uiState.isPlaying) {
            stopPlayback()
            return
        }

        viewModelScope.launch {
            stopPlayback()
            try {
                val player = MediaPlayer()
                player.setDataSource(context, audioUri)
                player.setOnCompletionListener { stopPlayback() }
                player.prepare()
                player.seekTo(uiState.startMs.toInt())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    runCatching {
                        val params = player.playbackParams ?: PlaybackParams()
                        player.playbackParams = params.setSpeed(uiState.speed)
                    }
                }
                player.start()

                mediaPlayer = player
                uiState = uiState.copy(
                    isPlaying = true,
                    playbackPositionMs = uiState.startMs,
                    message = "Preview playing"
                )

                positionJob?.cancel()
                positionJob = viewModelScope.launch {
                    while (mediaPlayer != null && mediaPlayer?.isPlaying == true) {
                        uiState = uiState.copy(
                            playbackPositionMs = mediaPlayer?.currentPosition?.toLong() ?: uiState.startMs
                        )
                        delay(100L)
                    }
                }

                val rawPreview = (uiState.endMs - uiState.startMs).toFloat()
                val adjustedPreview = rawPreview / uiState.speed
                val previewLength = adjustedPreview.toLong().coerceAtLeast(300L)
                viewModelScope.launch {
                    delay(previewLength)
                    stopPlayback()
                }
            } catch (error: Exception) {
                stopPlayback()
                uiState = uiState.copy(message = "Cannot play preview: ${error.localizedMessage}")
            }
        }
    }

    fun clearMessage() {
        if (uiState.message != null) {
            uiState = uiState.copy(message = null)
        }
    }

    private fun stopPlayback() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        } finally {
            mediaPlayer?.release()
            mediaPlayer = null
            positionJob?.cancel()
            uiState = uiState.copy(isPlaying = false, playbackPositionMs = uiState.startMs)
        }
    }

    override fun onCleared() {
        stopPlayback()
        super.onCleared()
    }

    private fun loadDurationMs(resolver: ContentResolver, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        val afd = resolver.openAssetFileDescriptor(uri, "r") ?: return 0L
        return try {
            if (afd.length < 0) {
                retriever.setDataSource(afd.fileDescriptor)
            } else {
                retriever.setDataSource(
                    afd.fileDescriptor,
                    afd.startOffset,
                    afd.length
                )
            }
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?: return 0L
            duration.toLong()
        } catch (_: Exception) {
            0L
        } finally {
            retriever.release()
            afd.close()
        }
    }
}

private fun ContentResolver.readDisplayName(uri: Uri): String {
    return runCatching {
        query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1 && cursor.moveToFirst()) cursor.getString(index) else null
            }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()
}
