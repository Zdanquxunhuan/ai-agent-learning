package com.yu.ai.rag.vector;

import java.util.UUID;

public record RetrievedChunk(
        UUID chunkId,
        UUID documentId,
        int chunkIndex,
        String content,
        Integer pageNumber,
        String sectionTitle,
        double similarity
) {
}
