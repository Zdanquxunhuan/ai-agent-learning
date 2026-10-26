package com.yu.ai.rag.answer;

/** Result of applying the evidence policy before spending tokens on an LLM call. */
public record EvidenceAssessment(
        boolean sufficient,
        RefusalReason refusalReason,
        int usableEvidenceCount,
        int strongEvidenceCount
) {
}
