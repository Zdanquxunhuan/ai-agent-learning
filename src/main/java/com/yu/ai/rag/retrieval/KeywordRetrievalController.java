
package com.yu.ai.rag.retrieval;

import com.yu.ai.rag.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/retrieval")
public class KeywordRetrievalController {

    private final KeywordRetriever keywordRetriever;

    public KeywordRetrievalController(
            KeywordRetriever keywordRetriever) {
        this.keywordRetriever = keywordRetriever;
    }

    /**
     * 关键词检索接口。
     */
    @PostMapping("/keyword-search")
    public ApiResponse<List<KeywordRetrievedChunk>> search(
            @Valid @RequestBody RetrievalSearchRequest request) {

        List<KeywordRetrievedChunk> results =
                keywordRetriever.search(
                        request.query(),
                        request.topK()
                );

        return ApiResponse.success(results);
    }
}
