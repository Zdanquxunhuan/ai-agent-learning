package com.yu.ai.rag.retrieval;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SiliconFlowRerankerTest {

    private static final String ENDPOINT =
            "https://api.siliconflow.cn/v1/rerank";

    @Test
    void shouldMapZeroBasedIndexesAndSortByRelevanceScore() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        List<HybridRetrievedChunk> candidates = candidates();

        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().json("""
                        {
                          "model":"BAAI/bge-reranker-v2-m3",
                          "query":"ERR-1007",
                          "documents":["candidate A","candidate B","candidate C"],
                          "top_n":3,
                          "return_documents":false
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "results":[
                            {"index":2,"relevance_score":0.97},
                            {"index":0,"relevance_score":0.40},
                            {"index":1,"relevance_score":0.20}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        SiliconFlowReranker reranker = new SiliconFlowReranker(
                builder.build(), ENDPOINT, "test-key",
                "BAAI/bge-reranker-v2-m3");

        RerankerResult result = reranker.rerank("ERR-1007", candidates, 2);

        assertEquals("siliconflow", result.provider());
        assertFalse(result.fallbackUsed());
        assertEquals(3, result.candidateCount());
        assertEquals(2, result.results().size());
        assertEquals(candidates.get(2).chunkId(),
                result.results().get(0).chunk().chunkId());
        assertEquals(0.97, result.results().get(0).rerankScore(), 1.0e-9);
        assertEquals(candidates.get(0).chunkId(),
                result.results().get(1).chunk().chunkId());
        server.verify();
    }

    @Test
    void shouldSupportOneBasedProviderIndexes() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        List<HybridRetrievedChunk> candidates = candidates();

        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "results":[
                            {"index":3,"relevance_score":0.97},
                            {"index":1,"relevance_score":0.40},
                            {"index":2,"relevance_score":0.20}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        SiliconFlowReranker reranker = new SiliconFlowReranker(
                builder.build(), ENDPOINT, "test-key",
                "BAAI/bge-reranker-v2-m3");

        RerankerResult result = reranker.rerank("ERR-1007", candidates, 1);

        assertEquals("siliconflow", result.provider());
        assertFalse(result.fallbackUsed());
        assertEquals(candidates.get(2).chunkId(),
                result.results().get(0).chunk().chunkId());
        server.verify();
    }

    @Test
    void shouldFallBackWhenProviderReturnsAnError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        List<HybridRetrievedChunk> candidates = candidates();

        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"temporarily unavailable\"}"));

        SiliconFlowReranker reranker = new SiliconFlowReranker(
                builder.build(), ENDPOINT, "test-key",
                "BAAI/bge-reranker-v2-m3");

        RerankerResult result = reranker.rerank("ERR-1007", candidates, 2);

        assertEquals("identity-fallback", result.provider());
        assertTrue(result.fallbackUsed());
        assertEquals(candidates.get(0).chunkId(),
                result.results().get(0).chunk().chunkId());
        assertEquals(candidates.get(1).chunkId(),
                result.results().get(1).chunk().chunkId());
        server.verify();
    }

    @Test
    void shouldFallBackWhenApiKeyIsMissingWithoutCallingNetwork() {
        List<HybridRetrievedChunk> candidates = candidates();
        SiliconFlowReranker reranker = new SiliconFlowReranker(
                RestClient.builder().build(), ENDPOINT, " ",
                "BAAI/bge-reranker-v2-m3");

        RerankerResult result = reranker.rerank("ERR-1007", candidates, 2);

        assertEquals("identity-fallback", result.provider());
        assertTrue(result.fallbackUsed());
        assertEquals(candidates.get(0).chunkId(),
                result.results().get(0).chunk().chunkId());
    }

    private static List<HybridRetrievedChunk> candidates() {
        return List.of(
                chunk("00000000-0000-0000-0000-000000000001", "candidate A"),
                chunk("00000000-0000-0000-0000-000000000002", "candidate B"),
                chunk("00000000-0000-0000-0000-000000000003", "candidate C")
        );
    }

    private static HybridRetrievedChunk chunk(String id, String content) {
        return new HybridRetrievedChunk(
                UUID.fromString(id),
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                0, content, null, null, 1, null, 0.9, null, 1.0 / 61.0);
    }
}
