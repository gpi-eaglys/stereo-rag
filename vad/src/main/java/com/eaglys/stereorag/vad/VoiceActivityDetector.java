package com.eaglys.stereorag.vad;

import com.eaglys.stereorag.common.AudioSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * Labels a mono audio stream as speech or non-speech, one fixed-size chunk at a time. Every
 * input chunk is labeled exactly once. Each {@link AudioSegment} includes its audio samples, not
 * just its timestamps: a live stream discards each chunk after it arrives, so timestamps alone
 * cannot recover the audio later.
 *
 * <p>Streaming is the primary contract. A backend (Silero-based, energy-based, WebRTC, ...)
 * implements only the incremental path. Batch processing of a fully-buffered clip is a default
 * method that calls the incremental method repeatedly. The reverse is not always possible: some
 * batch-oriented methods (global normalization, look-ahead) cannot be decomposed into
 * independent chunks.
 *
 * <p>Not thread-safe. Create one instance per audio stream, or call {@link #reset()} between
 * streams.
 */
public interface VoiceActivityDetector extends AutoCloseable {

    int sampleRate();

    /** Exact number of samples required in each call to {@link #processChunk(float[])}. */
    int chunkSamples();

    /**
     * Processes one chunk of {@link #chunkSamples()} mono samples normalized to [-1, 1].
     *
     * @return segments closed by this chunk. Usually empty. Can contain more than one segment
     *     &mdash; for example, a non-speech chunk removed from the internal buffer in the same
     *     call that also closes a speech segment.
     */
    List<AudioSegment> processChunk(float[] chunk);

    /**
     * Signals the end of the stream. Emits every chunk still held internally: the remaining
     * pre-roll buffer, and any segment still in progress.
     */
    List<AudioSegment> finish();

    /** Clears state; call before processing a new, unrelated stream. */
    void reset();

    /** Convenience batch API: labels every chunk in a fully-buffered clip. */
    default List<AudioSegment> detect(float[] samples) {
        int chunkSamples = chunkSamples();
        List<AudioSegment> segments = new ArrayList<>();
        for (int offset = 0; offset < samples.length; offset += chunkSamples) {
            int length = Math.min(chunkSamples, samples.length - offset);
            float[] chunk = new float[chunkSamples];
            System.arraycopy(samples, offset, chunk, 0, length);
            segments.addAll(processChunk(chunk));
        }
        segments.addAll(finish());
        return segments;
    }

    @Override
    void close();
}
