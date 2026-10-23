
package com.yu.ai.rag.retrieval;

import com.yu.ai.rag.embedding.EmbeddingResult;
import com.yu.ai.rag.embedding.EmbeddingService;
import com.yu.ai.rag.vector.RetrievedChunk;
import com.yu.ai.rag.vector.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SemanticRetriever {

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public SemanticRetriever(
            EmbeddingService embeddingService,
            VectorStore vectorStore) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    /**
     * 将用户问题转换为向量，并检索最相关的文本片段。
     */
    public List<RetrievedChunk> search(String query, int topK) {
        // 第一步：将用户问题转换为向量
        EmbeddingResult embeddingResult =
                embeddingService.embed(query);

        // 第二步：根据问题向量检索相关片段
        return vectorStore.similaritySearch(
                embeddingResult.vector(),
                topK
        );
    }
}
