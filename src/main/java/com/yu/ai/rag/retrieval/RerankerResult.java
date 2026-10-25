package com.yu.ai.rag.retrieval;

import java.util.List;

/** Result metadata makes provider use and fallback observable. */
public record RerankerResult(
        String provider,
        boolean fallbackUsed,
        int candidateCount,
        long rerankLatencyMs,
        List<RerankedChunk> results
) {
    public RerankerResult {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
