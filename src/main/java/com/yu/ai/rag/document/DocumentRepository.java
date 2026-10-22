package com.yu.ai.rag.document;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Objects;
import java.util.UUID;

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


    /**
     * 条件更新文档状态。
     *
     * 只有当前状态与 expectedStatus 一致时才更新，
     * 避免重复触发时覆盖已经发生的状态变化。
     *
     * @return true 表示更新成功，false 表示当前状态不匹配或文档不存在
     */
    public boolean transitionStatus(
            UUID documentId,
            DocumentStatus expectedStatus,
            DocumentStatus newStatus
    ) {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(expectedStatus, "expectedStatus must not be null");
        Objects.requireNonNull(newStatus, "newStatus must not be null");

        boolean allowed =
                (expectedStatus == DocumentStatus.UPLOADED
                        && newStatus == DocumentStatus.PROCESSING)
                        || (expectedStatus == DocumentStatus.PROCESSING
                        && (newStatus == DocumentStatus.READY
                        || newStatus == DocumentStatus.FAILED));

        if (!allowed) {
            throw new IllegalArgumentException(
                    "Invalid document status transition: "
                            + expectedStatus + " -> " + newStatus
            );
        }

        int updated = jdbcTemplate.update(
                """
                UPDATE documents
                SET status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = ?
                """,
                newStatus.name(),
                documentId,
                expectedStatus.name()
        );

        return updated == 1;
    }

}