package com.eaglys.stereorag.llm;

/**
 * Tuning for {@link GemmaTextGenerator}.
 *
 * @param modelPath path to the GGUF model file
 * @param mmprojPath path to the multimodal projector file, needed for {@link
 *     TextGenerator#completeWithAudio}. Null if audio input is not needed.
 * @param temperature sampling temperature. Higher values produce more varied output.
 * @param maxTokens maximum number of tokens to generate for one prompt
 * @param ctxSize context window size, in tokens
 */
public record GemmaConfig(String modelPath, String mmprojPath, float temperature, int maxTokens, int ctxSize) {

    public static GemmaConfig of(String modelPath) {
        return new GemmaConfig(modelPath, null, 0.2f, 512, 4096);
    }

    public static GemmaConfig withAudio(String modelPath, String mmprojPath) {
        return new GemmaConfig(modelPath, mmprojPath, 0.2f, 512, 4096);
    }
}
