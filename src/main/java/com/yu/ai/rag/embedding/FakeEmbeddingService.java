
package com.yu.ai.rag.embedding;

import java.util.List;

public class FakeEmbeddingService implements EmbeddingService {

    private static final String MODEL_ID = "fake-embedding-v1";
    private static final int DIMENSIONS = 3;

    @Override
    public EmbeddingResult embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "text must not be blank"
            );
        }

        String normalizedText = text.trim();

        int hash = normalizedText.hashCode();

        double first = ((hash & 0x7fffffff) % 1000 + 1) / 1000.0;
        double second = (normalizedText.length() % 1000 + 1) / 1000.0;
        double third = (
                normalizedText.codePointCount(
                        0, normalizedText.length()
                ) % 1000 + 1
        ) / 1000.0;

        List<Double> vector = List.of(first, second, third);

        return new EmbeddingResult(
                MODEL_ID,
                DIMENSIONS,
                vector
        );
    }
}
