package com.yu.ai.rag.answer;

import java.util.UUID;

/** A validated citation resolved to a real document and chunk from this request. */
public record CitationSource(
        String citationId,
        UUID documentId,
        String documentName,
        UUID chunkId,
        int chunkIndex,
        Integer pageNumber,
        String sectionTitle
) {
}
