
package com.yu.ai.rag.document;

import com.yu.ai.rag.chunking.DocumentChunkRepository;
import com.yu.ai.rag.chunking.TextChunk;
import com.yu.ai.rag.embedding.EmbeddingResult;
import com.yu.ai.rag.embedding.EmbeddingService;
import com.yu.ai.rag.vector.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DocumentChunkPersistenceService {

    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public DocumentChunkPersistenceService(
            DocumentChunkRepository chunkRepository,
            EmbeddingService embeddingService,
            VectorStore vectorStore
    ) {
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    /**
     * 在同一个数据库事务中替换切片并保存向量。
     */
    @Transactional
    public void replaceAll(
            UUID documentId,
            List<TextChunk> chunks
    ) {
        // 1. 删除旧切片及其向量
        chunkRepository.deleteByDocumentId(documentId);

        // 2. 为每个切片生成向量，并一次性保存文本和向量
        for (TextChunk chunk : chunks) {
            EmbeddingResult embedding =
                    embeddingService.embed(chunk.text());

            vectorStore.upsert(chunk, embedding);
        }
    }
}
