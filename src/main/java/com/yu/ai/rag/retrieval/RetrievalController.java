
package com.yu.ai.rag.retrieval;

import com.yu.ai.rag.common.ApiResponse;
import com.yu.ai.rag.vector.RetrievedChunk;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/retrieval")
public class RetrievalController {

    private final SemanticRetriever semanticRetriever;

    public RetrievalController(SemanticRetriever semanticRetriever) {
        this.semanticRetriever = semanticRetriever;
    }

    @PostMapping("/search")
    public ApiResponse<List<RetrievedChunk>> search(
            @Valid @RequestBody RetrievalSearchRequest request) {

        List<RetrievedChunk> results = semanticRetriever.search(
                request.query(),
                request.topK()
        );

        return ApiResponse.success(results);
    }
}
