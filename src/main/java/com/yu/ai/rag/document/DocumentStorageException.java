package com.yu.ai.rag.document;

/**
 * 文档存储异常
 */
public class DocumentStorageException extends RuntimeException {

    public DocumentStorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public DocumentStorageException(String message) {
        super(message);
    }
}