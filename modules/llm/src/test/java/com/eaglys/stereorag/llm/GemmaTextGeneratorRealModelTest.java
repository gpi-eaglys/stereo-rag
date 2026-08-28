package com.eaglys.stereorag.llm;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GemmaTextGeneratorRealModelTest {

    private static final String MODEL_PATH = "../../assets/mdl/gemma/gemma-4-E2B_q4_0-it.gguf";

    @Test
    void completesAPrompt() {
        assumeTrue(new File(MODEL_PATH).isFile(), "Gemma model not present at " + MODEL_PATH);

        GemmaConfig config = new GemmaConfig(MODEL_PATH, 0.2f, 32);
        String response;
        try (TextGenerator generator = new GemmaTextGenerator(config)) {
            response = generator.complete(
                    "Extract the intent in one word: \"I want to cancel my subscription.\"\nIntent:");
        }

        System.out.println("Gemma response: " + response);
        assertFalse(response.isBlank(), "Expected a non-blank response");
    }
}
