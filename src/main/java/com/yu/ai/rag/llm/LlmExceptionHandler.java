package com.yu.ai.rag.llm;

import com.yu.ai.rag.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LlmExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(LlmExceptionHandler.class);

    @ExceptionHandler(LlmGatewayException.class)
    public ResponseEntity<ApiResponse<Void>> handleLlmGatewayException(LlmGatewayException ex) {
        LlmGatewayException.FailureType type = ex.getFailureType();
        HttpStatus status = type == LlmGatewayException.FailureType.TIMEOUT
                ? HttpStatus.GATEWAY_TIMEOUT
                : HttpStatus.BAD_GATEWAY;
        log.warn("LLM request failed; failureType={}, httpStatus={}", type, status.value());
        return ResponseEntity.status(status).body(
                ApiResponse.<Void>failure(type.code(), type.publicMessage()));
    }
}
