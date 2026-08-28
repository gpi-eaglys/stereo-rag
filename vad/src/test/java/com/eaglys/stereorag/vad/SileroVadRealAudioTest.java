package com.eaglys.stereorag.vad;

import com.eaglys.stereorag.common.AudioSegment;
import com.eaglys.stereorag.common.SpeechLabel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SileroVadRealAudioTest {

    private static final int SAMPLE_RATE = 16000;

    @Test
    void labelsEveryChunkOfARealRecordingWithNoGapsOrOverlaps() {
        float[] samples = WavTestAudio.loadMonoPcm16("/audio/long-sample.wav");

        List<AudioSegment> segments;
        int chunkSamples;
        try (VoiceActivityDetector vad = VoiceActivityDetectors.silero(SAMPLE_RATE)) {
            chunkSamples = vad.chunkSamples();
            segments = vad.detect(samples);
        }
        // detect() zero-pads a trailing partial chunk, so the processed length can exceed the
        // input by up to one chunk; coverage is checked against that padded length, not the
        // raw sample count.
        int chunkCount = (samples.length + chunkSamples - 1) / chunkSamples;
        long paddedTotalSamples = (long) chunkCount * chunkSamples;

        System.out.printf("Labeled %d segment(s) covering %d ms of audio:%n",
                segments.size(), paddedTotalSamples * 1000L / SAMPLE_RATE);
        for (AudioSegment segment : segments) {
            System.out.printf("  [%6d ms .. %6d ms] %-10s (%d samples)%n",
                    segment.segment().startMs(), segment.segment().endMs(),
                    segment.label(), segment.samples().length);
        }

        assertFalse(segments.isEmpty(), "Expected at least one segment in a real recording");
        assertTrue(segments.stream().anyMatch(s -> s.label() == SpeechLabel.SPEECH),
                "Expected at least one SPEECH segment in a real recording");

        long expectedNextStartMs = 0;
        long totalSamples = 0;
        for (AudioSegment segment : segments) {
            assertEquals(expectedNextStartMs, segment.segment().startMs(), "Segments must cover the stream with no gap or overlap");
            assertTrue(segment.segment().endMs() > segment.segment().startMs(), "Segment must have positive duration");
            expectedNextStartMs = segment.segment().endMs();
            totalSamples += segment.samples().length;
        }
        assertEquals(paddedTotalSamples, totalSamples, "Every processed sample must appear in exactly one segment");
    }
}
