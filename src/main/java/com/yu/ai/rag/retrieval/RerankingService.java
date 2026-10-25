package com.yu.ai.rag.retrieval;

import com.yu.ai.rag.common.BusinessException;
import com.yu.ai.rag.common.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Retrieves a larger hybrid candidate pool, then reranks it to the requested Top-K.
 */
@Service
public class RerankingService {

    private final HybridRetriever hybridRetriever;
    private final Reranker reranker;

    public RerankingService(
            HybridRetriever hybridRetriever,
            Reranker reranker) {
        this.hybridRetriever = hybridRetriever;
        this.reranker = reranker;
    }

    public RerankerResult search(String query, int topK, int candidateK) {
        validate(query, topK, candidateK);

        List<HybridRetrievedChunk> candidates =
                hybridRetriever.search(query, candidateK);

        return reranker.rerank(query, candidates, topK);
    }

    private void validate(String query, int topK, int candidateK) {
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

        if (candidateK < 1 || candidateK > 20) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "candidateK 必须在 1 到 20 之间"
            );
        }

        if (candidateK < topK) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "candidateK 不能小于 topK"
            );
        }
    }
}
