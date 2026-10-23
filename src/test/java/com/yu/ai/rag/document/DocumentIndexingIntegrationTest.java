
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

}
