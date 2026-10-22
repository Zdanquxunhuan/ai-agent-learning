
package com.yu.ai.rag.embedding;

public class EmbeddingException extends RuntimeException {

    private final Code code;

    public EmbeddingException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public EmbeddingException(
            Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }

    public enum Code {
        INVALID_INPUT,
        CONFIGURATION_ERROR,
        TIMEOUT,
        RATE_LIMITED,
        AUTHENTICATION_FAILED,
        PROVIDER_ERROR,
        INVALID_RESPONSE,
        DIMENSION_MISMATCH,
        INTERRUPTED
    }
}
