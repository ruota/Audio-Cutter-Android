#include <jni.h>
#include <cstdio>
#include <vector>

#if defined(LAME_AVAILABLE)
#include <lame.h>
#endif

extern "C" JNIEXPORT jboolean JNICALL
Java_com_workwavestudio_audiocutter_LameMp3Encoder_nativeIsAvailable(
    JNIEnv*,
    jclass
) {
#if defined(LAME_AVAILABLE)
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_workwavestudio_audiocutter_LameMp3Encoder_nativeEncodePcmToMp3(
    JNIEnv* env,
    jclass,
    jstring pcmPath,
    jstring mp3Path,
    jint sampleRate,
    jint channels,
    jint bitrateKbps
) {
#if !defined(LAME_AVAILABLE)
    (void)env;
    (void)pcmPath;
    (void)mp3Path;
    (void)sampleRate;
    (void)channels;
    (void)bitrateKbps;
    return JNI_FALSE;
#else
    const char* pcmPathChars = env->GetStringUTFChars(pcmPath, nullptr);
    const char* mp3PathChars = env->GetStringUTFChars(mp3Path, nullptr);
    if (!pcmPathChars || !mp3PathChars) {
        if (pcmPathChars) env->ReleaseStringUTFChars(pcmPath, pcmPathChars);
        if (mp3PathChars) env->ReleaseStringUTFChars(mp3Path, mp3PathChars);
        return JNI_FALSE;
    }

    FILE* pcmFile = std::fopen(pcmPathChars, "rb");
    FILE* mp3File = std::fopen(mp3PathChars, "wb");
    if (!pcmFile || !mp3File) {
        if (pcmFile) std::fclose(pcmFile);
        if (mp3File) std::fclose(mp3File);
        env->ReleaseStringUTFChars(pcmPath, pcmPathChars);
        env->ReleaseStringUTFChars(mp3Path, mp3PathChars);
        return JNI_FALSE;
    }

    lame_t lame = lame_init();
    if (!lame) {
        std::fclose(pcmFile);
        std::fclose(mp3File);
        env->ReleaseStringUTFChars(pcmPath, pcmPathChars);
        env->ReleaseStringUTFChars(mp3Path, mp3PathChars);
        return JNI_FALSE;
    }

    lame_set_in_samplerate(lame, sampleRate);
    lame_set_num_channels(lame, channels);
    lame_set_brate(lame, bitrateKbps);
    if (lame_init_params(lame) < 0) {
        lame_close(lame);
        std::fclose(pcmFile);
        std::fclose(mp3File);
        env->ReleaseStringUTFChars(pcmPath, pcmPathChars);
        env->ReleaseStringUTFChars(mp3Path, mp3PathChars);
        return JNI_FALSE;
    }

    const int frameSamples = 1152;
    const int safeChannels = channels < 1 ? 1 : channels;
    std::vector<short> pcmBuffer(frameSamples * safeChannels);
    std::vector<unsigned char> mp3Buffer(frameSamples * 5 / 4 + 7200);

    int readFrames = 0;
    while ((readFrames = static_cast<int>(
                std::fread(pcmBuffer.data(), sizeof(short) * safeChannels, frameSamples, pcmFile)
            )) > 0) {
        int encodedBytes = 0;
        if (safeChannels == 1) {
            encodedBytes = lame_encode_buffer(
                lame,
                pcmBuffer.data(),
                pcmBuffer.data(),
                readFrames,
                mp3Buffer.data(),
                static_cast<int>(mp3Buffer.size())
            );
        } else {
            encodedBytes = lame_encode_buffer_interleaved(
                lame,
                pcmBuffer.data(),
                readFrames,
                mp3Buffer.data(),
                static_cast<int>(mp3Buffer.size())
            );
        }
        if (encodedBytes < 0) {
            lame_close(lame);
            std::fclose(pcmFile);
            std::fclose(mp3File);
            env->ReleaseStringUTFChars(pcmPath, pcmPathChars);
            env->ReleaseStringUTFChars(mp3Path, mp3PathChars);
            return JNI_FALSE;
        }
        if (encodedBytes > 0) {
            if (std::fwrite(mp3Buffer.data(), 1, encodedBytes, mp3File) !=
                static_cast<size_t>(encodedBytes)) {
                lame_close(lame);
                std::fclose(pcmFile);
                std::fclose(mp3File);
                env->ReleaseStringUTFChars(pcmPath, pcmPathChars);
                env->ReleaseStringUTFChars(mp3Path, mp3PathChars);
                return JNI_FALSE;
            }
        }
    }

    const int flushBytes = lame_encode_flush(
        lame,
        mp3Buffer.data(),
        static_cast<int>(mp3Buffer.size())
    );
    if (flushBytes > 0) {
        std::fwrite(mp3Buffer.data(), 1, flushBytes, mp3File);
    }

    lame_close(lame);
    std::fclose(pcmFile);
    std::fclose(mp3File);
    env->ReleaseStringUTFChars(pcmPath, pcmPathChars);
    env->ReleaseStringUTFChars(mp3Path, mp3PathChars);
    return JNI_TRUE;
#endif
}
