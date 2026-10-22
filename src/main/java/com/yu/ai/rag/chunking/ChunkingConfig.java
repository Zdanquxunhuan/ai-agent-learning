
package com.yu.ai.rag.chunking;

/**
 * 文档切片配置。
 *
 * @param maxChunkSize 单个切片允许的最大字符长度
 * @param overlapSize 相邻切片之间的重叠字符数
 */
public record ChunkingConfig(
        int maxChunkSize,
        int overlapSize
) {

    public ChunkingConfig {
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException(
                    "maxChunkSize must be greater than 0"
            );
        }

        if (overlapSize < 0) {
            throw new IllegalArgumentException(
                    "overlapSize must not be negative"
            );
        }

        if (overlapSize >= maxChunkSize) {
            throw new IllegalArgumentException(
                    "overlapSize must be less than maxChunkSize"
            );
        }
    }
}
