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
        List<RerankedChunk> evidence,
        List<String> citationIds,
        List<CitationSource> citations
) {
    public GeneratedAnswer {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        citationIds = citationIds == null ? List.of() : List.copyOf(citationIds);
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
