package com.yu.ai.rag.llm;

public record LlmRequest(
        String systemPrompt,
        String userPrompt,
        String model,
        double temperature,
        int maxTokens
) {
}
