package com.yu.ai.rag.document;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 文档数据库访问层
 */
@Repository
public class DocumentRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存文档元数据
     */
    public void save(DocumentRecord document) {

        String sql = """
                INSERT INTO documents (
                    id,
                    original_filename,
                    content_hash,
                    status
                )
                VALUES (?, ?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                document.id(),
                document.originalFilename(),
                document.contentHash(),
                document.status().name()
        );
    }
}