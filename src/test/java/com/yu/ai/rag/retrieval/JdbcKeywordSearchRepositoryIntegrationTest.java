
package com.yu.ai.rag.retrieval;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class JdbcKeywordSearchRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KeywordSearchRepository repository;

    private UUID documentId;

    @BeforeEach
    void setUp() {

        documentId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO documents (
                    id,
                    original_filename,
                    content_hash,
                    status
                )
                VALUES (?, ?, ?, ?)
                """,
                documentId,
                "day15-keyword-it.txt",
                UUID.randomUUID().toString().replace("-", ""),
                "READY"
        );

        insertChunk(
                0,
                "产品型号 BGE-M3，版本 v2.1.0，"
                        + "订单 ORD_2026_0001，错误码 ERR-1007。"
        );

        insertChunk(
                1,
                "订单 ORD_2026_0002 的状态是 READY。"
        );

        insertChunk(
                2,
                "数据库连接失败时，检查 PostgreSQL "
                        + "服务和数据库连接配置。"
        );
    }

    @AfterEach
    void cleanUp() {

        if (documentId != null) {
            // document_chunks 配置了 ON DELETE CASCADE。
            jdbcTemplate.update(
                    "DELETE FROM documents WHERE id = ?",
                    documentId
            );
        }
    }

    private void insertChunk(
            int chunkIndex,
            String content) {

        jdbcTemplate.update(
                """
                INSERT INTO document_chunks (
                    id,
                    document_id,
                    chunk_index,
                    content,
                    page_number,
                    section_title,
                    embedding
                )
                VALUES (?, ?, ?, ?, ?, ?, NULL)
                """,
                UUID.randomUUID(),
                documentId,
                chunkIndex,
                content,
                null,
                "Day15 integration"
        );
    }

    @Test
    void shouldMatchEnglishIdentifiersCaseInsensitively() {

        List<KeywordRetrievedChunk> results =
                repository.search(
                        List.of(
                                "err-1007",
                                "bge-m3",
                                "v2.1.0"
                        ),
                        10
                );

        assertEquals(1, results.size());
        assertEquals(0, results.get(0).chunkIndex());
        assertEquals(3, results.get(0).matchedTerms());
    }

    @Test
    void shouldTreatOrderNumberUnderscoreLiterally() {

        List<KeywordRetrievedChunk> results =
                repository.search(
                        List.of("ORD_2026_0001"),
                        10
                );

        assertEquals(
                List.of(0),
                results.stream()
                        .map(KeywordRetrievedChunk::chunkIndex)
                        .toList()
        );
    }

    @Test
    void shouldRankChunksWithMoreMatchedTermsFirst() {

        List<KeywordRetrievedChunk> results =
                repository.search(
                        List.of("订单", "ORD_2026_0001"),
                        10
                );

        assertEquals(2, results.size());

        assertEquals(0, results.get(0).chunkIndex());
        assertEquals(2, results.get(0).matchedTerms());

        assertEquals(1, results.get(1).chunkIndex());
        assertEquals(1, results.get(1).matchedTerms());
    }

    @Test
    void shouldMatchChinesePhrase() {

        List<KeywordRetrievedChunk> results =
                repository.search(
                        List.of("数据库连接失败"),
                        10
                );

        assertTrue(
                results.stream().anyMatch(
                        result -> result.chunkIndex() == 2
                )
        );
    }
}
