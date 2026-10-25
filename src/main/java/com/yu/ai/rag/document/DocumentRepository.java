package com.yu.ai.rag.document;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Document metadata persistence and lookup. */
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
                document.status().name());
    }

    public boolean transitionStatus(
            UUID documentId,
            DocumentStatus expectedStatus,
            DocumentStatus newStatus) {
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
                            + expectedStatus + " -> " + newStatus);
        }

        int updated = jdbcTemplate.update(
                """
                UPDATE documents
                SET status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = ?
                """,
                newStatus.name(),
                documentId,
                expectedStatus.name());
        return updated == 1;
    }

    /** Resolve the user-visible source filename for a citation. */
    public Optional<String> findOriginalFilenameById(UUID documentId) {
        List<String> names = jdbcTemplate.query(
                "SELECT original_filename FROM documents WHERE id = ?",
                (resultSet, rowNum) -> resultSet.getString("original_filename"),
                documentId);
        return names.stream().findFirst();
    }
}
