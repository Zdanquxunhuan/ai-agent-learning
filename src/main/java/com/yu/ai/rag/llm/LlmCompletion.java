package com.yu.ai.rag.llm;

public record LlmCompletion(
        String provider,
        String model,
        String content,
        Integer promptTokens,
        Integer completionTokens,
        long latencyMs
) {
}
