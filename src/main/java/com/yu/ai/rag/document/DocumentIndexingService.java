
package com.yu.ai.rag.document;

import com.yu.ai.rag.chunking.ChunkingConfig;
import com.yu.ai.rag.chunking.TextChunk;
import com.yu.ai.rag.chunking.TextChunker;
import com.yu.ai.rag.parser.DocumentParser;
import com.yu.ai.rag.parser.ParsedDocument;
import com.yu.ai.rag.parser.ParserRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class DocumentIndexingService {

    private final ParserRegistry parserRegistry;
    private final TextChunker textChunker;
    private final DocumentRepository documentRepository;
    private final DocumentChunkPersistenceService persistenceService;
    private final ChunkingConfig chunkingConfig;

    public DocumentIndexingService(
            ParserRegistry parserRegistry,
            TextChunker textChunker,
            DocumentRepository documentRepository,
            DocumentChunkPersistenceService persistenceService,
            @Value("${rag.chunking.max-chunk-size:500}")
            int maxChunkSize,
            @Value("${rag.chunking.overlap-size:50}")
            int overlapSize
    ) {
        this.parserRegistry = parserRegistry;
        this.textChunker = textChunker;
        this.documentRepository = documentRepository;
        this.persistenceService = persistenceService;
        this.chunkingConfig = new ChunkingConfig(
                maxChunkSize,
                overlapSize
        );
    }

    /**
     * 执行文档索引并维护文档状态。
     */
    public List<TextChunk> index(
            UUID documentId,
            String originalFilename,
            String contentType,
            byte[] content
    ) {
        Objects.requireNonNull(
                documentId,
                "documentId must not be null"
        );

        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException(
                    "originalFilename must not be blank"
            );
        }

        if (content == null || content.length == 0) {
            throw new IllegalArgumentException(
                    "Document content must not be empty"
            );
        }

        // 只有 UPLOADED 状态的文档才能开始索引。
        // 条件更新失败意味着文档不存在、状态不符或索引已被触发。
        boolean started = documentRepository.transitionStatus(
                documentId,
                DocumentStatus.UPLOADED,
                DocumentStatus.PROCESSING
        );

        if (!started) {
            throw new IllegalStateException(
                    "Document cannot start indexing: " + documentId
            );
        }

        try {
            // 1. 选择解析器。
            DocumentParser parser = parserRegistry.getParser(
                    originalFilename,
                    contentType
            );

            // 2. 解析原始文件。
            ParsedDocument parsedDocument = parser.parse(content);

            // 3. 执行文本切片。
            List<TextChunk> chunks = textChunker.chunk(
                    documentId.toString(),
                    parsedDocument,
                    chunkingConfig
            );

            if (chunks.isEmpty()) {
                throw new IllegalStateException(
                        "Document produced no text chunks"
                );
            }

            // 4. 在独立事务中替换数据库切片。
            persistenceService.replaceAll(documentId, chunks);

            // 5. 切片保存成功后，更新为 READY。
            boolean ready = documentRepository.transitionStatus(
                    documentId,
                    DocumentStatus.PROCESSING,
                    DocumentStatus.READY
            );

            if (!ready) {
                throw new IllegalStateException(
                        "Failed to mark document as READY: " + documentId
                );
            }

            return chunks;

        } catch (RuntimeException exception) {
            // 索引失败时，尽量将状态更新为 FAILED。
            // 状态更新本身失败时，保留原始异常。
            try {
                documentRepository.transitionStatus(
                        documentId,
                        DocumentStatus.PROCESSING,
                        DocumentStatus.FAILED
                );
            } catch (RuntimeException statusException) {
                exception.addSuppressed(statusException);
            }

            throw exception;
        }
    }
}
