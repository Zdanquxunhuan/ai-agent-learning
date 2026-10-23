
package com.yu.ai.rag.vector;

import com.yu.ai.rag.chunking.TextChunk;
import com.yu.ai.rag.embedding.EmbeddingResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

@Repository
public class JdbcVectorStore implements VectorStore {

    private static final int VECTOR_DIMENSIONS = 1024;

    private final JdbcTemplate jdbcTemplate;

    public JdbcVectorStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsert(
            TextChunk chunk,
            EmbeddingResult embedding) {

        if (chunk == null || embedding == null) {
            throw new IllegalArgumentException(
                    "chunk and embedding must not be null");
        }

        validateVector(embedding.vector());

        if (embedding.dimensions() != VECTOR_DIMENSIONS) {
            throw new IllegalArgumentException(
                    "Expected " + VECTOR_DIMENSIONS
                            + " dimensions but received "
                            + embedding.dimensions());
        }

        UUID documentId = parseDocumentId(chunk.documentId());
        UUID chunkId = UUID.randomUUID();

        String sql = """
                INSERT INTO document_chunks (
                    id,
                    document_id,
                    chunk_index,
                    content,
                    page_number,
                    section_title,
                    embedding
                )
                VALUES (?, ?, ?, ?, ?, ?, ?::vector)
                ON CONFLICT (document_id, chunk_index)
                DO UPDATE SET
                    content = EXCLUDED.content,
                    page_number = EXCLUDED.page_number,
                    section_title = EXCLUDED.section_title,
                    embedding = EXCLUDED.embedding
                """;

        jdbcTemplate.update(
                sql,
                chunkId,
                documentId,
                chunk.chunkIndex(),
                chunk.text(),
                chunk.pageNumber(),
                chunk.sectionTitle(),
                toVectorLiteral(embedding.vector())
        );
    }


    @Override
    public void deleteByDocumentId(UUID documentId) {
        if (documentId == null) {
            throw new IllegalArgumentException(
                    "documentId must not be null");
        }

        String sql = """
            UPDATE document_chunks
            SET embedding = NULL
            WHERE document_id = ?
            """;

        jdbcTemplate.update(sql, documentId);
    }


    @Override
    public List<RetrievedChunk> similaritySearch(
            List<Double> queryVector,
            int topK) {

        validateVector(queryVector);

        if (topK <= 0) {
            throw new IllegalArgumentException(
                    "topK must be positive");
        }

        String vectorLiteral = toVectorLiteral(queryVector);

        String sql = """
                SELECT
                    id,
                    document_id,
                    chunk_index,
                    content,
                    page_number,
                    section_title,
                    1 - (embedding <=> ?::vector) AS similarity
                FROM document_chunks
                WHERE embedding IS NOT NULL
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new RetrievedChunk(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("document_id")),
                        rs.getInt("chunk_index"),
                        rs.getString("content"),
                        (Integer) rs.getObject("page_number"),
                        rs.getString("section_title"),
                        rs.getDouble("similarity")
                ),
                vectorLiteral,
                vectorLiteral,
                topK
        );
    }

    private void validateVector(List<Double> vector) {
        if (vector == null || vector.isEmpty()) {
            throw new IllegalArgumentException(
                    "Vector must not be null or empty");
        }

        if (vector.size() != VECTOR_DIMENSIONS) {
            throw new IllegalArgumentException(
                    "Expected " + VECTOR_DIMENSIONS
                            + " dimensions but received "
                            + vector.size());
        }

        for (Double value : vector) {
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Vector values must be finite numbers");
            }
        }
    }

    private String toVectorLiteral(List<Double> vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");

        for (Double value : vector) {
            joiner.add(Double.toString(value));
        }

        return joiner.toString();
    }

    private UUID parseDocumentId(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException(
                    "chunk.documentId must not be blank");
        }

        try {
            return UUID.fromString(documentId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "chunk.documentId must be a valid UUID", ex);
        }
    }
}
