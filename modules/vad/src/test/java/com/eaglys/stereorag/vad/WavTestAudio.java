package com.eaglys.stereorag.vad;

import com.eaglys.stereorag.common.AudioUtils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Loads WAV files for tests using the JDK's built-in {@link AudioSystem}. No custom audio
 * decoding logic exists here or in production code.
 */
final class WavTestAudio {

    private WavTestAudio() {
    }

    /** Loads a mono, 16-bit PCM WAV file from the test classpath as normalized float samples. */
    static float[] loadMonoPcm16(String classpathResource) {
        try (InputStream resource = WavTestAudio.class.getResourceAsStream(classpathResource)) {
            if (resource == null) {
                throw new IOException("Test audio resource not found: " + classpathResource);
            }
            try (AudioInputStream audioIn = AudioSystem.getAudioInputStream(new BufferedInputStream(resource))) {
                AudioFormat format = audioIn.getFormat();
                if (format.getChannels() != 1
                        || format.getSampleSizeInBits() != 16
                        || format.isBigEndian()
                        || format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
                    throw new IllegalArgumentException(
                            "Expected mono 16-bit little-endian PCM, got: " + format);
                }
                return AudioUtils.pcm16ToFloat(audioIn.readAllBytes());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (UnsupportedAudioFileException e) {
            throw new IllegalArgumentException("Unsupported audio file: " + classpathResource, e);
        }
    }
}
