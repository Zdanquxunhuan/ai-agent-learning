package com.yu.ai.rag.retrieval;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Safe default: preserve the hybrid RRF order without calling an external service.
 */
@Component
@ConditionalOnProperty(
        prefix = "rag.rerank",
        name = "provider",
        havingValue = "identity",
        matchIfMissing = true
)
public class IdentityReranker implements Reranker {

    @Override
    public RerankerResult rerank(
            String query,
            List<HybridRetrievedChunk> candidates,
            int topK) {

        if (topK < 1) {
            throw new IllegalArgumentException("topK must be at least 1");
        }

        long started = System.nanoTime();
        List<RerankedChunk> results = new ArrayList<>();
        int resultCount = Math.min(topK, candidates.size());

        for (int i = 0; i < resultCount; i++) {
            results.add(new RerankedChunk(candidates.get(i), i + 1, null));
        }

        return new RerankerResult(
                "identity",
                false,
                candidates.size(),
                elapsedMillis(started),
                results
        );
    }

    static long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
