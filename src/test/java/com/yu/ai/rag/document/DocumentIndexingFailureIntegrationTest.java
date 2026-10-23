
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
    void parsingFailureShouldPersistFailedStatus() throws Exception {
        // 1. 模拟解析器抛出异常
        DocumentParser parser = org.mockito.Mockito.mock(
                DocumentParser.class
        );

        when(parserRegistry.getParser("failure.txt", "text/plain"))
                .thenReturn(parser);

        when(parser.parse(
                org.mockito.ArgumentMatchers.any(byte[].class)
        )).thenThrow(
                new IllegalStateException("simulated parse failure")
        );

        // 2. 准备上传文件
        byte[] content = "用于测试解析失败的内容".getBytes(
                StandardCharsets.UTF_8
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "failure.txt",
                "text/plain",
                content
        );

        // 3. 上传时会自动触发索引，因此这里预期抛出异常
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> documentApplicationService.upload(file)
        );

        assertEquals(
                "simulated parse failure",
                exception.getMessage()
        );

        // 4. 根据文件内容的 SHA-256 查询文档记录
        String contentHash = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(content)
        );

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM documents WHERE content_hash = ?",
                String.class,
                contentHash
        );

        // 5. 验证索引失败状态已持久化
        assertEquals("FAILED", status);
    }

}
