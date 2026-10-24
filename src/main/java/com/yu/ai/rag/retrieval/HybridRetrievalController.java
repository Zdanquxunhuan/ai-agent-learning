package com.yu.ai.rag.retrieval;

import com.yu.ai.rag.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/retrieval")
public class HybridRetrievalController {

    private final HybridRetriever hybridRetriever;

    public HybridRetrievalController(HybridRetriever hybridRetriever) {
        this.hybridRetriever = hybridRetriever;
    }

    /**
     * Hybrid retrieval endpoint.
     */
    @PostMapping("/hybrid-search")
    public ApiResponse<List<HybridRetrievedChunk>> search(
            @Valid @RequestBody RetrievalSearchRequest request) {
        return ApiResponse.success(
                hybridRetriever.search(request.query(), request.topK())
        );
    }
}
