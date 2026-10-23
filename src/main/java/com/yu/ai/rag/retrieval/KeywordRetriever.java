
package com.yu.ai.rag.retrieval;

import com.yu.ai.rag.common.BusinessException;
import com.yu.ai.rag.common.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 关键词检索服务。
 */
@Service
public class KeywordRetriever {

    /**
     * 提取：
     * 1. 连续中文字符
     * 2. 英文、型号、版本号、错误码等标识符
     * 3. 数字及带分隔符的数字标识
     */
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile(
                    "[\\p{IsHan}]+"
                            + "|[A-Za-z][A-Za-z0-9._/-]*"
                            + "|[0-9]+(?:[._/-][A-Za-z0-9]+)*"
            );

    private final KeywordSearchRepository repository;

    public KeywordRetriever(
            KeywordSearchRepository repository) {
        this.repository = repository;
    }

    /**
     * 执行关键词检索。
     */
    public List<KeywordRetrievedChunk> search(
            String query,
            int topK) {

        if (query == null || query.isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "query 不能为空"
            );
        }

        if (query.length() > 1000) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "query 长度不能超过 1000 个字符"
            );
        }

        if (topK < 1 || topK > 20) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "topK 必须在 1 到 20 之间"
            );
        }

        List<String> keywords = tokenize(query);

        if (keywords.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "query 不包含可检索的关键词"
            );
        }

        return repository.search(keywords, topK);
    }

    /**
     * 将查询文本拆成关键词。
     *
     * 使用大小写不敏感的方式去重，
     * 同时保留第一次出现的原始写法。
     */
    static List<String> tokenize(String query) {

        if (query == null || query.isBlank()) {
            return List.of();
        }

        Matcher matcher = TOKEN_PATTERN.matcher(query);

        Map<String, String> keywords =
                new LinkedHashMap<>();

        while (matcher.find()) {
            String keyword = matcher.group();

            keywords.putIfAbsent(
                    keyword.toLowerCase(Locale.ROOT),
                    keyword
            );
        }

        return List.copyOf(keywords.values());
    }
}
