package com.yu.ai.rag.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "rag.llm", name = "provider", havingValue = "deepseek")
public class DeepSeekLlmGateway implements LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekLlmGateway.class);

    private final LlmProperties properties;
    private final RestTemplate restTemplate;

    public DeepSeekLlmGateway(LlmProperties properties, RestTemplate deepSeekRestTemplate) {
        this.properties = properties;
        this.restTemplate = deepSeekRestTemplate;
    }

    @Override
    public LlmCompletion complete(LlmRequest request) {
        long start = System.nanoTime();
        String endpoint = properties.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());

        DeepSeekRequest body = new DeepSeekRequest(
                request.model(),
                List.of(
                        new ChatMessage("system", request.systemPrompt()),
                        new ChatMessage("user", request.userPrompt())
                ),
                request.temperature(),
                request.maxTokens(),
                false,
                Map.of("type", "disabled")
        );

        ResponseEntity<DeepSeekResponse> response;
        try {
            response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    DeepSeekResponse.class
            );
        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                throw new LlmGatewayException(
                        LlmGatewayException.FailureType.TIMEOUT,
                        "DeepSeek request timed out",
                        ex
                );
            }
            throw new LlmGatewayException(
                    LlmGatewayException.FailureType.PROVIDER_ERROR,
                    "DeepSeek network request failed",
                    ex
            );
        } catch (HttpStatusCodeException ex) {
            log.warn("DeepSeek returned HTTP status {}", ex.getStatusCode().value());
            throw new LlmGatewayException(
                    LlmGatewayException.FailureType.PROVIDER_ERROR,
                    "DeepSeek returned HTTP " + ex.getStatusCode().value(),
                    ex
            );
        } catch (RestClientException ex) {
            throw new LlmGatewayException(
                    LlmGatewayException.FailureType.PROVIDER_ERROR,
                    "DeepSeek request failed",
                    ex
            );
        }

        DeepSeekResponse responseBody = response.getBody();
        if (responseBody == null || responseBody.choices() == null || responseBody.choices().isEmpty()) {
            throw new LlmGatewayException(
                    LlmGatewayException.FailureType.INVALID_RESPONSE,
                    "DeepSeek response did not contain choices"
            );
        }

        String content = responseBody.choices().get(0).message() == null
                ? null
                : responseBody.choices().get(0).message().content();
        if (!StringUtils.hasText(content)) {
            throw new LlmGatewayException(
                    LlmGatewayException.FailureType.EMPTY_RESPONSE,
                    "DeepSeek returned empty content"
            );
        }

        Usage usage = responseBody.usage();
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        String actualModel = StringUtils.hasText(responseBody.model())
                ? responseBody.model()
                : request.model();

        return new LlmCompletion(
                "deepseek",
                actualModel,
                content.trim(),
                usage == null ? null : usage.promptTokens(),
                usage == null ? null : usage.completionTokens(),
                latencyMs
        );
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("timed out") || normalized.contains("timeout")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private record ChatMessage(String role, String content) { }

    private record DeepSeekRequest(
            String model,
            List<ChatMessage> messages,
            double temperature,
            @JsonProperty("max_tokens") int maxTokens,
            boolean stream,
            Map<String, String> thinking) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static record DeepSeekResponse(
            String model,
            List<Choice> choices,
            Usage usage) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static record Choice(Message message) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static record Message(String content) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens) { }
}
