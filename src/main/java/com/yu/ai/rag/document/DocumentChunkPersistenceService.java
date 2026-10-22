
package com.yu.ai.rag.document;

import com.yu.ai.rag.chunking.DocumentChunkRepository;
import com.yu.ai.rag.chunking.TextChunk;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DocumentChunkPersistenceService {

    private final DocumentChunkRepository chunkRepository;

    public DocumentChunkPersistenceService(
            DocumentChunkRepository chunkRepository
    ) {
        this.chunkRepository = chunkRepository;
    }

    /**
     * 在同一个数据库事务中删除旧切片并保存新切片。
     */
    @Transactional
    public void replaceAll(
            UUID documentId,
            List<TextChunk> chunks
    ) {
        chunkRepository.deleteByDocumentId(documentId);
        chunkRepository.saveAll(documentId, chunks);
    }
}
