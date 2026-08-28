package com.eaglys.stereorag.vad;

/**
 * Tuning for {@link WarmupCooldownVoiceActivityDetector}'s OFF/WARMUP/ON/COOLDOWN state machine.
 *
 * @param threshold probability above which a chunk counts as a "hit"
 * @param minWarmupChunks consecutive hits (beyond the one that leaves OFF) required to confirm
 *     WARMUP into ON
 * @param minCooldownChunks consecutive misses required to close ON/COOLDOWN back to OFF
 * @param lhsPadChunks number of chunks before the warmup period, added as pre-roll once confirmed
 * @param speechFlushChunks number of confirmed-speech chunks to buffer before emitting them. The
 *     segment does not need to be closed yet. {@code 1} emits each chunk immediately after
 *     confirmation, for lowest latency. {@link #FLUSH_ONLY_AT_CLOSE} buffers the whole segment
 *     and emits it only when cooldown closes it. Other values emit larger batches, less often.
 */
public record VadConfig(
        float threshold, int minWarmupChunks, int minCooldownChunks, int lhsPadChunks, int speechFlushChunks) {

    /** {@link #speechFlushChunks} sentinel: never flush early, only when the segment closes. */
    public static final int FLUSH_ONLY_AT_CLOSE = Integer.MAX_VALUE;

    public static VadConfig defaults() {
        return new VadConfig(0.4f, 2, 2, 2, FLUSH_ONLY_AT_CLOSE);
    }
}
