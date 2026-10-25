package com.yu.ai.rag.answer;

import java.util.List;

public record CitationValidationResult(
        List<String> citationIds,
        List<CitationSource> citations,
        int rejectedCitationCount
) {
    public CitationValidationResult {
        citationIds = citationIds == null ? List.of() : List.copyOf(citationIds);
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
