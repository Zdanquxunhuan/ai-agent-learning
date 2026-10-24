package com.yu.ai.rag.retrieval;

import com.yu.ai.rag.common.BusinessException;
import com.yu.ai.rag.common.ErrorCode;
import com.yu.ai.rag.vector.RetrievedChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Combines semantic and keyword retrieval with Reciprocal Rank Fusion (RRF).
 */
@Service
public class HybridRetriever {

    /** Standard RRF rank constant. It keeps small rank differences from dominating. */
    private static final int RRF_K = 60;

    private static final Comparator<HybridRetrievedChunk> RESULT_ORDER =
            Comparator.comparingDouble(HybridRetrievedChunk::rrfScore)
                    .reversed()
                    .thenComparing(
                            HybridRetrievedChunk::semanticRank,
                            Comparator.nullsLast(Comparator.<Integer>naturalOrder())
                    )
                    .thenComparing(
                            HybridRetrievedChunk::keywordRank,
                            Comparator.nullsLast(Comparator.<Integer>naturalOrder())
                    )
                    .thenComparing(HybridRetrievedChunk::documentId)
                    .thenComparingInt(HybridRetrievedChunk::chunkIndex)
                    .thenComparing(HybridRetrievedChunk::chunkId);

    private final SemanticRetriever semanticRetriever;
    private final KeywordRetriever keywordRetriever;

    public HybridRetriever(
            SemanticRetriever semanticRetriever,
            KeywordRetriever keywordRetriever) {
        this.semanticRetriever = semanticRetriever;
        this.keywordRetriever = keywordRetriever;
    }

    /**
     * Runs both retrieval strategies, fuses their ranks, deduplicates by chunkId,
     * and returns at most topK chunks.
     */
    public List<HybridRetrievedChunk> search(String query, int topK) {
        validate(query, topK);

        List<RetrievedChunk> semanticResults =
                semanticRetriever.search(query, topK);

        // Punctuation-only input can still be semantically meaningful to an
        // embedding model even when the keyword tokenizer finds no tokens.
        List<KeywordRetrievedChunk> keywordResults =
                KeywordRetriever.tokenize(query).isEmpty()
                        ? List.of()
                        : keywordRetriever.search(query, topK);

        Map<UUID, Candidate> candidates = new LinkedHashMap<>();

        for (int i = 0; i < semanticResults.size(); i++) {
            RetrievedChunk result = semanticResults.get(i);
            Candidate candidate = candidates.computeIfAbsent(
                    result.chunkId(),
                    ignored -> Candidate.fromSemantic(result)
            );

            // Keep the first rank if an upstream implementation ever returns
            // the same chunk more than once.
            if (candidate.semanticRank == null) {
                candidate.semanticRank = i + 1;
                candidate.similarity = result.similarity();
            }
        }

        for (int i = 0; i < keywordResults.size(); i++) {
            KeywordRetrievedChunk result = keywordResults.get(i);
            Candidate candidate = candidates.computeIfAbsent(
                    result.chunkId(),
                    ignored -> Candidate.fromKeyword(result)
            );

            if (candidate.keywordRank == null) {
                candidate.keywordRank = i + 1;
                candidate.matchedTerms = result.matchedTerms();
            }
        }

        List<HybridRetrievedChunk> fusedResults = new ArrayList<>();
        for (Candidate candidate : candidates.values()) {
            fusedResults.add(candidate.toResult());
        }

        return fusedResults.stream()
                .sorted(RESULT_ORDER)
                .limit(topK)
                .toList();
    }

    private void validate(String query, int topK) {
        if (query == null || query.isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "query 不能为空"
            );
        }

        if (query.length() > 1000) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "query 长度不能超过 1000 个字符"
            );
        }

        if (topK < 1 || topK > 20) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "topK 必须在 1 到 20 之间"
            );
        }
    }

    private static final class Candidate {
        private final UUID chunkId;
        private final UUID documentId;
        private final int chunkIndex;
        private final String content;
        private final Integer pageNumber;
        private final String sectionTitle;

        private Integer semanticRank;
        private Integer keywordRank;
        private Double similarity;
        private Integer matchedTerms;

        private Candidate(
                UUID chunkId,
                UUID documentId,
                int chunkIndex,
                String content,
                Integer pageNumber,
                String sectionTitle) {
            this.chunkId = chunkId;
            this.documentId = documentId;
            this.chunkIndex = chunkIndex;
            this.content = content;
            this.pageNumber = pageNumber;
            this.sectionTitle = sectionTitle;
        }

        private static Candidate fromSemantic(RetrievedChunk result) {
            Candidate candidate = new Candidate(
                    result.chunkId(),
                    result.documentId(),
                    result.chunkIndex(),
                    result.content(),
                    result.pageNumber(),
                    result.sectionTitle()
            );
            candidate.similarity = result.similarity();
            return candidate;
        }

        private static Candidate fromKeyword(KeywordRetrievedChunk result) {
            Candidate candidate = new Candidate(
                    result.chunkId(),
                    result.documentId(),
                    result.chunkIndex(),
                    result.content(),
                    result.pageNumber(),
                    result.sectionTitle()
            );
            candidate.matchedTerms = result.matchedTerms();
            return candidate;
        }

        private HybridRetrievedChunk toResult() {
            double score = 0.0;
            if (semanticRank != null) {
                score += 1.0 / (RRF_K + semanticRank);
            }
            if (keywordRank != null) {
                score += 1.0 / (RRF_K + keywordRank);
            }

            return new HybridRetrievedChunk(
                    chunkId,
                    documentId,
                    chunkIndex,
                    content,
                    pageNumber,
                    sectionTitle,
                    semanticRank,
                    keywordRank,
                    similarity,
                    matchedTerms,
                    score
            );
        }
    }
}
