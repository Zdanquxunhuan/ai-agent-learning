package com.yu.ai.rag.document;

import java.util.UUID;

/**
 * 文档元数据
 */
public record DocumentRecord(
        UUID id,
        String originalFilename,
        String contentHash,
        DocumentStatus status
) {
}