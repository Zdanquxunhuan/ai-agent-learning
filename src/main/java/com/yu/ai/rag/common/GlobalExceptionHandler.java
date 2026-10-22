package com.yu.ai.rag.common;


import com.yu.ai.rag.document.DocumentStorageException;
import com.yu.ai.rag.document.DocumentValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Void>failure(
                        ex.getErrorCode().getCode(),
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex) {

        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.failure(
                        "FILE_TOO_LARGE",
                        "上传文件超过大小限制"
                ));
    }

    @ExceptionHandler(DocumentStorageException.class)
    public ResponseEntity<ApiResponse<Void>> handleDocumentStorage(
            DocumentStorageException ex) {

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(
                        "DOCUMENT_STORAGE_ERROR",
                        "文档保存失败，请稍后重试"
                ));
    }

    @ExceptionHandler(DocumentValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDocumentValidation(
            DocumentValidationException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(
                        "INVALID_UPLOAD",
                        ex.getMessage()
                ));
    }

    /**
     * 处理请求对象的参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .filter(Objects::nonNull)
                .orElse("请求参数不合法");

        return ResponseEntity.badRequest()
                .body(ApiResponse.<Void>failure(
                        ErrorCode.INVALID_PARAMETER.getCode(),
                        message
                ));
    }

    /**
     * 处理方法参数约束异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException ex) {

        String message = ex.getConstraintViolations()
                .stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse("请求参数不合法");

        return ResponseEntity.badRequest()
                .body(ApiResponse.<Void>failure(
                        ErrorCode.INVALID_PARAMETER.getCode(),
                        message
                ));
    }

    /**
     * 处理未预期的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception ex) {

        log.error("服务器发生未预期的异常", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.<Void>failure(
                        ErrorCode.INTERNAL_ERROR.getCode(),
                        ErrorCode.INTERNAL_ERROR.getDefaultMessage()
                ));
    }
}
