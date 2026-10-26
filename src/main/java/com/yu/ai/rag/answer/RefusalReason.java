package com.yu.ai.rag.answer;

/** Machine-readable reason explaining why an answer was not produced. */
public enum RefusalReason {
    NO_RETRIEVAL,
    LOW_EVIDENCE,
    MODEL_INSUFFICIENT_EVIDENCE,
    NO_VALID_CITATIONS,
    RETRIEVAL_ERROR,
    MODEL_ERROR
}
