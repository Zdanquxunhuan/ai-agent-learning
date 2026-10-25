package com.yu.ai.rag.retrieval;

import java.util.List;

/**
 * Reorders an existing candidate list by query relevance.
 * Implementations must not add chunks that were not present in candidates.
 */
public interface Reranker {

    RerankerResult rerank(
            String query,
            List<HybridRetrievedChunk> candidates,
            int topK);
}
