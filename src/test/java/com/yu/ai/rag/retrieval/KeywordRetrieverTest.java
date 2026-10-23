
package com.yu.ai.rag.retrieval;

import com.yu.ai.rag.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KeywordRetrieverTest {

    private final KeywordSearchRepository repository =
            mock(KeywordSearchRepository.class);

    private final KeywordRetriever retriever =
            new KeywordRetriever(repository);

    @Test
    void shouldPreserveChineseAndExactIdentifiers() {

        List<String> keywords = KeywordRetriever.tokenize(
                "订单号 ORD_2026_0001 ERR-1007 "
                        + "v2.1.0 BAAI/bge-m3"
        );

        assertEquals(
                List.of(
                        "订单号",
                        "ORD_2026_0001",
                        "ERR-1007",
                        "v2.1.0",
                        "BAAI/bge-m3"
                ),
                keywords
        );
    }

    @Test
    void shouldRemoveDuplicateKeywords() {

        when(repository.search(
                List.of("ERR-1007"),
                3
        )).thenReturn(List.of());

        assertTrue(
                retriever.search(
                        "ERR-1007 ERR-1007",
                        3
                ).isEmpty()
        );

        verify(repository).search(
                List.of("ERR-1007"),
                3
        );
    }

    @Test
    void shouldRejectQueryWithoutSearchableKeywords() {

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> retriever.search("!!!", 3)
                );

        assertEquals(
                "INVALID_PARAMETER",
                exception.getErrorCode().getCode()
        );
    }

    @Test
    void shouldRejectInvalidTopK() {

        assertThrows(
                BusinessException.class,
                () -> retriever.search("ERR-1007", 0)
        );
    }
}
