package com.eaglys.stereorag.llm;

import com.eaglys.stereorag.common.AudioSegment;

/** Generates text from a prompt, using a local language model. */
public interface TextGenerator extends AutoCloseable {

    /** Generates a complete response for the given prompt. */
    String complete(String prompt);

    /** Generates a complete response for the given prompt, together with a piece of audio. */
    String completeWithAudio(String prompt, AudioSegment audio);

    @Override
    void close();
}
