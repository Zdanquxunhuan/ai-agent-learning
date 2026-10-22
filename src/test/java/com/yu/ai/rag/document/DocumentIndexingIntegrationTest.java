
package com.yu.ai.rag.document;

import com.yu.ai.rag.chunking.DocumentChunkRepository;
import com.yu.ai.rag.chunking.TextChunk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "rag.storage.upload-dir=target/test-uploads"
})
@Transactional
class DocumentIndexingIntegrationTest {

    @Autowired
    private DocumentApplicationService documentApplicationService;

    @Autowired
    private DocumentIndexingService documentIndexingService;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Path uploadedFile;

    @AfterEach
    void cleanUpUploadedFile() throws IOException {
        if (uploadedFile != null) {
            Files.deleteIfExists(uploadedFile);
        }
    }

    @Test
    void uploadAndIndexShouldPersistChunksAndMarkReady()
            throws IOException {

        // 1. 准备一份较长的测试文档。
        String paragraph1 =
                "RAG 系统先解析文档，再进行文本切片并保存到 PostgreSQL。";

        String paragraph2 =
                "每个切片都应保留原文顺序，方便后续检索和引用。";

        String source = (
                paragraph1 + "\n\n" + paragraph2 + "\n\n"
        ).repeat(20);

        byte[] content = source.getBytes(StandardCharsets.UTF_8);

        // 2. 调用真实上传服务，保存文件和文档元数据。
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "day11-integration.txt",
                "text/plain",
                content
        );

        DocumentUploadResponse upload =
                documentApplicationService.upload(file);

        UUID documentId = upload.documentId();

        assertEquals(DocumentStatus.UPLOADED, upload.status());

        // 3. 读取上传服务实际保存的原始文件。
        uploadedFile = Path.of(
                "target",
                "test-uploads",
                documentId.toString()
        );

        assertTrue(
                Files.exists(uploadedFile),
                "上传后的原始文件应该存在"
        );

        byte[] storedContent = Files.readAllBytes(uploadedFile);

        // 4. 执行真实索引流程：解析 -> 切片 -> 数据库保存。
        List<TextChunk> chunks = documentIndexingService.index(
                documentId,
                upload.originalFilename(),
                upload.contentType(),
                storedContent
        );

        assertTrue(chunks.size() > 1, "测试文档应该产生多个切片");

        // 5. 验证数据库中的切片数量。
        int savedCount =
                documentChunkRepository.countByDocumentId(documentId);

        assertEquals(chunks.size(), savedCount);

        // 6. 验证数据库中的切片内容和顺序。
        List<String> savedContents = jdbcTemplate.queryForList(
                """
                SELECT content
                FROM document_chunks
                WHERE document_id = ?
                ORDER BY chunk_index
                """,
                String.class,
                documentId
        );

        assertEquals(
                chunks.stream().map(TextChunk::text).toList(),
                savedContents
        );

        // 7. 验证文档最终状态。
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM documents WHERE id = ?",
                String.class,
                documentId
        );

        assertEquals("READY", status);
    }
}
