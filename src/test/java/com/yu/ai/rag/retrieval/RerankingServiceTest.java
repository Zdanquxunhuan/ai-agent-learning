package com.yu.ai.rag.retrieval;

import com.yu.ai.rag.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RerankingServiceTest {

    @Mock
    private HybridRetriever hybridRetriever;

    @Mock
    private Reranker reranker;

    @InjectMocks
    private RerankingService rerankingService;

    @Test
    void shouldRetrieveCandidatePoolThenRerankToTopK() {
        List<HybridRetrievedChunk> candidates = List.of(
                chunk("00000000-0000-0000-0000-000000000001", "candidate one"),
                chunk("00000000-0000-0000-0000-000000000002", "candidate two"),
                chunk("00000000-0000-0000-0000-000000000003", "candidate three")
        );
        RerankerResult expected = new RerankerResult(
                "identity", false, 3, 0, List.of(
                new RerankedChunk(candidates.get(0), 1, null),
                new RerankedChunk(candidates.get(1), 2, null)));

        when(hybridRetriever.search("ERR-1007", 3)).thenReturn(candidates);
        when(reranker.rerank("ERR-1007", candidates, 2)).thenReturn(expected);

        RerankerResult actual =
                rerankingService.search("ERR-1007", 2, 3);

        assertEquals(expected, actual);
        verify(hybridRetriever).search("ERR-1007", 3);
        verify(reranker).rerank("ERR-1007", candidates, 2);
    }

    @Test
    void shouldRejectCandidateKSmallerThanTopK() {
        assertThrows(
                BusinessException.class,
                () -> rerankingService.search("ERR-1007", 4, 3)
        );
        verifyNoInteractions(hybridRetriever, reranker);
    }

    private static HybridRetrievedChunk chunk(String id, String content) {
        return new HybridRetrievedChunk(
                UUID.fromString(id),
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                0, content, null, null, 1, null, 0.9, null, 1.0 / 61.0);
    }
}
