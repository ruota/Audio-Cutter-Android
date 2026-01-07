package com.workwavestudio.audiocutter

object LameMp3Encoder {
    private const val LIB_NAME = "lamejni"
    @Volatile
    private var cachedAvailable: Boolean? = null

    fun isAvailable(): Boolean {
        cachedAvailable?.let { return it }
        val available = synchronized(this) {
            cachedAvailable?.let { return it }
            val loaded = runCatching {
                System.loadLibrary(LIB_NAME)
            }.isSuccess
            val result = loaded && runCatching { nativeIsAvailable() }.getOrDefault(false)
            cachedAvailable = result
            result
        }
        return available
    }

    fun encodePcmToMp3(
        pcmPath: String,
        mp3Path: String,
        sampleRate: Int,
        channels: Int,
        bitrateKbps: Int
    ): Boolean {
        if (!isAvailable()) return false
        return runCatching {
            nativeEncodePcmToMp3(pcmPath, mp3Path, sampleRate, channels, bitrateKbps)
        }.getOrDefault(false)
    }

    private external fun nativeIsAvailable(): Boolean

    private external fun nativeEncodePcmToMp3(
        pcmPath: String,
        mp3Path: String,
        sampleRate: Int,
        channels: Int,
        bitrateKbps: Int
    ): Boolean
}
