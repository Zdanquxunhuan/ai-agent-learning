package com.yu.ai.rag.document;

/**
 * 文档上传校验异常
 */
public class DocumentValidationException extends RuntimeException {

    public DocumentValidationException(String message) {
        super(message);
    }
}