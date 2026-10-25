package com.yu.ai.rag.llm;

/** Provider-neutral boundary for chat completion calls. */
public interface LlmGateway {

    LlmCompletion complete(LlmRequest request);
}
