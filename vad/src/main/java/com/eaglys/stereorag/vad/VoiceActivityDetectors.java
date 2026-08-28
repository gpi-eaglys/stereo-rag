package com.eaglys.stereorag.vad;

/** Convenience factories for {@link VoiceActivityDetector} backends. */
public final class VoiceActivityDetectors {

    private VoiceActivityDetectors() {
    }

    public static VoiceActivityDetector silero(int sampleRate) {
        return silero(sampleRate, VadConfig.defaults());
    }

    public static VoiceActivityDetector silero(int sampleRate, VadConfig config) {
        return new WarmupCooldownVoiceActivityDetector(sampleRate, config, new SileroVadDetector(sampleRate));
    }
}
