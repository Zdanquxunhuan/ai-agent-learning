package com.yu.ai.rag.document;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 文档索引状态查询结果。
 */
public record DocumentIndexingStatusResponse(
        UUID documentId,
        DocumentStatus documentStatus,
        IndexingTaskStatus taskStatus,
        int attempt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        String failureReason
) {
}