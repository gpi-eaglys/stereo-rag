package com.eaglys.stereorag.vad;

import com.eaglys.stereorag.common.AudioSegment;
import com.eaglys.stereorag.common.SpeechLabel;
import com.eaglys.stereorag.common.SpeechSegment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VoiceActivityDetectorTest {

    private static final int SAMPLE_RATE = 16000;
    private static final int CHUNK_SAMPLES = SileroVadDetector.CHUNK_SAMPLES_16K;

    @Test
    void confirmedSpeechIsFlankedByNonSpeechCoveringTheWholeStream() {
        // markers: 0,1 = pre-roll silence; 2,3,4 = warmup->confirm (3 consecutive hits);
        // 5,6 = cooldown (2 misses closes it); 7 = trailing silence. minWarmupChunks=2 needs
        // a 3rd hit to confirm. lhsPadChunks=1 pulls one extra chunk of pre-roll. Marker 0 is
        // outside that pull window; it is removed from the ring buffer as its own NON_SPEECH
        // chunk.
        VoiceActivityScorer fake = fakeScorer(0.1f, 0.1f, 0.9f, 0.9f, 0.9f, 0.1f, 0.1f, 0.1f);
        VadConfig config = new VadConfig(0.5f, 2, 2, 1, VadConfig.FLUSH_ONLY_AT_CLOSE);
        List<AudioSegment> segments = detect(fake, config, 8);

        assertEquals(3, segments.size());
        assertSegment(segments.get(0), 0, 32, SpeechLabel.NON_SPEECH, markerAudio(0));
        assertSegment(segments.get(1), 32, 224, SpeechLabel.SPEECH, markerAudio(1, 2, 3, 4, 5, 6));
        assertSegment(segments.get(2), 224, 256, SpeechLabel.NON_SPEECH, markerAudio(7));
    }

    @Test
    void warmupThatNeverConfirmsEmitsEveryChunkAsNonSpeech() {
        VoiceActivityScorer fake = fakeScorer(0.9f, 0.1f, 0.1f, 0.1f);
        VadConfig config = new VadConfig(0.5f, 2, 2, 1, VadConfig.FLUSH_ONLY_AT_CLOSE);
        List<AudioSegment> segments = detect(fake, config, 4);

        assertEquals(4, segments.size());
        assertSegment(segments.get(0), 0, 32, SpeechLabel.NON_SPEECH, markerAudio(0));
        assertSegment(segments.get(1), 32, 64, SpeechLabel.NON_SPEECH, markerAudio(1));
        assertSegment(segments.get(2), 64, 96, SpeechLabel.NON_SPEECH, markerAudio(2));
        assertSegment(segments.get(3), 96, 128, SpeechLabel.NON_SPEECH, markerAudio(3));
    }

    @Test
    void cooldownThatRecoversStaysInOneSegment() {
        // Confirm ON at index 1, drop to COOLDOWN at index 2, recover to ON at index 3 (no
        // segment split), then two sustained misses (4, 5) start a new COOLDOWN that closes
        // at index 6. One segment covers indices 0-5. Index 6 is a trailing NON_SPEECH chunk.
        VoiceActivityScorer fake = fakeScorer(0.9f, 0.9f, 0.1f, 0.9f, 0.1f, 0.1f, 0.1f);
        VadConfig config = new VadConfig(0.5f, 1, 2, 0, VadConfig.FLUSH_ONLY_AT_CLOSE);
        List<AudioSegment> segments = detect(fake, config, 7);

        assertEquals(2, segments.size());
        assertSegment(segments.get(0), 0, 192, SpeechLabel.SPEECH, markerAudio(0, 1, 2, 3, 4, 5));
        assertSegment(segments.get(1), 192, 224, SpeechLabel.NON_SPEECH, markerAudio(6));
    }

    @Test
    void speechFlushChunksOneStreamsEveryConfirmedChunkImmediately() {
        // Same scenario as confirmedSpeechIsFlankedByNonSpeechCoveringTheWholeStream, but with
        // speechFlushChunks=1. The pre-roll pulled at confirm time (markers 1-3) and every
        // later ON/COOLDOWN chunk (4, 5, 6) are each emitted as a separate one-chunk SPEECH
        // segment, instead of one 6-chunk batch. Marker 0 is a leading, never-claimed silence
        // chunk, removed from the ring buffer several calls after the SPEECH chunks are
        // emitted. It must still be labeled NON_SPEECH.
        VoiceActivityScorer fake = fakeScorer(0.1f, 0.1f, 0.9f, 0.9f, 0.9f, 0.1f, 0.1f, 0.1f);
        VadConfig config = new VadConfig(0.5f, 2, 2, 1, 1);
        List<AudioSegment> segments = detect(fake, config, 8);

        // Order follows call order: the 4 chunks confirmed together (pre-roll markers 1-3,
        // plus marker 4) are all emitted in that same call. Marker 0 is emitted later, when
        // it is removed from the ring buffer.
        assertEquals(8, segments.size());
        assertSegment(segments.get(0), 32, 64, SpeechLabel.SPEECH, markerAudio(1));
        assertSegment(segments.get(1), 64, 96, SpeechLabel.SPEECH, markerAudio(2));
        assertSegment(segments.get(2), 96, 128, SpeechLabel.SPEECH, markerAudio(3));
        assertSegment(segments.get(3), 128, 160, SpeechLabel.SPEECH, markerAudio(4));
        assertSegment(segments.get(4), 160, 192, SpeechLabel.SPEECH, markerAudio(5));
        assertSegment(segments.get(5), 192, 224, SpeechLabel.SPEECH, markerAudio(6));
        assertSegment(segments.get(6), 0, 32, SpeechLabel.NON_SPEECH, markerAudio(0));
        assertSegment(segments.get(7), 224, 256, SpeechLabel.NON_SPEECH, markerAudio(7));
    }

    @Test
    void finishClosesASegmentStillOpenAtEndOfStream() {
        // Confirms ON at index 1, pulling index 0 in as pre-roll. It never returns to
        // silence before the stream ends, so finish() emits it.
        VoiceActivityScorer fake = fakeScorer(0.9f, 0.9f, 0.9f);
        VadConfig config = new VadConfig(0.5f, 1, 2, 0, VadConfig.FLUSH_ONLY_AT_CLOSE);
        List<AudioSegment> segments = detect(fake, config, 3);

        assertEquals(1, segments.size());
        assertSegment(segments.get(0), 0, 96, SpeechLabel.SPEECH, markerAudio(0, 1, 2));
    }

    private static void assertSegment(
            AudioSegment actual, long startMs, long endMs, SpeechLabel label, float[] samples) {
        assertEquals(new SpeechSegment(startMs, endMs), actual.segment());
        assertEquals(label, actual.label());
        assertArrayEquals(samples, actual.samples());
    }

    private static List<AudioSegment> detect(VoiceActivityScorer scorer, VadConfig config, int chunkCount) {
        try (VoiceActivityDetector vad = new WarmupCooldownVoiceActivityDetector(SAMPLE_RATE, config, scorer)) {
            return vad.detect(markerAudio(rangeMarkers(chunkCount)));
        }
    }

    /** Builds one big sample array where chunk i is filled entirely with the value i. */
    private static float[] markerAudio(int... chunkMarkers) {
        float[] samples = new float[chunkMarkers.length * CHUNK_SAMPLES];
        for (int i = 0; i < chunkMarkers.length; i++) {
            java.util.Arrays.fill(samples, i * CHUNK_SAMPLES, (i + 1) * CHUNK_SAMPLES, (float) chunkMarkers[i]);
        }
        return samples;
    }

    private static int[] rangeMarkers(int count) {
        int[] markers = new int[count];
        for (int i = 0; i < count; i++) {
            markers[i] = i;
        }
        return markers;
    }

    private static VoiceActivityScorer fakeScorer(float... probabilities) {
        return new VoiceActivityScorer() {
            private final Iterator<Float> values = boxed(probabilities).iterator();

            @Override
            public float speechProbability(float[] chunk) {
                return values.next();
            }

            @Override
            public void reset() {
            }

            @Override
            public void close() {
            }
        };
    }

    private static List<Float> boxed(float[] values) {
        List<Float> boxed = new ArrayList<>();
        for (float value : values) {
            boxed.add(value);
        }
        return boxed;
    }
}
