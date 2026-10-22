
package com.yu.ai.rag.chunking;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
public class DocumentChunkRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 批量保存文档切片。
     */
    public void saveAll(UUID documentId, List<TextChunk> chunks) {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(chunks, "chunks must not be null");

        if (chunks.isEmpty()) {
            return;
        }

        // 确保所有切片都属于当前文档。
        String expectedDocumentId = documentId.toString();

        for (TextChunk chunk : chunks) {
            if (!expectedDocumentId.equals(chunk.documentId())) {
                throw new IllegalArgumentException(
                        "Chunk documentId does not match: "
                                + chunk.documentId()
                );
            }
        }

        String sql = """
                INSERT INTO document_chunks (
                    id,
                    document_id,
                    chunk_index,
                    content,
                    page_number,
                    section_title,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {

            @Override
            public void setValues(
                    PreparedStatement ps,
                    int index
            ) throws SQLException {

                TextChunk chunk = chunks.get(index);

                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, documentId);
                ps.setInt(3, chunk.chunkIndex());
                ps.setString(4, chunk.text());

                if (chunk.pageNumber() == null) {
                    ps.setNull(5, Types.INTEGER);
                } else {
                    ps.setInt(5, chunk.pageNumber());
                }

                if (chunk.sectionTitle() == null) {
                    ps.setNull(6, Types.VARCHAR);
                } else {
                    ps.setString(6, chunk.sectionTitle());
                }
            }

            @Override
            public int getBatchSize() {
                return chunks.size();
            }
        });
    }

    /**
     * 删除指定文档的全部切片。
     */
    public int deleteByDocumentId(UUID documentId) {
        return jdbcTemplate.update(
                "DELETE FROM document_chunks WHERE document_id = ?",
                documentId
        );
    }

    /**
     * 查询指定文档的切片数量。
     */
    public int countByDocumentId(UUID documentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE document_id = ?",
                Integer.class,
                documentId
        );

        return count == null ? 0 : count;
    }
}
