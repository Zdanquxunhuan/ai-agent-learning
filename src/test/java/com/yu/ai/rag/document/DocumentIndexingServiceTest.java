package com.yu.ai.rag.document;


import com.yu.ai.rag.chunking.TextChunk;
import com.yu.ai.rag.chunking.TextChunker;
import com.yu.ai.rag.parser.ParsedDocument;
import com.yu.ai.rag.parser.ParserRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

public class DocumentIndexingServiceTest {


    @Test
    void repeatedIndexingShouldBeRejectedBeforeParsing() {
        UUID documentId = UUID.randomUUID();

        DocumentRepository documentRepository =
                mock(DocumentRepository.class);

        ParserRegistry parserRegistry =
                mock(ParserRegistry.class);

        DocumentChunkPersistenceService persistenceService =
                mock(DocumentChunkPersistenceService.class);

        // 文档已经处于 PROCESSING 或 READY 等非 UPLOADED 状态。
        // 条件状态更新失败，拒绝本次重复索引。
        when(documentRepository.transitionStatus(
                documentId,
                DocumentStatus.UPLOADED,
                DocumentStatus.PROCESSING
        )).thenReturn(false);

        DocumentIndexingService service =
                new DocumentIndexingService(
                        parserRegistry,
                        new TextChunker(),
                        documentRepository,
                        persistenceService,
                        500,
                        50
                );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.index(
                        documentId,
                        "test.txt",
                        "text/plain",
                        "测试内容".getBytes(
                                java.nio.charset.StandardCharsets.UTF_8
                        )
                )
        );

        assertTrue(
                exception.getMessage().contains(
                        "Document cannot start indexing"
                )
        );

        // 必须先成功取得索引资格，才能调用解析器和持久化服务。
        verifyNoInteractions(parserRegistry, persistenceService);

        verify(documentRepository).transitionStatus(
                documentId,
                DocumentStatus.UPLOADED,
                DocumentStatus.PROCESSING
        );
    }

}
