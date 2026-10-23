
package com.yu.ai.rag.retrieval;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Repository
public class JdbcKeywordSearchRepository
        implements KeywordSearchRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcKeywordSearchRepository(
            JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<KeywordRetrievedChunk> search(
            List<String> keywords,
            int topK) {

        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }

        if (topK <= 0) {
            throw new IllegalArgumentException(
                    "topK must be positive");
        }

        // 清理空关键词并去重。
        List<String> terms = keywords.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(term -> !term.isEmpty())
                .distinct()
                .toList();

        if (terms.isEmpty()) {
            return List.of();
        }

        // 每命中一个关键词，得 1 分。
        String scoreExpression = terms.stream()
                .map(term ->
                        "CASE WHEN STRPOS("
                                + "LOWER(dc.content), LOWER(?)"
                                + ") > 0 THEN 1 ELSE 0 END")
                .collect(Collectors.joining(" + "));

        // 任意一个关键词命中，就进入候选结果。
        String matchExpression = terms.stream()
                .map(term ->
                        "STRPOS(LOWER(dc.content), LOWER(?)) > 0")
                .collect(Collectors.joining(" OR "));

        String sql = """
                SELECT
                    dc.id,
                    dc.document_id,
                    dc.chunk_index,
                    dc.content,
                    dc.page_number,
                    dc.section_title,
                    (%s) AS matched_terms
                FROM document_chunks dc
                WHERE %s
                ORDER BY
                    matched_terms DESC,
                    dc.document_id ASC,
                    dc.chunk_index ASC
                LIMIT ?
                """.formatted(
                scoreExpression,
                matchExpression
        );

        // SQL 参数顺序：
        // 1. 计算匹配分数的关键词
        // 2. 筛选候选结果的关键词
        // 3. topK
        List<Object> parameters = new ArrayList<>();

        parameters.addAll(terms);
        parameters.addAll(terms);
        parameters.add(topK);

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new KeywordRetrievedChunk(
                        rs.getObject("id", java.util.UUID.class),
                        rs.getObject(
                                "document_id",
                                java.util.UUID.class
                        ),
                        rs.getInt("chunk_index"),
                        rs.getString("content"),
                        (Integer) rs.getObject("page_number"),
                        rs.getString("section_title"),
                        rs.getInt("matched_terms")
                ),
                parameters.toArray()
        );
    }
}
