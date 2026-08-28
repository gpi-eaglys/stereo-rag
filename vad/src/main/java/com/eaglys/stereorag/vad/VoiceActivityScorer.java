package com.eaglys.stereorag.vad;

/** Scores fixed-size audio chunks for speech probability, carrying state across calls. */
public interface VoiceActivityScorer extends AutoCloseable {

    float speechProbability(float[] chunk);

    void reset();

    @Override
    void close();
}
