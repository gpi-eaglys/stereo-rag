package com.eaglys.stereorag.common;

/** A detected span of speech, in milliseconds from the start of the audio stream. */
public record SpeechSegment(long startMs, long endMs) {
}
