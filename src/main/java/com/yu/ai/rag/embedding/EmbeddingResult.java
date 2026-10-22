
package com.yu.ai.rag.embedding;

import java.util.List;

public record EmbeddingResult(
//        模型标识	fake-embedding-v1
        String modelId,
//        向量维度
        int dimensions,
        List<Double> vector
) {
    public EmbeddingResult {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId must not be blank");
        }

        if (vector == null || vector.isEmpty()) {
            throw new IllegalArgumentException("vector must not be null or empty");
        }

        vector = List.copyOf(vector);

        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }

        if (dimensions != vector.size()) {
            throw new IllegalArgumentException(
                    "dimensions must match vector size"
            );
        }
    }
}
