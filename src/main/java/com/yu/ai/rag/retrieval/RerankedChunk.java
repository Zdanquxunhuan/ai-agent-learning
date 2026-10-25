package com.yu.ai.rag.retrieval;

/**
 * One chunk after the reranking stage.
 * rerankScore is null when the active reranker does not provide a score.
 */
public record RerankedChunk(
        HybridRetrievedChunk chunk,
        int rerankRank,
        Double rerankScore
) {
}
