package com.eaglys.stereorag.vad;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Wraps the Silero VAD ONNX model (v5 graph: packed recurrent {@code state} of shape
 * [2, batch, 128]) and scores one fixed-size audio chunk at a time.
 *
 * <p>Not thread-safe: each instance carries the recurrent state for a single audio stream.
 */
public final class SileroVadDetector implements VoiceActivityScorer {

    private static final String MODEL_RESOURCE = "/mdl/siler/silero-vad_v5.onnx";
    private static final int STATE_LAYERS = 2;
    private static final int STATE_HIDDEN_SIZE = 128;

    /** Samples per chunk expected by the model at 16 kHz (32 ms). */
    public static final int CHUNK_SAMPLES_16K = 512;
    /** Samples per chunk expected by the model at 8 kHz (32 ms). */
    public static final int CHUNK_SAMPLES_8K = 256;

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final long sampleRate;
    private float[][][] state;

    public SileroVadDetector(int sampleRate) {
        if (sampleRate != 8000 && sampleRate != 16000) {
            throw new IllegalArgumentException("Silero VAD only supports 8000 or 16000 Hz, got: " + sampleRate);
        }
        this.sampleRate = sampleRate;
        this.environment = OrtEnvironment.getEnvironment();
        try {
            this.session = environment.createSession(readModel(), new OrtSession.SessionOptions());
        } catch (OrtException e) {
            throw new IllegalStateException("Failed to create ONNX session for Silero VAD", e);
        }
        reset();
    }

    /** Clears the recurrent state; call before scoring a new, unrelated audio stream. */
    public void reset() {
        state = new float[STATE_LAYERS][1][STATE_HIDDEN_SIZE];
    }

    /**
     * Scores one chunk of {@link #CHUNK_SAMPLES_16K} (or {@link #CHUNK_SAMPLES_8K} at 8 kHz)
     * mono samples normalized to [-1, 1].
     *
     * @return probability in [0, 1] that the chunk contains speech
     */
    public float speechProbability(float[] chunk) {
        try (OnnxTensor inputTensor = OnnxTensor.createTensor(environment, new float[][] {chunk});
                OnnxTensor stateTensor = OnnxTensor.createTensor(environment, state);
                OnnxTensor srTensor = OnnxTensor.createTensor(environment, sampleRate)) {

            Map<String, OnnxTensor> inputs = Map.of(
                    "input", inputTensor,
                    "state", stateTensor,
                    "sr", srTensor);

            try (OrtSession.Result result = session.run(inputs)) {
                float[][] output = (float[][]) result.get(0).getValue();
                state = (float[][][]) result.get(1).getValue();
                return output[0][0];
            }
        } catch (OrtException e) {
            throw new IllegalStateException("Silero VAD inference failed", e);
        }
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (OrtException e) {
            throw new IllegalStateException("Failed to close Silero VAD session", e);
        }
    }

    private static byte[] readModel() {
        try (InputStream in = SileroVadDetector.class.getResourceAsStream(MODEL_RESOURCE)) {
            if (in == null) {
                throw new IOException("Model resource not found on classpath: " + MODEL_RESOURCE);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
