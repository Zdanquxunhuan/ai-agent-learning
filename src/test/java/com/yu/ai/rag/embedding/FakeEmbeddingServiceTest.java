
package com.yu.ai.rag.embedding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FakeEmbeddingServiceTest {

    private final EmbeddingService embeddingService =
            new FakeEmbeddingService();

    @Test
    void shouldReturnDeterministicEmbedding() {
        String text = "如何重置密码";

        EmbeddingResult first = embeddingService.embed(text);
        EmbeddingResult second = embeddingService.embed(text);

        assertEquals("fake-embedding-v1", first.modelId());
        assertEquals(3, first.dimensions());
        assertEquals(3, first.vector().size());
        assertEquals(first, second);
    }
}
