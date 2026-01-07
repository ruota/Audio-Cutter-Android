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
    @Volatile
    private var cachedEncoders: Set<String>? = null

    fun supportedFormats(): List<OutputFormat> {
        val encoders = getEncoders()
        val mp3Supported = hasEncoder(encoders, MP3_ENCODERS) || LameMp3Encoder.isAvailable()
        if (encoders.isEmpty()) {
            return buildList {
                add(OutputFormat.M4A)
                if (mp3Supported) add(OutputFormat.MP3)
                add(OutputFormat.WAV)
            }
        }
        val formats = buildList {
            if (hasEncoder(encoders, AAC_ENCODERS)) add(OutputFormat.M4A)
            if (mp3Supported) add(OutputFormat.MP3)
            if (hasEncoder(encoders, VORBIS_ENCODERS)) add(OutputFormat.OGG)
            if (hasEncoder(encoders, FLAC_ENCODERS)) add(OutputFormat.FLAC)
            if (hasEncoder(encoders, PCM_ENCODERS)) add(OutputFormat.WAV)
        }
        return if (formats.isNotEmpty()) formats else listOf(OutputFormat.M4A, OutputFormat.WAV)
    }

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
        val encoders = getEncoders()
        if (format == OutputFormat.MP3 &&
            LameMp3Encoder.isAvailable() &&
            !hasEncoder(encoders, MP3_ENCODERS)
        ) {
            return trimMp3WithLame(context, sourceUri, startMs, endMs, quality, speed)
        }
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

    private fun trimMp3WithLame(
        context: Context,
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        quality: QualityPreset,
        speed: Float
    ): Uri {
        val resolver = context.contentResolver
        val inputCopy = copyToCache(resolver.openInputStream(sourceUri), context.cacheDir)
        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val outputFile = File(outputDir, "clip_${System.currentTimeMillis()}.mp3")
        val pcmFile = File.createTempFile("audio_pcm_", ".pcm", context.cacheDir)

        val durationSec = max(0.1, (endMs - startMs).toDouble() / 1000.0)
        val startSec = max(0.0, startMs.toDouble() / 1000.0)
        val atempoFilter = buildAtempoFilters(speed)
        val sampleRate = 44100
        val channels = 2

        val cmdParts = buildList {
            addAll(listOf("-ss", startSec.format2()))
            addAll(listOf("-t", durationSec.format2()))
            addAll(listOf("-i", inputCopy.absolutePath))
            add("-vn")
            if (atempoFilter.isNotEmpty()) {
                addAll(listOf("-filter:a", atempoFilter))
            }
            addAll(listOf("-ac", channels.toString(), "-ar", sampleRate.toString()))
            addAll(listOf("-f", "s16le"))
            add("-y")
            add(pcmFile.absolutePath)
        }

        val session = FFmpegKit.execute(cmdParts.joinToString(" ") { it.quoteIfNeeded() })
        inputCopy.delete()

        if (!ReturnCode.isSuccess(session.returnCode)) {
            pcmFile.delete()
            outputFile.delete()
            throw IllegalStateException("Trim failed: ${session.output ?: session.failStackTrace}")
        }

        val encoded = LameMp3Encoder.encodePcmToMp3(
            pcmPath = pcmFile.absolutePath,
            mp3Path = outputFile.absolutePath,
            sampleRate = sampleRate,
            channels = channels,
            bitrateKbps = quality.bitrateKbps
        )
        pcmFile.delete()

        if (!encoded) {
            outputFile.delete()
            throw IllegalStateException("MP3 encode failed")
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
        val encoders = getEncoders()
        return when (format) {
            OutputFormat.MP3 -> {
                val encoder = pickEncoder(encoders, MP3_ENCODERS)
                    ?: throw IllegalStateException("MP3 encoder not available in this build")
                listOf("-c:a", encoder, "-b:a", bitrate)
            }
            OutputFormat.M4A -> {
                val encoder = pickEncoder(encoders, AAC_ENCODERS)
                    ?: throw IllegalStateException("AAC encoder not available in this build")
                listOf("-c:a", encoder, "-b:a", bitrate, "-movflags", "+faststart")
            }
            OutputFormat.OGG -> {
                val encoder = pickEncoder(encoders, VORBIS_ENCODERS)
                    ?: throw IllegalStateException("Vorbis encoder not available in this build")
                val args = mutableListOf("-c:a", encoder, "-b:a", bitrate)
                if (encoder == "vorbis") {
                    args.addAll(listOf("-strict", "-2"))
                }
                args
            }
            OutputFormat.FLAC -> {
                val encoder = pickEncoder(encoders, FLAC_ENCODERS)
                    ?: throw IllegalStateException("FLAC encoder not available in this build")
                listOf("-c:a", encoder)
            }
            OutputFormat.WAV -> {
                val encoder = pickEncoder(encoders, PCM_ENCODERS)
                    ?: throw IllegalStateException("PCM encoder not available in this build")
                listOf("-c:a", encoder)
            }
        }
    }

    private fun getEncoders(): Set<String> {
        cachedEncoders?.let { return it }
        val encoders = synchronized(this) {
            cachedEncoders?.let { return it }
            val session = FFmpegKit.execute("-hide_banner -encoders")
            val output = session.output.orEmpty()
            parseEncoders(output).also { cachedEncoders = it }
        }
        return encoders
    }

    private fun parseEncoders(output: String): Set<String> {
        return output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("A") }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"))
                if (parts.size < 2) {
                    null
                } else {
                    parts[1].takeIf { it != "=" }
                }
            }
            .map { it.lowercase(Locale.US) }
            .toSet()
    }

    private fun hasEncoder(encoders: Set<String>, candidates: List<String>): Boolean {
        return candidates.any { encoders.contains(it) }
    }

    private fun pickEncoder(encoders: Set<String>, candidates: List<String>): String? {
        if (candidates.isEmpty()) return null
        if (encoders.isEmpty()) return candidates.first()
        return candidates.firstOrNull { encoders.contains(it) }
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

    private companion object {
        val MP3_ENCODERS = listOf("libmp3lame", "libshine", "mp3")
        val AAC_ENCODERS = listOf("aac", "libfdk_aac", "libfaac")
        val VORBIS_ENCODERS = listOf("libvorbis", "vorbis")
        val FLAC_ENCODERS = listOf("flac")
        val PCM_ENCODERS = listOf(
            "pcm_s16le",
            "pcm_s16be",
            "pcm_s24le",
            "pcm_s24be",
            "pcm_f32le",
            "pcm_f32be"
        )
    }
}
