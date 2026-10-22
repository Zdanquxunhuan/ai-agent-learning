
package com.yu.ai.rag.embedding;

public interface EmbeddingService {

    /**
     * 将文本转换为向量。
     *
     * @param text 待转换的文本
     * @return 文本对应的向量结果
     */
    EmbeddingResult embed(String text);
}
