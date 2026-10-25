package com.yu.ai.rag.answer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** The structured JSON payload expected from the language model. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmAnswerPayload(
        String answer,
        List<String> citationIds,
        Boolean insufficientEvidence
) {
}
