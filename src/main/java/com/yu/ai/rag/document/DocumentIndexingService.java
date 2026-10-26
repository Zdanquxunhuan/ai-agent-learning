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
     * 执行文档索引。
     *
     * 可以由异步任务调用，也保留直接调用能力。
     */
    public List<TextChunk> index(
            UUID documentId,
            String originalFilename,
            String contentType,
            byte[] content
    ) {
        Objects.requireNonNull(documentId, "documentId must not be null");

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

        // 原子状态更新：防止重复执行同一文档。
        boolean started = documentRepository.startIndexing(documentId);

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

            // 4. 保存切片和向量。
            persistenceService.replaceAll(documentId, chunks);

            // 5. 全部成功后，文档进入 READY。
            boolean ready = documentRepository.markReady(documentId);

            if (!ready) {
                throw new IllegalStateException(
                        "Failed to mark document as READY: " + documentId
                );
            }

            return chunks;

        } catch (RuntimeException exception) {
            try {
                documentRepository.markDocumentFailed(
                        documentId,
                        buildFailureReason(exception)
                );
            } catch (RuntimeException statusException) {
                exception.addSuppressed(statusException);
            }

            throw exception;
        }
    }

    private String buildFailureReason(RuntimeException exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return exception.getClass().getSimpleName() + ": " + message;
    }
}