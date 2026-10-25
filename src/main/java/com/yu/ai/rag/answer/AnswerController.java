package com.yu.ai.rag.answer;

import com.yu.ai.rag.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/qa")
public class AnswerController {

    private final AnswerGenerator answerGenerator;

    public AnswerController(AnswerGenerator answerGenerator) {
        this.answerGenerator = answerGenerator;
    }

    @PostMapping("/answer")
    public ApiResponse<GeneratedAnswer> answer(@Valid @RequestBody AnswerRequest request) {
        return ApiResponse.success(answerGenerator.generate(
                request.query(), request.topK(), request.candidateK()));
    }
}
