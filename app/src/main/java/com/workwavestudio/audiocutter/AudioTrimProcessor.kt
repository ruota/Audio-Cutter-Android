package com.workwavestudio.audiocutter

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class AudioTrimProcessor {

    fun trimAudio(
        context: Context,
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        format: OutputFormat,
        quality: QualityPreset,
        speed: Float
    ): Uri {
        require(endMs > startMs) { "End time must be greater than start time" }

        val resolver = context.contentResolver
        val inputCopy = copyToCache(resolver.openInputStream(sourceUri), context.cacheDir)
        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val outputFile = File(outputDir, "clip_${System.currentTimeMillis()}.${format.extension}")

        val durationSec = max(0.1, (endMs - startMs).toDouble() / 1000.0)
        val startSec = max(0.0, startMs.toDouble() / 1000.0)
        val atempoFilter = buildAtempoFilters(speed)

        val cmdParts = buildList {
            addAll(listOf("-ss", startSec.format2()))
            addAll(listOf("-t", durationSec.format2()))
            addAll(listOf("-i", inputCopy.absolutePath))
            add("-vn")
            if (atempoFilter.isNotEmpty()) {
                addAll(listOf("-filter:a", atempoFilter))
            }
            addAll(codecArgs(format, quality))
            add("-y")
            add(outputFile.absolutePath)
        }

        val session = FFmpegKit.execute(cmdParts.joinToString(" ") { it.quoteIfNeeded() })
        inputCopy.delete()

        if (!ReturnCode.isSuccess(session.returnCode)) {
            outputFile.delete()
            throw IllegalStateException("Trim failed: ${session.output ?: session.failStackTrace}")
        }
        if (!outputFile.exists()) {
            throw IllegalStateException("Output file not created")
        }

        return Uri.fromFile(outputFile)
    }

    fun generateWaveform(
        context: Context,
        sourceUri: Uri,
        buckets: Int = 160
    ): List<Float> {
        val resolver = context.contentResolver
        val inputCopy = copyToCache(resolver.openInputStream(sourceUri), context.cacheDir)
        val pcmFile = File.createTempFile("waveform_", ".pcm", context.cacheDir)

        val cmd = listOf(
            "-i", inputCopy.absolutePath,
            "-ac", "1",
            "-ar", "8000",
            "-f", "s16le",
            "-y", pcmFile.absolutePath
        )
        val session = FFmpegKit.execute(cmd.joinToString(" ") { it.quoteIfNeeded() })
        inputCopy.delete()
        if (!ReturnCode.isSuccess(session.returnCode)) {
            pcmFile.delete()
            throw IllegalStateException("Waveform failed: ${session.output ?: session.failStackTrace}")
        }

        val amplitudes = pcmFile.readBytes()
            .asSequence()
            .chunked(2)
            .map { bytes ->
                if (bytes.size < 2) 0 else {
                    val value = (bytes[1].toInt() shl 8) or (bytes[0].toInt() and 0xFF)
                    value.toShort()
                }
            }
            .map { abs(it.toFloat()) / Short.MAX_VALUE }
            .toList()

        pcmFile.delete()

        if (amplitudes.isEmpty()) return emptyList()

        val step = max(1, amplitudes.size / buckets)
        val downsampled = buildList {
            var i = 0
            while (i < amplitudes.size) {
                val slice = amplitudes.subList(i, kotlin.math.min(i + step, amplitudes.size))
                val avg = slice.average().toFloat().coerceIn(0f, 1f)
                add(avg)
                if (size >= buckets) break
                i += step
            }
        }
        return downsampled
    }

    private fun codecArgs(format: OutputFormat, quality: QualityPreset): List<String> {
        val bitrate = "${quality.bitrateKbps}k"
        return when (format) {
            OutputFormat.MP3 -> listOf("-c:a", "libmp3lame", "-b:a", bitrate)
            OutputFormat.M4A -> listOf("-c:a", "aac", "-b:a", bitrate, "-movflags", "+faststart")
            OutputFormat.OGG -> listOf("-c:a", "libvorbis", "-b:a", bitrate)
            OutputFormat.FLAC -> listOf("-c:a", "flac")
            OutputFormat.WAV -> listOf("-c:a", "pcm_s16le")
        }
    }

    private fun copyToCache(inputStream: InputStream?, cacheDir: File): File {
        requireNotNull(inputStream) { "Cannot open source file." }
        val cacheFile = File.createTempFile("audio_src_", ".tmp", cacheDir)
        FileOutputStream(cacheFile).use { out ->
            inputStream.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                }
            }
        }
        return cacheFile
    }

    private fun Double.format2(): String = String.format(Locale.US, "%.3f", this)
    private fun String.quoteIfNeeded(): String = if (contains(" ")) "\"$this\"" else this

    private fun buildAtempoFilters(speed: Float): String {
        val clamped = speed.coerceIn(0.1f, 4.0f).toDouble()
        if (kotlin.math.abs(clamped - 1.0) < 0.01) return ""
        val filters = mutableListOf<Double>()
        var factor = clamped
        while (factor > 2.0) {
            filters.add(2.0)
            factor /= 2.0
        }
        while (factor < 0.5) {
            filters.add(0.5)
            factor /= 0.5
        }
        filters.add(factor)
        return filters.joinToString(",") { f -> "atempo=${String.format(Locale.US, "%.2f", f)}" }
    }
}
