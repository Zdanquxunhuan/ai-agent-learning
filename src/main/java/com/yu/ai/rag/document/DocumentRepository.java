package com.yu.ai.rag.document;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DocumentRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

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

    public Optional<DocumentRecord> findById(UUID documentId) {
        List<DocumentRecord> records = jdbcTemplate.query(
                """
                SELECT id, original_filename, content_hash, status
                FROM documents
                WHERE id = ?
                """,
                (rs, rowNum) -> new DocumentRecord(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("original_filename"),
                        rs.getString("content_hash"),
                        DocumentStatus.valueOf(rs.getString("status"))
                ),
                documentId
        );

        return records.stream().findFirst();
    }

    /**
     * 原有状态流转方法，保留兼容。
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

        return jdbcTemplate.update(
                """
                UPDATE documents
                SET status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = ?
                """,
                newStatus.name(),
                documentId,
                expectedStatus.name()
        ) == 1;
    }

    /**
     * 提交索引任务。
     *
     * 只有 UPLOADED 或 FAILED 文档可以提交。
     * 只有 PENDING 或 FAILED 任务可以重新排队。
     */
    public boolean queueIndexing(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId must not be null");

        return jdbcTemplate.update(
                """
                UPDATE documents
                SET indexing_task_status = 'QUEUED',
                    indexing_attempt = indexing_attempt + 1,
                    indexing_started_at = NULL,
                    indexing_finished_at = NULL,
                    indexing_failure_reason = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status IN ('UPLOADED', 'FAILED')
                  AND indexing_task_status IN ('PENDING', 'FAILED')
                """,
                documentId
        ) == 1;
    }

    /**
     * 将排队任务标记为运行中。
     */
    public boolean markTaskRunning(UUID documentId) {
        return jdbcTemplate.update(
                """
                UPDATE documents
                SET indexing_task_status = 'RUNNING',
                    indexing_started_at = CURRENT_TIMESTAMP,
                    indexing_finished_at = NULL,
                    indexing_failure_reason = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND indexing_task_status = 'QUEUED'
                """,
                documentId
        ) == 1;
    }

    /**
     * 将任务标记为成功。
     */
    public boolean markTaskSucceeded(UUID documentId) {
        return jdbcTemplate.update(
                """
                UPDATE documents
                SET indexing_task_status = 'SUCCEEDED',
                    indexing_finished_at = CURRENT_TIMESTAMP,
                    indexing_failure_reason = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND indexing_task_status = 'RUNNING'
                """,
                documentId
        ) == 1;
    }

    /**
     * 将任务标记为失败，并保存可诊断原因。
     */
    public boolean markTaskFailed(
            UUID documentId,
            String failureReason
    ) {
        String reason = normalizeFailureReason(failureReason);

        return jdbcTemplate.update(
                """
                UPDATE documents
                SET indexing_task_status = 'FAILED',
                    indexing_finished_at = CURRENT_TIMESTAMP,
                    indexing_failure_reason = ?,
                    status = CASE
                        WHEN status IN ('UPLOADED', 'PROCESSING')
                            THEN 'FAILED'
                        ELSE status
                    END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND indexing_task_status IN ('QUEUED', 'RUNNING')
                """,
                reason,
                documentId
        ) == 1;
    }

    /**
     * 开始执行文档索引。
     *
     * UPLOADED：首次索引。
     * FAILED：失败后的重新索引。
     */
    public boolean startIndexing(UUID documentId) {
        return jdbcTemplate.update(
                """
                UPDATE documents
                SET status = 'PROCESSING',
                    indexing_started_at = CURRENT_TIMESTAMP,
                    indexing_finished_at = NULL,
                    indexing_failure_reason = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status IN ('UPLOADED', 'FAILED')
                """,
                documentId
        ) == 1;
    }

    /**
     * 索引成功后更新文档状态。
     */
    public boolean markReady(UUID documentId) {
        return jdbcTemplate.update(
                """
                UPDATE documents
                SET status = 'READY',
                    indexing_finished_at = CURRENT_TIMESTAMP,
                    indexing_failure_reason = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = 'PROCESSING'
                """,
                documentId
        ) == 1;
    }

    /**
     * 索引执行失败后更新文档状态。
     */
    public boolean markDocumentFailed(
            UUID documentId,
            String failureReason
    ) {
        return jdbcTemplate.update(
                """
                UPDATE documents
                SET status = 'FAILED',
                    indexing_finished_at = CURRENT_TIMESTAMP,
                    indexing_failure_reason = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = 'PROCESSING'
                """,
                normalizeFailureReason(failureReason),
                documentId
        ) == 1;
    }

    /**
     * 查询文档和索引任务的最新状态。
     */
    public Optional<DocumentIndexingStatusResponse> findIndexingStatus(
            UUID documentId
    ) {
        List<DocumentIndexingStatusResponse> results = jdbcTemplate.query(
                """
                SELECT
                    id,
                    status,
                    indexing_task_status,
                    indexing_attempt,
                    indexing_started_at,
                    indexing_finished_at,
                    indexing_failure_reason
                FROM documents
                WHERE id = ?
                """,
                (rs, rowNum) -> new DocumentIndexingStatusResponse(
                        UUID.fromString(rs.getString("id")),
                        DocumentStatus.valueOf(rs.getString("status")),
                        IndexingTaskStatus.valueOf(
                                rs.getString("indexing_task_status")
                        ),
                        rs.getInt("indexing_attempt"),
                        rs.getObject(
                                "indexing_started_at",
                                OffsetDateTime.class
                        ),
                        rs.getObject(
                                "indexing_finished_at",
                                OffsetDateTime.class
                        ),
                        rs.getString("indexing_failure_reason")
                ),
                documentId
        );

        return results.stream().findFirst();
    }

    /**
     * 原有引用来源查询方法，保留。
     */
    public Optional<String> findOriginalFilenameById(UUID documentId) {
        List<String> names = jdbcTemplate.query(
                """
                SELECT original_filename
                FROM documents
                WHERE id = ?
                """,
                (rs, rowNum) -> rs.getString("original_filename"),
                documentId
        );

        return names.stream().findFirst();
    }

    private String normalizeFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            return "Unknown indexing failure";
        }

        String normalized = failureReason
                .replace('\r', ' ')
                .replace('\n', ' ');

        return normalized.length() <= 500
                ? normalized
                : normalized.substring(0, 500);
    }

    /**
     * 根据文件内容哈希查找文档。
     */
    public Optional<DocumentRecord> findByContentHash(
            String contentHash
    ) {
        List<DocumentRecord> records = jdbcTemplate.query(
                """
                SELECT id, original_filename, content_hash, status
                FROM documents
                WHERE content_hash = ?
                """,
                (rs, rowNum) -> new DocumentRecord(
                        rs.getObject("id", UUID.class),
                        rs.getString("original_filename"),
                        rs.getString("content_hash"),
                        DocumentStatus.valueOf(rs.getString("status"))
                ),
                contentHash
        );

        return records.stream().findFirst();
    }

    /**
     * 标记文档正在删除。
     *
     * 正在索引的文档不允许删除。
     */
    public boolean markDeleting(UUID documentId) {
        return jdbcTemplate.update(
                """
                UPDATE documents
                SET status = 'DELETING',
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status IN (
                      'UPLOADED',
                      'READY',
                      'FAILED',
                      'DELETING'
                  )
                  AND indexing_task_status NOT IN (
                      'QUEUED',
                      'RUNNING'
                  )
                """,
                documentId
        ) == 1;
    }

    /**
     * 为文档重建索引做准备。
     *
     * 将文档恢复为 UPLOADED，
     * 将任务恢复为 PENDING。
     */
    public boolean prepareForRebuild(UUID documentId) {
        return jdbcTemplate.update(
                """
                UPDATE documents
                SET status = 'UPLOADED',
                    indexing_task_status = 'PENDING',
                    indexing_started_at = NULL,
                    indexing_finished_at = NULL,
                    indexing_failure_reason = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status IN (
                      'UPLOADED',
                      'READY',
                      'FAILED'
                  )
                  AND indexing_task_status NOT IN (
                      'QUEUED',
                      'RUNNING'
                  )
                """,
                documentId
        ) == 1;
    }

    /**
     * 仅允许删除已经完成切片清理的文档元数据。
     */
    public boolean deleteMetadata(UUID documentId) {
        return jdbcTemplate.update(
                """
                DELETE FROM documents
                WHERE id = ?
                  AND status = 'DELETING'
                """,
                documentId
        ) == 1;
    }
}