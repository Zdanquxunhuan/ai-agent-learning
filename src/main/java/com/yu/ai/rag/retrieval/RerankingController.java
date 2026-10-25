package com.yu.ai.rag.retrieval;

import com.yu.ai.rag.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/retrieval")
public class RerankingController {

    private final RerankingService rerankingService;

    public RerankingController(RerankingService rerankingService) {
        this.rerankingService = rerankingService;
    }

    @PostMapping("/rerank-search")
    public ApiResponse<RerankerResult> search(
            @Valid @RequestBody RerankSearchRequest request) {
        return ApiResponse.success(
                rerankingService.search(
                        request.query(),
                        request.topK(),
                        request.candidateK()
                )
        );
    }
}
