package com.eaglys.stereorag.llm;

import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.ModelParameters;

/**
 * Generates text with a local Gemma model, through llama.cpp (JNI, in-process, no network
 * calls).
 *
 * <p>Not thread-safe: each instance holds one model loaded in native memory.
 */
public final class GemmaTextGenerator implements TextGenerator {

    private final LlamaModel model;
    private final float temperature;
    private final int maxTokens;

    public GemmaTextGenerator(GemmaConfig config) {
        ModelParameters modelParams = new ModelParameters().setModel(config.modelPath());
        this.model = new LlamaModel(modelParams);
        this.temperature = config.temperature();
        this.maxTokens = config.maxTokens();
    }

    @Override
    public String complete(String prompt) {
        InferenceParameters inferParams = new InferenceParameters(prompt)
                .setTemperature(temperature)
                .setNPredict(maxTokens);
        return model.complete(inferParams);
    }

    @Override
    public void close() {
        model.close();
    }
}
