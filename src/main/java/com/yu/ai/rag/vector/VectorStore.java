
package com.yu.ai.rag.vector;

import com.yu.ai.rag.chunking.TextChunk;
import com.yu.ai.rag.embedding.EmbeddingResult;

import java.util.List;
import java.util.UUID;

public interface VectorStore {

    /**
     * 保存或更新切片及其向量。
     * 相同 documentId 和 chunkIndex 应执行更新，而不是重复插入。
     */
    void upsert(TextChunk chunk, EmbeddingResult embedding);

    /**
     * 删除指定文档的全部向量。
     */
    void deleteByDocumentId(UUID documentId);

    /**
     * 根据查询向量搜索最相似的切片。
     *
     * @param queryVector 查询文本对应的向量
     * @param topK 返回结果数量上限
     */
    List<RetrievedChunk> similaritySearch(
            List<Double> queryVector,
            int topK
    );
}
