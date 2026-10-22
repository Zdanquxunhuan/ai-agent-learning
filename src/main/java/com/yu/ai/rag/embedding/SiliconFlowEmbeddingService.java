
package com.yu.ai.rag.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
public class SiliconFlowEmbeddingService implements EmbeddingService {

    private static final Logger log =
            LoggerFactory.getLogger(SiliconFlowEmbeddingService.class);

    private final RestClient restClient;
    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final int expectedDimensions;
    private final int requestsPerSecond;

    private final Object rateLimitLock = new Object();
    private long nextAllowedNanos = System.nanoTime();

    public SiliconFlowEmbeddingService(
            @Value("${rag.embedding.base-url:https://api.siliconflow.cn/v1}")
            String baseUrl,
            @Value("${rag.embedding.api-key:}")
            String apiKey,
            @Value("${rag.embedding.model:BAAI/bge-m3}")
            String model,
            @Value("${rag.embedding.dimensions:1024}")
            int expectedDimensions,
            @Value("${rag.embedding.connect-timeout:3s}")
            Duration connectTimeout,
            @Value("${rag.embedding.read-timeout:30s}")
            Duration readTimeout,
            @Value("${rag.embedding.requests-per-second:2}")
            int requestsPerSecond) {

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Embedding base URL must not be blank");
        }

        if (expectedDimensions <= 0 || requestsPerSecond <= 0) {
            throw new IllegalArgumentException(
                    "Embedding dimensions and requests-per-second must be positive");
        }

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(
                Math.toIntExact(connectTimeout.toMillis()));
        factory.setReadTimeout(
                Math.toIntExact(readTimeout.toMillis()));

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();

        String normalizedBaseUrl = baseUrl.trim();
        while (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(
                    0, normalizedBaseUrl.length() - 1);
        }

        this.endpoint = normalizedBaseUrl + "/embeddings";
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.expectedDimensions = expectedDimensions;
        this.requestsPerSecond = requestsPerSecond;
    }

    @Override
    public EmbeddingResult embed(String text) {
        if (text == null || text.isBlank()) {
            throw new EmbeddingException(
                    EmbeddingException.Code.INVALID_INPUT,
                    "Embedding text must not be blank");
        }

        if (apiKey.isBlank()) {
            throw new EmbeddingException(
                    EmbeddingException.Code.CONFIGURATION_ERROR,
                    "SILICONFLOW_API_KEY is not configured");
        }

        awaitRateLimit();

        try {
            EmbeddingResponse response = restClient.post()
                    .uri(endpoint)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new EmbeddingRequest(
                            model, text, "float"))
                    .retrieve()
                    .body(EmbeddingResponse.class);

            if (response == null
                    || response.data() == null
                    || response.data().isEmpty()) {
                throw new EmbeddingException(
                        EmbeddingException.Code.INVALID_RESPONSE,
                        "Embedding provider returned no vector");
            }

            List<Double> vector =
                    response.data().get(0).embedding();

            if (vector == null || vector.isEmpty()) {
                throw new EmbeddingException(
                        EmbeddingException.Code.INVALID_RESPONSE,
                        "Embedding provider returned an empty vector");
            }

            if (vector.size() != expectedDimensions) {
                throw new EmbeddingException(
                        EmbeddingException.Code.DIMENSION_MISMATCH,
                        "Expected " + expectedDimensions
                                + " dimensions but received "
                                + vector.size());
            }

            String responseModel =
                    response.model() == null
                            || response.model().isBlank()
                            ? model : response.model();

            Integer promptTokens =
                    response.usage() == null
                            ? null
                            : response.usage().promptTokens();

            log.info(
                    "Embedding succeeded: model={}, dimensions={}, promptTokens={}",
                    responseModel, vector.size(), promptTokens);

            return new EmbeddingResult(
                    responseModel,
                    vector.size(),
                    vector);

        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();

            if (status == 401 || status == 403) {
                throw new EmbeddingException(
                        EmbeddingException.Code.AUTHENTICATION_FAILED,
                        "SiliconFlow authentication failed: HTTP " + status,
                        ex);
            }

            if (status == 429) {
                throw new EmbeddingException(
                        EmbeddingException.Code.RATE_LIMITED,
                        "SiliconFlow rate limit exceeded",
                        ex);
            }

            if (status == 504) {
                throw new EmbeddingException(
                        EmbeddingException.Code.TIMEOUT,
                        "SiliconFlow request timed out",
                        ex);
            }

            throw new EmbeddingException(
                    EmbeddingException.Code.PROVIDER_ERROR,
                    "SiliconFlow returned HTTP " + status,
                    ex);

        } catch (ResourceAccessException ex) {
            EmbeddingException.Code code = isTimeout(ex)
                    ? EmbeddingException.Code.TIMEOUT
                    : EmbeddingException.Code.PROVIDER_ERROR;

            throw new EmbeddingException(
                    code,
                    "Failed to access SiliconFlow embedding service",
                    ex);

        } catch (RestClientException ex) {
            throw new EmbeddingException(
                    EmbeddingException.Code.INVALID_RESPONSE,
                    "Failed to read SiliconFlow response",
                    ex);
        }
    }

    private void awaitRateLimit() {
        long intervalNanos = Math.max(
                1L,
                TimeUnit.SECONDS.toNanos(1) / requestsPerSecond);

        long waitNanos;

        synchronized (rateLimitLock) {
            long now = System.nanoTime();
            long scheduled = Math.max(now, nextAllowedNanos);

            waitNanos = scheduled - now;
            nextAllowedNanos = scheduled + intervalNanos;
        }

        if (waitNanos > 0) {
            try {
                TimeUnit.NANOSECONDS.sleep(waitNanos);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();

                throw new EmbeddingException(
                        EmbeddingException.Code.INTERRUPTED,
                        "Embedding request was interrupted",
                        ex);
            }
        }
    }

    private boolean isTimeout(Throwable exception) {
        for (Throwable cause = exception;
             cause != null;
             cause = cause.getCause()) {

            String name = cause.getClass()
                    .getSimpleName()
                    .toLowerCase(Locale.ROOT);

            if (cause instanceof SocketTimeoutException
                    || cause instanceof HttpTimeoutException
                    || name.contains("timeout")) {
                return true;
            }
        }

        return false;
    }

    private record EmbeddingRequest(
            String model,
            String input,
            @JsonProperty("encoding_format") String encodingFormat) {
    }

    private record EmbeddingResponse(
            String model,
            List<EmbeddingData> data,
            EmbeddingUsage usage) {
    }

    private record EmbeddingData(
            List<Double> embedding,
            Integer index) {
    }

    private record EmbeddingUsage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("total_tokens") Integer totalTokens) {
    }
}
