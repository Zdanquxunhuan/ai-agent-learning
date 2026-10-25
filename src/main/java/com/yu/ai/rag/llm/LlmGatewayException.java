package com.yu.ai.rag.llm;

public class LlmGatewayException extends RuntimeException {

    public enum FailureType {
        TIMEOUT("LLM_TIMEOUT", "The language model request timed out."),
        EMPTY_RESPONSE("LLM_EMPTY_RESPONSE", "The language model returned an empty answer."),
        PROVIDER_ERROR("LLM_PROVIDER_ERROR", "The language model service failed."),
        INVALID_RESPONSE("LLM_INVALID_RESPONSE", "The language model returned an invalid response.");

        private final String code;
        private final String publicMessage;

        FailureType(String code, String publicMessage) {
            this.code = code;
            this.publicMessage = publicMessage;
        }

        public String code() { return code; }
        public String publicMessage() { return publicMessage; }
    }

    private final FailureType failureType;

    public LlmGatewayException(FailureType failureType, String internalMessage) {
        super(internalMessage);
        this.failureType = failureType;
    }

    public LlmGatewayException(
            FailureType failureType,
            String internalMessage,
            Throwable cause) {
        super(internalMessage, cause);
        this.failureType = failureType;
    }

    public FailureType getFailureType() { return failureType; }
}
