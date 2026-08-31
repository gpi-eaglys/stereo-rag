package com.eaglys.stereorag.llm;

import com.eaglys.stereorag.common.AudioSegment;
import com.eaglys.stereorag.common.AudioUtils;
import com.eaglys.stereorag.common.SpeechLabel;
import com.eaglys.stereorag.common.SpeechSegment;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GemmaTextGeneratorRealModelTest {

    private static final String MODEL_PATH = "../../assets/mdl/gemma/gemma-4-E2B_q4_0-it.gguf";
    private static final String MMPROJ_PATH = "../../assets/mdl/gemma/gemma-4-E2B-it-mmproj.gguf";
    private static final String AUDIO_PATH = "../../assets/audio/long-sample.wav";

    @Test
    void completesAPrompt() {
        assumeTrue(new File(MODEL_PATH).isFile(), "Gemma model not present at " + MODEL_PATH);

        GemmaConfig config = new GemmaConfig(MODEL_PATH, null, 0.2f, 32, 4096);
        String response;
        try (TextGenerator generator = new GemmaTextGenerator(config)) {
            response = generator.complete(
                    "Extract the intent in one word: \"I want to cancel my subscription.\"\nIntent:");
        }

        System.out.println("Gemma response: " + response);
        assertFalse(response.isBlank(), "Expected a non-blank response");
    }

    @Test
    void completesTwiceOnAGrowingPromptWithTheSameGenerator() {
        assumeTrue(new File(MODEL_PATH).isFile(), "Gemma model not present at " + MODEL_PATH);

        GemmaConfig config = new GemmaConfig(MODEL_PATH, null, 0.2f, 16, 4096);
        try (TextGenerator generator = new GemmaTextGenerator(config)) {
            String firstPrompt = "Customer: I want to cancel my subscription.\nAgent:";
            String firstResponse = generator.complete(firstPrompt);
            System.out.println("First response: " + firstResponse);
            assertFalse(firstResponse.isBlank(), "Expected a non-blank response on the first call");

            String secondPrompt = firstPrompt + firstResponse + "\nCustomer: Actually, never mind.\nAgent:";
            String secondResponse = generator.complete(secondPrompt);
            System.out.println("Second response: " + secondResponse);
            assertFalse(secondResponse.isBlank(), "Expected a non-blank response on the second call");
        }
    }

    @Test
    void completesAPromptWithAudio() throws IOException, UnsupportedAudioFileException {
        assumeTrue(new File(MODEL_PATH).isFile(), "Gemma model not present at " + MODEL_PATH);
        assumeTrue(new File(MMPROJ_PATH).isFile(), "Gemma mmproj not present at " + MMPROJ_PATH);
        assumeTrue(new File(AUDIO_PATH).isFile(), "Test audio not present at " + AUDIO_PATH);

        GemmaConfig config = GemmaConfig.withAudio(MODEL_PATH, MMPROJ_PATH);
        AudioSegment audio = readWav(AUDIO_PATH);

        String response;
        try (TextGenerator generator = new GemmaTextGenerator(config)) {
            response = generator.completeWithAudio("Transcribe the audio.", audio);
        }

        System.out.println("Gemma audio response: " + response);
        assertFalse(response.isBlank(), "Expected a non-blank response");
    }

    private static AudioSegment readWav(String path) throws IOException, UnsupportedAudioFileException {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(new File(path))) {
            float[] samples = AudioUtils.pcm16ToFloat(in.readAllBytes());
            long durationMs = samples.length * 1000L / (long) in.getFormat().getSampleRate();
            return new AudioSegment(new SpeechSegment(0, durationMs), SpeechLabel.SPEECH, samples);
        }
    }
}
