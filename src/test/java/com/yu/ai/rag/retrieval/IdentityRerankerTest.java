package com.yu.ai.rag.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IdentityRerankerTest {

    @Test
    void shouldPreserveHybridOrderAndLimitTopK() {
        List<HybridRetrievedChunk> candidates = List.of(
                chunk("00000000-0000-0000-0000-000000000001", "first"),
                chunk("00000000-0000-0000-0000-000000000002", "second"),
                chunk("00000000-0000-0000-0000-000000000003", "third")
        );

        RerankerResult result =
                new IdentityReranker().rerank("query", candidates, 2);

        assertEquals("identity", result.provider());
        assertEquals(false, result.fallbackUsed());
        assertEquals(3, result.candidateCount());
        assertEquals(2, result.results().size());
        assertEquals("first", result.results().get(0).chunk().content());
        assertEquals("second", result.results().get(1).chunk().content());
        assertEquals(1, result.results().get(0).rerankRank());
        assertNull(result.results().get(0).rerankScore());
    }

    private static HybridRetrievedChunk chunk(String id, String content) {
        UUID chunkId = UUID.fromString(id);
        UUID documentId = UUID.fromString(
                "10000000-0000-0000-0000-000000000001");
        return new HybridRetrievedChunk(
                chunkId, documentId, 0, content, null, null,
                1, null, 0.9, null, 1.0 / 61.0);
    }
}
