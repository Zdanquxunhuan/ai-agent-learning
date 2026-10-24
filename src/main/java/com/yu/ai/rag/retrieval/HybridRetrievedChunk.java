package com.yu.ai.rag.retrieval;

import java.util.UUID;

/**
 * A chunk returned by hybrid retrieval.
 *
 * semanticRank and keywordRank are one-based ranks in their respective
 * result lists. A null rank means that the chunk was not returned by that
 * retrieval strategy. rrfScore is a rank-fusion score, not a probability.
 */
public record HybridRetrievedChunk(
        UUID chunkId,
        UUID documentId,
        int chunkIndex,
        String content,
        Integer pageNumber,
        String sectionTitle,
        Integer semanticRank,
        Integer keywordRank,
        Double similarity,
        Integer matchedTerms,
        double rrfScore
) {
}
