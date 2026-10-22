
package com.yu.ai.rag.document;

import com.yu.ai.rag.parser.ParserRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.yu.ai.rag.parser.DocumentParser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "rag.storage.upload-dir=target/test-uploads"
})
@Transactional
class DocumentIndexingFailureIntegrationTest {

    @Autowired
    private DocumentApplicationService documentApplicationService;

    @Autowired
    private DocumentIndexingService documentIndexingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ParserRegistry parserRegistry;

    private Path uploadedFile;

    @AfterEach
    void cleanUp() throws IOException {
        if (uploadedFile != null) {
            Files.deleteIfExists(uploadedFile);
        }
    }

    @Test
    void parsingFailureShouldPersistFailedStatus() throws IOException {
        // 模拟解析器抛出异常。
        // 创建模拟解析器
        DocumentParser parser = org.mockito.Mockito.mock(
                DocumentParser.class
        );

// 模拟解析器选择成功
        when(parserRegistry.getParser("failure.txt", "text/plain"))
                .thenReturn(parser);

// 模拟真正解析时发生异常
        when(parser.parse(
                org.mockito.ArgumentMatchers.any(byte[].class)
        )).thenThrow(
                new IllegalStateException("simulated parse failure")
        );

        // 1. 通过真实上传服务创建文档。
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "failure.txt",
                "text/plain",
                "用于测试解析失败的内容".getBytes(
                        StandardCharsets.UTF_8
                )
        );

        DocumentUploadResponse upload =
                documentApplicationService.upload(file);

        UUID documentId = upload.documentId();

        assertEquals(DocumentStatus.UPLOADED, upload.status());

        // 2. 读取上传后的原始文件。
        uploadedFile = Path.of(
                "target",
                "test-uploads",
                documentId.toString()
        );

        assertTrue(Files.exists(uploadedFile));

        byte[] content = Files.readAllBytes(uploadedFile);

        // 3. 执行索引，验证异常被正常抛出。
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> documentIndexingService.index(
                        documentId,
                        upload.originalFilename(),
                        upload.contentType(),
                        content
                )
        );

        assertEquals(
                "simulated parse failure",
                exception.getMessage()
        );

        // 4. 直接查询 PostgreSQL，验证失败状态已写入。
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM documents WHERE id = ?",
                String.class,
                documentId
        );

        assertEquals("FAILED", status);
    }
}
