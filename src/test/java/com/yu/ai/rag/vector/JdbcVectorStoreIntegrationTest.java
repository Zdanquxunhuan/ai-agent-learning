
package com.yu.ai.rag.vector;

import com.yu.ai.rag.chunking.TextChunk;
import com.yu.ai.rag.embedding.EmbeddingResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "rag.storage.upload-dir=target/test-uploads"
})
@Transactional
class JdbcVectorStoreIntegrationTest {

    @Autowired
    private JdbcVectorStore vectorStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldOrderChunksBySimilarity() {
        UUID documentId = createDocument();

        vectorStore.upsert(
                chunk(documentId, 0, "最相关的内容"),
                embedding(1.0, 0.0));

        vectorStore.upsert(
                chunk(documentId, 1, "中等相关的内容"),
                embedding(1.0, 1.0));

        vectorStore.upsert(
                chunk(documentId, 2, "不太相关的内容"),
                embedding(0.0, 1.0));

        List<RetrievedChunk> results =
                vectorStore.similaritySearch(
                        vector(1.0, 0.0), 3);

        assertEquals(3, results.size());

        assertEquals(
                "最相关的内容",
                results.get(0).content());

        assertEquals(
                "中等相关的内容",
                results.get(1).content());

        assertEquals(
                "不太相关的内容",
                results.get(2).content());

        assertTrue(
                results.get(0).similarity()
                        > results.get(1).similarity());

        assertTrue(
                results.get(1).similarity()
                        > results.get(2).similarity());
    }

    @Test
    void shouldUpdateExistingChunkInsteadOfDuplicating() {
        UUID documentId = createDocument();

        vectorStore.upsert(
                chunk(documentId, 0, "旧内容"),
                embedding(1.0, 0.0));

        vectorStore.upsert(
                chunk(documentId, 0, "更新后的内容"),
                embedding(0.0, 1.0));

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM document_chunks
                WHERE document_id = ?
                  AND chunk_index = 0
                """,
                Integer.class,
                documentId);

        String content = jdbcTemplate.queryForObject(
                """
                SELECT content
                FROM document_chunks
                WHERE document_id = ?
                  AND chunk_index = 0
                """,
                String.class,
                documentId);

        assertEquals(1, count);
        assertEquals("更新后的内容", content);
    }

    @Test
    void shouldRejectIncorrectVectorDimensions() {
        UUID documentId = UUID.randomUUID();

        TextChunk chunk = chunk(
                documentId, 0, "维度错误测试");

        EmbeddingResult wrongEmbedding =
                new EmbeddingResult(
                        "test-model",
                        2,
                        List.of(1.0, 2.0));

        assertThrows(
                IllegalArgumentException.class,
                () -> vectorStore.upsert(
                        chunk, wrongEmbedding));
    }

    @Test
    void shouldClearVectorsAndPreserveChunks() {
        UUID documentId = createDocument();

        vectorStore.upsert(
                chunk(documentId, 0, "需要保留的原始文本"),
                embedding(1.0, 0.0));

        vectorStore.deleteByDocumentId(documentId);

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM document_chunks
                WHERE document_id = ?
                """,
                Integer.class,
                documentId);

        Boolean embeddingIsNull =
                jdbcTemplate.queryForObject(
                        """
                        SELECT embedding IS NULL
                        FROM document_chunks
                        WHERE document_id = ?
                          AND chunk_index = 0
                        """,
                        Boolean.class,
                        documentId);

        assertEquals(1, count);
        assertTrue(embeddingIsNull);
    }

    private UUID createDocument() {
        UUID documentId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO documents (
                    id,
                    original_filename,
                    content_hash,
                    status
                )
                VALUES (?, ?, ?, 'READY')
                """,
                documentId,
                "vector-store-test.txt",
                UUID.randomUUID().toString().replace("-", ""));

        return documentId;
    }

    private TextChunk chunk(
            UUID documentId,
            int index,
            String content) {

        return new TextChunk(
                documentId.toString(),
                index,
                content,
                index + 1,
                "测试章节");
    }

    private EmbeddingResult embedding(
            double first,
            double second) {

        return new EmbeddingResult(
                "test-embedding-v1",
                1024,
                vector(first, second));
    }

    private List<Double> vector(
            double first,
            double second) {

        List<Double> values = new ArrayList<>(
                Collections.nCopies(1024, 0.0));

        values.set(0, first);
        values.set(1, second);

        return values;
    }
}
