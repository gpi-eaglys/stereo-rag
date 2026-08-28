package com.eaglys.stereorag.vad;

import com.eaglys.stereorag.common.AudioSegment;
import com.eaglys.stereorag.common.SpeechLabel;
import com.eaglys.stereorag.common.SpeechSegment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Adapts a per-chunk {@link VoiceActivityScorer} into a {@link VoiceActivityDetector} using an
 * OFF / WARMUP / ON / COOLDOWN state machine:
 *
 * <pre>
 * -------|???????*****|?????????-------
 * OFF     warmup  ON   cooldown   OFF
 *        ^^^^^^^^^^^^^^^^^^^^^^^
 *        emitted SPEECH audio (batched per VadConfig#speechFlushChunks)
 * </pre>
 *
 * <p>A single above-threshold chunk starts WARMUP, not ON. Confirming ON requires {@code
 * minWarmupChunks} consecutive hits, so a single chunk of noise is not flagged as speech.
 * Symmetrically, ON drops to OFF only after {@code minCooldownChunks} consecutive misses, so a
 * brief dip does not split one utterance into two segments.
 *
 * <p>Every chunk is labeled exactly once, either as part of a SPEECH segment, or by itself as a
 * one-chunk NON_SPEECH segment when it is no longer needed for pre-roll. Each chunk has a {@code
 * claimed} flag, set when it is added to the pending speech buffer (during WARMUP, or once
 * ON/COOLDOWN). A chunk removed from the ring buffer without being claimed &mdash; from a
 * cancelled WARMUP, or from silence &mdash; is emitted as NON_SPEECH.
 *
 * <p>Once speech is confirmed, {@link VadConfig#speechFlushChunks()} controls how soon its audio
 * is emitted. {@code 1} emits each chunk immediately after confirmation, for lowest latency.
 * {@link VadConfig#FLUSH_ONLY_AT_CLOSE} buffers the whole segment and emits it only when cooldown
 * closes it. Other values batch that many chunks per emission.
 */
public final class WarmupCooldownVoiceActivityDetector implements VoiceActivityDetector {

    private enum State { OFF, WARMUP, ON, COOLDOWN }

    /** {@code claimed} is set once this frame has been added to the pending speech buffer. */
    private static final class Frame {
        final long timeMs;
        final float[] samples;
        boolean claimed;

        Frame(long timeMs, float[] samples) {
            this.timeMs = timeMs;
            this.samples = samples;
        }
    }

    private final VoiceActivityScorer scorer;
    private final VadConfig config;
    private final int sampleRate;
    private final int chunkSamples;
    private final long chunkDurationMs;
    private final int maxRingSize;

    private final Deque<Frame> ring = new ArrayDeque<>();
    private final List<Frame> speech = new ArrayList<>();

    private State state = State.OFF;
    private long elapsedMs;
    private int hitStreak;
    private int missStreak;

    public WarmupCooldownVoiceActivityDetector(int sampleRate, VadConfig config, VoiceActivityScorer scorer) {
        this.sampleRate = sampleRate;
        this.chunkSamples = sampleRate == 16000
                ? SileroVadDetector.CHUNK_SAMPLES_16K
                : SileroVadDetector.CHUNK_SAMPLES_8K;
        this.chunkDurationMs = chunkSamples * 1000L / sampleRate;
        this.config = config;
        this.scorer = scorer;
        this.maxRingSize = config.minWarmupChunks() + config.lhsPadChunks() + config.minCooldownChunks() + 1;
    }

    @Override
    public int sampleRate() {
        return sampleRate;
    }

    @Override
    public int chunkSamples() {
        return chunkSamples;
    }

    @Override
    public List<AudioSegment> processChunk(float[] chunk) {
        Frame frame = new Frame(elapsedMs, chunk);
        elapsedMs += chunkDurationMs;
        float probability = scorer.speechProbability(chunk);

        List<AudioSegment> emitted = new ArrayList<>(probability > config.threshold() ? onHit(frame) : onMiss(frame));

        ring.addLast(frame);
        if (ring.size() > maxRingSize) {
            Frame evicted = ring.removeFirst();
            if (!evicted.claimed) {
                emitted.add(emitNonSpeech(evicted));
            }
        }
        return emitted;
    }

    private List<AudioSegment> onHit(Frame frame) {
        hitStreak++;
        missStreak = 0;
        switch (state) {
            case OFF -> state = State.WARMUP;
            case WARMUP -> {
                if (hitStreak > config.minWarmupChunks()) {
                    state = State.ON;
                    pullPreroll();
                    claim(frame);
                    return flushEarlyBatches();
                }
            }
            case ON, COOLDOWN -> {
                state = State.ON;
                claim(frame);
                return flushEarlyBatches();
            }
        }
        return List.of();
    }

    private List<AudioSegment> onMiss(Frame frame) {
        hitStreak = 0;
        switch (state) {
            case OFF -> {
            }
            case WARMUP -> state = State.OFF;
            case ON -> {
                state = State.COOLDOWN;
                missStreak = 1;
                claim(frame);
                return flushEarlyBatches();
            }
            case COOLDOWN -> {
                if (missStreak >= config.minCooldownChunks()) {
                    state = State.OFF;
                    missStreak = 0;
                    // speech can be empty here if speechFlushChunks already emitted
                    // everything. Closing is then only a state change.
                    return speech.isEmpty() ? List.of() : List.of(closeSegment(speech.size()));
                }
                claim(frame);
                missStreak++;
                return flushEarlyBatches();
            }
        }
        return List.of();
    }

    private void claim(Frame frame) {
        frame.claimed = true;
        speech.add(frame);
    }

    /** Flushes {@code speechFlushChunks}-sized batches for as long as one is available. */
    private List<AudioSegment> flushEarlyBatches() {
        int batchSize = config.speechFlushChunks();
        if (speech.size() < batchSize) {
            return List.of();
        }
        List<AudioSegment> flushed = new ArrayList<>();
        while (speech.size() >= batchSize) {
            flushed.add(closeSegment(batchSize));
        }
        return flushed;
    }

    private void pullPreroll() {
        int pull = Math.min(ring.size(), hitStreak + config.lhsPadChunks() - 1);
        if (pull <= 0) {
            return;
        }
        List<Frame> ordered = new ArrayList<>(ring);
        int fromIndex = ordered.size() - pull;
        // Skip frames already claimed by a previous segment; the ring buffer can still hold them.
        while (fromIndex < ordered.size() && ordered.get(fromIndex).claimed) {
            fromIndex++;
        }
        for (Frame frame : ordered.subList(fromIndex, ordered.size())) {
            claim(frame);
        }
    }

    /** Packs and removes the first {@code count} pending frames into one SPEECH segment. */
    private AudioSegment closeSegment(int count) {
        long startMs = speech.get(0).timeMs;
        long endMs = speech.get(count - 1).timeMs + chunkDurationMs;
        int totalSamples = 0;
        for (int i = 0; i < count; i++) {
            totalSamples += speech.get(i).samples.length;
        }
        float[] samples = new float[totalSamples];
        int position = 0;
        for (int i = 0; i < count; i++) {
            float[] frameSamples = speech.get(i).samples;
            System.arraycopy(frameSamples, 0, samples, position, frameSamples.length);
            position += frameSamples.length;
        }
        speech.subList(0, count).clear();
        return new AudioSegment(new SpeechSegment(startMs, endMs), SpeechLabel.SPEECH, samples);
    }

    private AudioSegment emitNonSpeech(Frame frame) {
        long endMs = frame.timeMs + chunkDurationMs;
        return new AudioSegment(new SpeechSegment(frame.timeMs, endMs), SpeechLabel.NON_SPEECH, frame.samples);
    }

    @Override
    public List<AudioSegment> finish() {
        List<AudioSegment> emitted = new ArrayList<>();
        for (Frame frame : ring) {
            if (!frame.claimed) {
                emitted.add(emitNonSpeech(frame));
            }
        }
        if (state == State.ON || state == State.COOLDOWN) {
            state = State.OFF;
            missStreak = 0;
            if (!speech.isEmpty()) {
                emitted.add(closeSegment(speech.size()));
            }
        }
        ring.clear();
        return emitted;
    }

    @Override
    public void reset() {
        scorer.reset();
        elapsedMs = 0;
        state = State.OFF;
        hitStreak = 0;
        missStreak = 0;
        ring.clear();
        speech.clear();
    }

    @Override
    public void close() {
        scorer.close();
    }
}
