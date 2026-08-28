package com.eaglys.stereorag.vad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SileroVadDetectorTest {

    @Test
    void silenceScoresLowProbability() {
        try (SileroVadDetector detector = new SileroVadDetector(16000)) {
            float[] silence = new float[SileroVadDetector.CHUNK_SAMPLES_16K];
            float probability = detector.speechProbability(silence);
            assertTrue(probability < 0.1f, "Expected low speech probability for silence, got " + probability);
        }
    }

    @Test
    void stateCanBeResetBetweenStreams() {
        try (SileroVadDetector detector = new SileroVadDetector(16000)) {
            float[] silence = new float[SileroVadDetector.CHUNK_SAMPLES_16K];
            detector.speechProbability(silence);
            detector.reset();
            float probability = detector.speechProbability(silence);
            assertTrue(probability < 0.1f, "Expected low speech probability after reset, got " + probability);
        }
    }
}
