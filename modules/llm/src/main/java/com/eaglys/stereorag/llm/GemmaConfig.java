package com.eaglys.stereorag.llm;

/**
 * Tuning for {@link GemmaTextGenerator}.
 *
 * @param modelPath path to the GGUF model file
 * @param temperature sampling temperature. Higher values produce more varied output.
 * @param maxTokens maximum number of tokens to generate for one prompt
 */
public record GemmaConfig(String modelPath, float temperature, int maxTokens) {

    public static GemmaConfig of(String modelPath) {
        return new GemmaConfig(modelPath, 0.2f, 512);
    }
}
