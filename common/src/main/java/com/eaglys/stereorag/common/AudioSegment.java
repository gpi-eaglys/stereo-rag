package com.eaglys.stereorag.common;

/** A {@link SpeechSegment} labeled speech or non-speech, with audio samples normalized to [-1, 1]. */
public record AudioSegment(SpeechSegment segment, SpeechLabel label, float[] samples) {
}
