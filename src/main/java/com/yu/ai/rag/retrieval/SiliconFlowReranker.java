package com.yu.ai.rag.retrieval;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Optional SiliconFlow adapter using POST /v1/rerank.
 * Any provider/configuration/response failure degrades to the original hybrid order.
 */
@Component
@ConditionalOnProperty(
        prefix = "rag.rerank",
        name = "provider",
        havingValue = "siliconflow"
)
public class SiliconFlowReranker implements Reranker {

    private static final Logger log =
            LoggerFactory.getLogger(SiliconFlowReranker.class);

    private final RestClient restClient;
    private final String endpoint;
    private final String apiKey;
    private final String model;

    public SiliconFlowReranker(
            @Value("${rag.rerank.base-url:https://api.siliconflow.cn/v1}")
            String baseUrl,
            @Value("${rag.rerank.api-key:}")
            String apiKey,
            @Value("${rag.rerank.model:BAAI/bge-reranker-v2-m3}")
            String model,
            @Value("${rag.rerank.connect-timeout:3s}")
            Duration connectTimeout,
            @Value("${rag.rerank.read-timeout:10s}")
            Duration readTimeout) {
        this(
                createRestClient(connectTimeout, readTimeout),
                normalizeBaseUrl(baseUrl) + "/rerank",
                apiKey,
                model
        );
    }

    // Package-private constructor supports deterministic HTTP adapter tests.
    SiliconFlowReranker(
            RestClient restClient,
            String endpoint,
            String apiKey,
            String model) {
        this.restClient = restClient;
        this.endpoint = endpoint;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
    }

    @Override
    public RerankerResult rerank(
            String query,
            List<HybridRetrievedChunk> candidates,
            int topK) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be at least 1");
        }

        long started = System.nanoTime();
        if (candidates == null || candidates.isEmpty()) {
            return new RerankerResult(
                    "siliconflow", false, 0,
                    IdentityReranker.elapsedMillis(started), List.of());
        }

        if (apiKey.isBlank()) {
            log.warn("Reranker API key is missing; using hybrid ranking");
            return fallback(candidates, topK, started);
        }

        try {
            List<String> documents = candidates.stream()
                    .map(candidate -> candidate.content() == null
                            ? "" : candidate.content())
                    .toList();

            ApiResponse response = restClient.post()
                    .uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiRequest(
                            model,
                            query,
                            documents,
                            candidates.size(),
                            false
                    ))
                    .retrieve()
                    .body(ApiResponse.class);

            if (response == null
                    || response.results() == null
                    || response.results().size() != candidates.size()) {
                throw new IllegalStateException(
                        "Reranker returned an incomplete result list");
            }

            int indexOffset = detectIndexOffset(
                    response.results(), candidates.size());
            Set<Integer> seenIndexes = new HashSet<>();
            List<IndexedCandidate> ordered = new ArrayList<>();

            for (ApiResult item : response.results()) {
                if (item.index() == null
                        || item.relevanceScore() == null
                        || !Double.isFinite(item.relevanceScore())) {
                    throw new IllegalStateException(
                            "Reranker returned an invalid result item");
                }

                int candidateIndex = item.index() - indexOffset;
                if (candidateIndex < 0
                        || candidateIndex >= candidates.size()
                        || !seenIndexes.add(candidateIndex)) {
                    throw new IllegalStateException(
                            "Reranker returned an invalid or duplicate index");
                }

                ordered.add(new IndexedCandidate(
                        candidateIndex,
                        candidates.get(candidateIndex),
                        item.relevanceScore()
                ));
            }

            if (seenIndexes.size() != candidates.size()) {
                throw new IllegalStateException(
                        "Reranker did not return every candidate");
            }

            ordered.sort(
                    Comparator.comparingDouble(IndexedCandidate::score)
                            .reversed()
                            .thenComparingInt(IndexedCandidate::originalIndex)
            );

            List<RerankedChunk> results = new ArrayList<>();
            int resultCount = Math.min(topK, ordered.size());
            for (int i = 0; i < resultCount; i++) {
                IndexedCandidate item = ordered.get(i);
                results.add(new RerankedChunk(
                        item.chunk(), i + 1, item.score()));
            }

            return new RerankerResult(
                    "siliconflow",
                    false,
                    candidates.size(),
                    IdentityReranker.elapsedMillis(started),
                    results
            );
        } catch (RestClientException | IllegalStateException
                 | IllegalArgumentException ex) {
            // Do not log response bodies, query text, documents, or credentials.
            log.warn(
                    "SiliconFlow reranker failed; using hybrid ranking. reason={}",
                    ex.getClass().getSimpleName());
            return fallback(candidates, topK, started);
        }
    }

    private RerankerResult fallback(
            List<HybridRetrievedChunk> candidates,
            int topK,
            long started) {
        List<RerankedChunk> results = new ArrayList<>();
        int resultCount = Math.min(topK, candidates.size());

        for (int i = 0; i < resultCount; i++) {
            results.add(new RerankedChunk(candidates.get(i), i + 1, null));
        }

        return new RerankerResult(
                "identity-fallback",
                true,
                candidates.size(),
                IdentityReranker.elapsedMillis(started),
                results
        );
    }

    /**
     * The provider response index base is not assumed. Requesting all candidates
     * lets us distinguish 0-based indices (contains 0) from 1-based indices
     * (contains candidateCount). Ambiguous responses trigger safe fallback.
     */
    private int detectIndexOffset(List<ApiResult> results, int candidateCount) {
        boolean hasZero = results.stream()
                .anyMatch(item -> item.index() != null && item.index() == 0);
        boolean hasCandidateCount = results.stream()
                .anyMatch(item -> item.index() != null
                        && item.index() == candidateCount);

        if (hasZero && !hasCandidateCount) {
            return 0;
        }
        if (hasCandidateCount && !hasZero) {
            return 1;
        }
        throw new IllegalStateException(
                "Reranker returned an ambiguous index base");
    }

    private static RestClient createRestClient(
            Duration connectTimeout,
            Duration readTimeout) {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.toIntExact(connectTimeout.toMillis()));
        factory.setReadTimeout(Math.toIntExact(readTimeout.toMillis()));
        return RestClient.builder().requestFactory(factory).build();
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Reranker base URL must not be blank");
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private record ApiRequest(
            String model,
            String query,
            List<String> documents,
            @JsonProperty("top_n") int topN,
            @JsonProperty("return_documents") boolean returnDocuments) {
    }

    private record ApiResponse(List<ApiResult> results) {
    }

    private record ApiResult(
            Integer index,
            @JsonProperty("relevance_score") Double relevanceScore) {
    }

    private record IndexedCandidate(
            int originalIndex,
            HybridRetrievedChunk chunk,
            double score) {
    }
}
