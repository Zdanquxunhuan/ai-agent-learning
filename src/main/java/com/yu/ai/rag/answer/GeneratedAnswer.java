package com.yu.ai.rag.answer;

import com.yu.ai.rag.retrieval.RerankedChunk;

import java.util.List;

public record GeneratedAnswer(
        String status,
        String answer,
        String provider,
        String model,
        boolean insufficientEvidence,
        String refusalReason,
        int evidenceCount,
        long rerankLatencyMs,
        long generationLatencyMs,
        Integer promptTokens,
        Integer completionTokens,
        List<RerankedChunk> evidence
) {
    public GeneratedAnswer {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
