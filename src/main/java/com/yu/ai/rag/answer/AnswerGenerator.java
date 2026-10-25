package com.yu.ai.rag.answer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final ObjectMapper objectMapper;
    private final CitationValidator citationValidator;

    public AnswerGenerator(
            RerankingService rerankingService,
            LlmGateway llmGateway,
            LlmProperties properties,
            PromptBuilder promptBuilder,
            ObjectMapper objectMapper,
            CitationValidator citationValidator) {
        this.rerankingService = rerankingService;
        this.llmGateway = llmGateway;
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
        this.citationValidator = citationValidator;
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
                    "INSUFFICIENT_EVIDENCE", NO_EVIDENCE_ANSWER, "none", "not-called",
                    true, "NO_RETRIEVED_EVIDENCE", 0, retrieval.rerankLatencyMs(),
                    0, null, null, List.of(), List.of(), List.of());
        }

        List<CitationCandidate> citationCandidates = new ArrayList<>();
        for (int i = 0; i < evidence.size(); i++) {
            citationCandidates.add(new CitationCandidate("C" + (i + 1), evidence.get(i)));
        }

        PromptBuilder.BuiltPrompt builtPrompt = promptBuilder.build(query, citationCandidates);
        Map<String, RerankedChunk> currentEvidence = new LinkedHashMap<>();
        for (CitationCandidate candidate : builtPrompt.includedEvidence()) {
            currentEvidence.put(candidate.citationId(), candidate.evidence());
        }
        if (currentEvidence.isEmpty()) {
            return new GeneratedAnswer(
                    "INSUFFICIENT_EVIDENCE", NO_EVIDENCE_ANSWER, "none", "not-called",
                    true, "NO_PROMPT_EVIDENCE", 0, retrieval.rerankLatencyMs(),
                    0, null, null, List.of(), List.of(), List.of());
        }

        LlmRequest request = new LlmRequest(
                promptBuilder.systemPrompt(),
                builtPrompt.userPrompt(),
                properties.getModel(),
                properties.getTemperature(),
                properties.getMaxTokens());

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
                    "LLM gateway returned an empty completion");
        }

        LlmAnswerPayload payload = parsePayload(completion.content());
        CitationValidationResult validation = citationValidator.validate(
                payload.citationIds(), currentEvidence);
        if (validation.rejectedCitationCount() > 0) {
            log.warn("RAG answer contained unsupported citations; rejectedCitationCount={}",
                    validation.rejectedCitationCount());
        }

        long generationLatencyMs = completion.latencyMs();
        if (Boolean.TRUE.equals(payload.insufficientEvidence())) {
            return new GeneratedAnswer(
                    "INSUFFICIENT_EVIDENCE",
                    citationValidator.removeUnacceptedInlineMarkers(payload.answer(), Set.of()),
                    completion.provider(),
                    completion.model(),
                    true,
                    "MODEL_INSUFFICIENT_EVIDENCE",
                    evidence.size(),
                    retrieval.rerankLatencyMs(),
                    generationLatencyMs,
                    completion.promptTokens(),
                    completion.completionTokens(),
                    evidence,
                    List.of(),
                    List.of());
        }

        if (validation.citationIds().isEmpty()) {
            log.warn("RAG answer rejected because no valid citations were returned; evidenceCount={}", evidence.size());
            return new GeneratedAnswer(
                    "INSUFFICIENT_EVIDENCE",
                    NO_EVIDENCE_ANSWER,
                    completion.provider(),
                    completion.model(),
                    true,
                    "NO_VALID_CITATIONS",
                    evidence.size(),
                    retrieval.rerankLatencyMs(),
                    generationLatencyMs,
                    completion.promptTokens(),
                    completion.completionTokens(),
                    evidence,
                    List.of(),
                    List.of());
        }

        String answer = citationValidator.removeUnacceptedInlineMarkers(
                payload.answer(), Set.copyOf(validation.citationIds()));
        log.info(
                "RAG answer generated provider={} model={} temperature={} maxTokens={} queryLength={} evidenceCount={} validCitationCount={} promptTokens={} completionTokens={} generationLatencyMs={}",
                completion.provider(), completion.model(), properties.getTemperature(), properties.getMaxTokens(),
                query.length(), evidence.size(), validation.citationIds().size(), completion.promptTokens(),
                completion.completionTokens(), generationLatencyMs);

        return new GeneratedAnswer(
                "ANSWERED",
                answer,
                completion.provider(),
                completion.model(),
                false,
                null,
                evidence.size(),
                retrieval.rerankLatencyMs(),
                generationLatencyMs,
                completion.promptTokens(),
                completion.completionTokens(),
                evidence,
                validation.citationIds(),
                validation.citations());
    }

    private LlmAnswerPayload parsePayload(String content) {
        String normalized = stripMarkdownFence(content);
        try {
            LlmAnswerPayload payload = objectMapper.readValue(normalized, LlmAnswerPayload.class);
            if (payload == null
                    || !StringUtils.hasText(payload.answer())
                    || payload.citationIds() == null
                    || payload.insufficientEvidence() == null) {
                throw new LlmGatewayException(
                        LlmGatewayException.FailureType.INVALID_RESPONSE,
                        "LLM JSON must contain non-empty answer, citationIds array, and insufficientEvidence boolean");
            }
            return payload;
        } catch (JsonProcessingException ex) {
            throw new LlmGatewayException(
                    LlmGatewayException.FailureType.INVALID_RESPONSE,
                    "LLM response was not a valid structured citation JSON object",
                    ex);
        }
    }

    private String stripMarkdownFence(String content) {
        String normalized = content.trim();
        if (!normalized.startsWith("```")) {
            return normalized;
        }
        int firstLineBreak = normalized.indexOf('\n');
        if (firstLineBreak >= 0) {
            normalized = normalized.substring(firstLineBreak + 1).trim();
        }
        if (normalized.endsWith("```")) {
            normalized = normalized.substring(0, normalized.length() - 3).trim();
        }
        return normalized;
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
}
