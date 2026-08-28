package com.eaglys.stereorag.common;

/** Conversions between PCM16 audio bytes and normalized float32 samples. */
public final class AudioUtils {

    private AudioUtils() {
    }

    /** Converts little-endian 16-bit PCM bytes into samples normalized to [-1, 1]. */
    public static float[] pcm16ToFloat(byte[] pcm16LittleEndian) {
        int sampleCount = pcm16LittleEndian.length / 2;
        float[] samples = new float[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            short sample = (short) ((pcm16LittleEndian[2 * i] & 0xFF)
                    | (pcm16LittleEndian[2 * i + 1] << 8));
            samples[i] = sample / 32768f;
        }
        return samples;
    }
}
