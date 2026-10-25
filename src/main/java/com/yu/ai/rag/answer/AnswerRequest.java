package com.yu.ai.rag.answer;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnswerRequest(
        @NotBlank(message = "query must not be blank")
        @Size(max = 1000, message = "query must not exceed 1000 characters")
        String query,
        @Min(value = 1, message = "topK must be at least 1")
        @Max(value = 20, message = "topK must not exceed 20")
        int topK,
        @Min(value = 1, message = "candidateK must be at least 1")
        @Max(value = 20, message = "candidateK must not exceed 20")
        int candidateK
) {
}
