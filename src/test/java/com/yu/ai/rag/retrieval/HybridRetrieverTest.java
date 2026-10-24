package com.yu.ai.rag.retrieval;

import com.yu.ai.rag.common.BusinessException;
import com.yu.ai.rag.vector.RetrievedChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridRetrieverTest {

    private static final UUID DOCUMENT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SHARED_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID SEMANTIC_ONLY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final UUID SEMANTIC_ONLY_2_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000013");
    private static final UUID KEYWORD_ONLY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000014");
    private static final UUID KEYWORD_ONLY_2_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000015");

    @Mock
    private SemanticRetriever semanticRetriever;

    @Mock
    private KeywordRetriever keywordRetriever;

    @InjectMocks
    private HybridRetriever hybridRetriever;

    @Test
    void shouldFuseRanksDeduplicateByChunkIdAndReturnTopK() {
        when(semanticRetriever.search("ERR-1007", 3)).thenReturn(List.of(
                semantic(SHARED_ID, 0, "ERR-1007 troubleshooting", 0.95),
                semantic(SEMANTIC_ONLY_ID, 1, "Troubleshooting an error", 0.90),
                semantic(SEMANTIC_ONLY_2_ID, 2, "Error handling guide", 0.85)
        ));

        when(keywordRetriever.search("ERR-1007", 3)).thenReturn(List.of(
                keyword(SHARED_ID, 0, "ERR-1007 troubleshooting", 3),
                keyword(KEYWORD_ONLY_ID, 3, "ERR-1007 exact code reference", 2),
                keyword(KEYWORD_ONLY_2_ID, 4, "Other ERR-1007 note", 1)
        ));

        List<HybridRetrievedChunk> results =
                hybridRetriever.search("ERR-1007", 3);

        assertEquals(3, results.size());
        assertEquals(SHARED_ID, results.get(0).chunkId());
        assertEquals(1, results.get(0).semanticRank());
        assertEquals(1, results.get(0).keywordRank());
        assertEquals(2.0 / 61.0, results.get(0).rrfScore(), 1.0e-12);

        assertEquals(SEMANTIC_ONLY_ID, results.get(1).chunkId());
        assertEquals(2, results.get(1).semanticRank());
        assertNull(results.get(1).keywordRank());
        assertEquals(1.0 / 62.0, results.get(1).rrfScore(), 1.0e-12);

        assertEquals(KEYWORD_ONLY_ID, results.get(2).chunkId());
        assertNull(results.get(2).semanticRank());
        assertEquals(2, results.get(2).keywordRank());

        assertEquals(3, results.stream()
                .map(HybridRetrievedChunk::chunkId)
                .distinct()
                .count());

        verify(semanticRetriever).search("ERR-1007", 3);
        verify(keywordRetriever).search("ERR-1007", 3);
    }

    @Test
    void shouldRejectBlankQueryBeforeCallingRetrievers() {
        assertThrows(
                BusinessException.class,
                () -> hybridRetriever.search("   ", 3)
        );
        verifyNoInteractions(semanticRetriever, keywordRetriever);
    }

    @Test
    void shouldRejectTopKOutsideAllowedRange() {
        assertThrows(
                BusinessException.class,
                () -> hybridRetriever.search("ERR-1007", 21)
        );
        verifyNoInteractions(semanticRetriever, keywordRetriever);
    }

    private static RetrievedChunk semantic(
            UUID chunkId,
            int chunkIndex,
            String content,
            double similarity) {
        return new RetrievedChunk(
                chunkId,
                DOCUMENT_ID,
                chunkIndex,
                content,
                null,
                null,
                similarity
        );
    }

    private static KeywordRetrievedChunk keyword(
            UUID chunkId,
            int chunkIndex,
            String content,
            int matchedTerms) {
        return new KeywordRetrievedChunk(
                chunkId,
                DOCUMENT_ID,
                chunkIndex,
                content,
                null,
                null,
                matchedTerms
        );
    }
}
