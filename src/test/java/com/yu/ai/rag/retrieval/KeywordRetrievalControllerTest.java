
package com.yu.ai.rag.retrieval;

import com.yu.ai.rag.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(KeywordRetrievalController.class)
@Import(GlobalExceptionHandler.class)
class KeywordRetrievalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KeywordRetriever keywordRetriever;

    @Test
    void shouldReturnKeywordSearchResults() throws Exception {

        KeywordRetrievedChunk result =
                new KeywordRetrievedChunk(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        2,
                        "错误码 ERR-1007 表示数据库连接失败",
                        null,
                        "故障排查",
                        1
                );

        when(keywordRetriever.search("ERR-1007", 3))
                .thenReturn(List.of(result));

        mockMvc.perform(
                        post("/api/v1/retrieval/keyword-search")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "query": "ERR-1007",
                                          "topK": 3
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].chunkIndex").value(2))
                .andExpect(jsonPath("$.data[0].matchedTerms").value(1));
    }

    @Test
    void shouldRejectBlankQuery() throws Exception {

        mockMvc.perform(
                        post("/api/v1/retrieval/keyword-search")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "query": " ",
                                          "topK": 3
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("INVALID_PARAMETER")
                );
    }
}
