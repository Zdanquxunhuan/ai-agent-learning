package com.yu.ai.rag.document;

import java.util.UUID;

/**
 * 文档上传响应
 */
public record DocumentUploadResponse(
        UUID documentId,
        String originalFilename,
        long sizeBytes,
        String contentType,
        String contentHash,
        DocumentStatus status
) {
}