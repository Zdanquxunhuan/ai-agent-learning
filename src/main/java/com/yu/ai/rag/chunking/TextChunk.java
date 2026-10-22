
package com.yu.ai.rag.chunking;

/**
 * 文档切片结果。
 *
 * @param documentId  原始文档 ID
 * @param chunkIndex  切片序号，从 0 开始
 * @param text        切片文本
 * @param pageNumber  页码，未知时为 null
 * @param sectionTitle 所属章节，未知时为 null
 */
public record TextChunk(
        String documentId,
        int chunkIndex,
        String text,
        Integer pageNumber,
        String sectionTitle
) {

    public TextChunk {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }

        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must be >= 0");
        }

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }

        if (pageNumber != null && pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be >= 1");
        }

        if (sectionTitle != null && sectionTitle.isBlank()) {
            sectionTitle = null;
        }
    }
}
