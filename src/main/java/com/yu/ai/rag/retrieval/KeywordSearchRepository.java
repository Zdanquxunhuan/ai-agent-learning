
package com.yu.ai.rag.retrieval;

import java.util.List;

/**
 * 关键词检索的数据访问接口。
 */
public interface KeywordSearchRepository {

    /**
     * 根据关键词查询切片。
     *
     * @param keywords 查询关键词
     * @param topK 返回结果数量上限
     */
    List<KeywordRetrievedChunk> search(
            List<String> keywords,
            int topK
    );
}
