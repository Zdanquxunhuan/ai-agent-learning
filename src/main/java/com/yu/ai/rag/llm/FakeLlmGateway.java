package com.yu.ai.rag.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "rag.llm", name = "provider", havingValue = "fake", matchIfMissing = true)
public class FakeLlmGateway implements LlmGateway {

    private static final String FIXED_RESPONSE =
            "{\"answer\":\"[C1] Fake LLM fixed test response. This is not a real model answer.\","
                    + "\"citationIds\":[\"C1\"],\"insufficientEvidence\":false}";

    @Override
    public LlmCompletion complete(LlmRequest request) {
        long start = System.nanoTime();
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        return new LlmCompletion(
                "fake",
                "fake-llm-v1",
                FIXED_RESPONSE,
                null,
                null,
                latencyMs);
    }
}
