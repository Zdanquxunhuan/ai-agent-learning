
package com.yu.ai.rag.document;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentApplicationServiceTest {

    @TempDir
    Path tempDir;

    private DocumentRepository documentRepository;
    private DocumentApplicationService service;

    @BeforeEach
    void setUp() {
        documentRepository = mock(DocumentRepository.class);

        DocumentUploadValidator validator =
                new DocumentUploadValidator();

        service = new DocumentApplicationService(
                documentRepository,
                validator,
                tempDir.toString()
        );
    }

    /**
     * 测试1：正常上传
     */
    @Test
    void shouldUploadValidTxtFile() throws Exception {
        byte[] content = "RAG Day 7 upload test"
                .getBytes(StandardCharsets.UTF_8);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.txt",
                "text/plain",
                content
        );

        DocumentUploadResponse response = service.upload(file);

        assertNotNull(response.documentId());
        assertEquals("sample.txt", response.originalFilename());
        assertEquals(content.length, response.sizeBytes());
        assertEquals("text/plain", response.contentType());
        assertEquals(64, response.contentHash().length());
        assertEquals(DocumentStatus.UPLOADED, response.status());

        // 原文件已经保存
        Path savedFile = tempDir.resolve(
                response.documentId().toString()
        );

        assertTrue(Files.exists(savedFile));
        assertArrayEquals(content, Files.readAllBytes(savedFile));

        // 元数据已经交给 Repository 保存
        verify(documentRepository, times(1))
                .save(any(DocumentRecord.class));
    }

    /**
     * 测试2：拒绝空文件
     */
    @Test
    void shouldRejectEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        DocumentValidationException exception =
                assertThrows(
                        DocumentValidationException.class,
                        () -> service.upload(file)
                );

        assertEquals("上传文件不能为空", exception.getMessage());

        verifyNoInteractions(documentRepository);
    }

    /**
     * 测试3：拒绝超过10MB的文件
     */
    @Test
    void shouldRejectOversizedFile() {
        byte[] content = new byte[10 * 1024 * 1024 + 1];

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.txt",
                "text/plain",
                content
        );

        DocumentValidationException exception =
                assertThrows(
                        DocumentValidationException.class,
                        () -> service.upload(file)
                );

        assertEquals("上传文件不能超过 10MB", exception.getMessage());

        verifyNoInteractions(documentRepository);
    }

    /**
     * 测试4：拒绝非法扩展名
     */
    @Test
    void shouldRejectUnsupportedFileType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.exe",
                "application/octet-stream",
                "test".getBytes(StandardCharsets.UTF_8)
        );

        DocumentValidationException exception =
                assertThrows(
                        DocumentValidationException.class,
                        () -> service.upload(file)
                );

        assertTrue(
                exception.getMessage().contains("不支持该文件类型")
        );

        verifyNoInteractions(documentRepository);
    }

    /**
     * 测试5：拒绝路径穿越攻击文件名
     */
    @Test
    void shouldRejectPathTraversalFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../evil.txt",
                "text/plain",
                "malicious test".getBytes(StandardCharsets.UTF_8)
        );

        DocumentValidationException exception =
                assertThrows(
                        DocumentValidationException.class,
                        () -> service.upload(file)
                );

        assertEquals(
                "文件名不能包含路径信息",
                exception.getMessage()
        );

        verifyNoInteractions(documentRepository);
    }
}
