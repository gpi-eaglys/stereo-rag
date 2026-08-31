package com.eaglys.stereorag.llm;

import com.eaglys.stereorag.common.AudioSegment;
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
        // fit_params (on by default) auto-adjusts unset model/context parameters to fit free
        // device memory. It crashes in this environment, so it is turned off and the
        // parameters it would set are set explicitly instead.
        //
        // Turning fit_params off has a side effect: n_parallel defaults to -1 ("auto"),
        // meant to be resolved by fit_params. With fit_params off, that -1 is never resolved
        // and is later used as an unsigned size when sizing an internal buffer, requesting
        // about 4 billion entries and crashing with std::bad_alloc. setParallel(1) avoids
        // this by setting a real value instead of relying on the -1 default.
        ModelParameters modelParams = new ModelParameters()
                .setModel(config.modelPath())
                .setFitParams(false)
                .setCtxSize(config.ctxSize())
                .setGpuLayers(0)
                .setFlashAttn("off")
                .setParallel(1);
        if (config.mmprojPath() != null) {
            modelParams.setMmproj(config.mmprojPath());
        }
        this.model = new LlamaModel(modelParams);
        this.temperature = config.temperature();
        this.maxTokens = config.maxTokens();
    }

    @Override
    public String complete(String prompt) {
        // cache_prompt reuses the previously computed part of the prompt when the same
        // instance is called again with a prompt that starts with the same text. This is
        // what makes incremental use (e.g. a growing conversation transcript) efficient.
        InferenceParameters inferParams = new InferenceParameters(prompt)
                .setTemperature(temperature)
                .setNPredict(maxTokens)
                .setCachePrompt(true);
        return model.complete(inferParams);
    }

    @Override
    public String completeWithAudio(String prompt, AudioSegment audio) {
        // Runs on its own native context inside llama.cpp, separate from the one used by
        // complete(). Each call starts fresh: no cache_prompt-style reuse across calls yet.
        return model.completeWithAudio(prompt, audio.samples(), temperature, maxTokens);
    }

    @Override
    public void close() {
        model.close();
    }
}
