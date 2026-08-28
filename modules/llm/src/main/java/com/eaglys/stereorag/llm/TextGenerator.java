package com.eaglys.stereorag.llm;

/** Generates text from a prompt, using a local language model. */
public interface TextGenerator extends AutoCloseable {

    /** Generates a complete response for the given prompt. */
    String complete(String prompt);

    @Override
    void close();
}
