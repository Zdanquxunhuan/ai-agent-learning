
package com.yu.ai.rag.retrieval;

import com.yu.ai.rag.embedding.EmbeddingResult;
import com.yu.ai.rag.embedding.EmbeddingService;
import com.yu.ai.rag.vector.RetrievedChunk;
import com.yu.ai.rag.vector.VectorStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SemanticRetrieverTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private SemanticRetriever semanticRetriever;

    @Test
    void shouldEmbedQueryThenSearchVectorStore() {
        String query = "数据库如何保存向量？";
        int topK = 3;

        EmbeddingResult embeddingResult = new EmbeddingResult(
                "test-model",
                3,
                List.of(0.1, 0.2, 0.3)
        );

        RetrievedChunk chunk = new RetrievedChunk(
                UUID.randomUUID(),
                UUID.randomUUID(),
                0,
                "数据库通过 pgvector 保存向量。",
                1,
                "向量存储",
                0.95
        );

        List<RetrievedChunk> expected = List.of(chunk);

        // 模拟问题向量化
        when(embeddingService.embed(query))
                .thenReturn(embeddingResult);

        // 模拟向量数据库查询
        when(vectorStore.similaritySearch(
                embeddingResult.vector(), topK))
                .thenReturn(expected);

        // 执行语义检索
        List<RetrievedChunk> actual =
                semanticRetriever.search(query, topK);

        // 验证返回结果
        assertEquals(expected, actual);

        // 验证调用顺序：先向量化，再查询数据库
        InOrder inOrder = inOrder(
                embeddingService, vectorStore);

        inOrder.verify(embeddingService).embed(query);
        inOrder.verify(vectorStore).similaritySearch(
                embeddingResult.vector(), topK);

        verifyNoMoreInteractions(
                embeddingService, vectorStore);
    }
}
