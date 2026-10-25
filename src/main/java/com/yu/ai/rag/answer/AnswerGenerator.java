package com.yu.ai.rag.answer;

import com.yu.ai.rag.common.BusinessException;
import com.yu.ai.rag.common.ErrorCode;
import com.yu.ai.rag.llm.LlmCompletion;
import com.yu.ai.rag.llm.LlmGateway;
import com.yu.ai.rag.llm.LlmGatewayException;
import com.yu.ai.rag.llm.LlmProperties;
import com.yu.ai.rag.llm.LlmRequest;
import com.yu.ai.rag.retrieval.RerankerResult;
import com.yu.ai.rag.retrieval.RerankedChunk;
import com.yu.ai.rag.retrieval.RerankingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AnswerGenerator {

    private static final Logger log = LoggerFactory.getLogger(AnswerGenerator.class);
    private static final String NO_EVIDENCE_ANSWER =
            "\u77e5\u8bc6\u5e93\u4e2d\u6ca1\u6709\u68c0\u7d22\u5230\u53ef\u7528\u8bc1\u636e\uff0c"
                    + "\u6682\u65f6\u65e0\u6cd5\u57fa\u4e8e\u77e5\u8bc6\u5e93\u56de\u7b54\u8be5\u95ee\u9898\u3002";

    private final RerankingService rerankingService;
    private final LlmGateway llmGateway;
    private final LlmProperties properties;
    private final PromptBuilder promptBuilder;

    public AnswerGenerator(
            RerankingService rerankingService,
            LlmGateway llmGateway,
            LlmProperties properties,
            PromptBuilder promptBuilder) {
        this.rerankingService = rerankingService;
        this.llmGateway = llmGateway;
        this.properties = properties;
        this.promptBuilder = promptBuilder;
    }

    public GeneratedAnswer generate(String query, int topK, int candidateK) {
        validate(query, topK, candidateK);

        RerankerResult retrieval = rerankingService.search(query, topK, candidateK);
        List<RerankedChunk> evidence = retrieval.results().stream()
                .filter(item -> item.chunk() != null && StringUtils.hasText(item.chunk().content()))
                .toList();

        if (evidence.isEmpty()) {
            log.info("RAG answer skipped because no evidence was retrieved; queryLength={}", query.length());
            return new GeneratedAnswer(
                    "INSUFFICIENT_EVIDENCE",
                    NO_EVIDENCE_ANSWER,
                    "none",
                    "not-called",
                    true,
                    "NO_RETRIEVED_EVIDENCE",
                    0,
                    retrieval.rerankLatencyMs(),
                    0,
                    null,
                    null,
                    List.of()
            );
        }

        String userPrompt = promptBuilder.buildUserPrompt(query, evidence);
        LlmRequest request = new LlmRequest(
                promptBuilder.systemPrompt(),
                userPrompt,
                properties.getModel(),
                properties.getTemperature(),
                properties.getMaxTokens()
        );

        LlmCompletion completion;
        try {
            completion = llmGateway.complete(request);
        } catch (LlmGatewayException ex) {
            log.warn("RAG answer generation failed; failureType={}, queryLength={}, evidenceCount={}",
                    ex.getFailureType(), query.length(), evidence.size());
            throw ex;
        }

        if (completion == null || !StringUtils.hasText(completion.content())) {
            throw new LlmGatewayException(
                    LlmGatewayException.FailureType.EMPTY_RESPONSE,
                    "LLM gateway returned an empty completion"
            );
        }

        String answer = completion.content().trim();
        log.info(
                "RAG answer generated provider={} model={} temperature={} maxTokens={} querySample={} evidenceCount={} promptTokens={} completionTokens={} generationLatencyMs={} answerSample={}",
                completion.provider(),
                completion.model(),
                properties.getTemperature(),
                properties.getMaxTokens(),
                sample(query, 100),
                evidence.size(),
                completion.promptTokens(),
                completion.completionTokens(),
                completion.latencyMs(),
                sample(answer, 160)
        );

        return new GeneratedAnswer(
                "ANSWERED",
                answer,
                completion.provider(),
                completion.model(),
                false,
                null,
                evidence.size(),
                retrieval.rerankLatencyMs(),
                completion.latencyMs(),
                completion.promptTokens(),
                completion.completionTokens(),
                evidence
        );
    }

    private void validate(String query, int topK, int candidateK) {
        if (!StringUtils.hasText(query)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "query must not be blank");
        }
        if (query.length() > 1000) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "query must not exceed 1000 characters");
        }
        if (topK < 1 || topK > 20) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "topK must be between 1 and 20");
        }
        if (candidateK < topK || candidateK > 20) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "candidateK must be between topK and 20");
        }
    }

    private String sample(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ");
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength) + "...";
    }
}
