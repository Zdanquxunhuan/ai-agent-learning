package com.yu.ai.rag.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "rag.llm", name = "provider", havingValue = "fake", matchIfMissing = true)
public class FakeLlmGateway implements LlmGateway {

    private static final String FIXED_RESPONSE =
            "\u3010Fake LLM \u56fa\u5b9a\u6d4b\u8bd5\u56de\u7b54\u3011"
                    + "\u5df2\u9a8c\u8bc1\u68c0\u7d22\u5230\u751f\u6210\u7684\u8c03\u7528\u94fe\uff1b"
                    + "\u8fd9\u662f\u56fa\u5b9a\u6d4b\u8bd5\u6587\u672c\uff0c\u4e0d\u662f\u771f\u5b9e\u6a21\u578b\u7684\u77e5\u8bc6\u56de\u7b54\u3002";

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
                latencyMs
        );
    }
}
