
package com.yu.ai.rag.retrieval;

import java.util.UUID;

/**
 * 关键词检索返回的文档切片。
 *
 * matchedTerms 表示该切片命中了多少个不同的查询关键词，
 * 不代表概率或经过校准的相关性分数。
 */
public record KeywordRetrievedChunk(
        UUID chunkId,
        UUID documentId,
        int chunkIndex,
        String content,
        Integer pageNumber,
        String sectionTitle,
        int matchedTerms
) {
}
