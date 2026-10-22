package com.yu.ai.rag.parser;

/**
 * 文档解析后的文本片段及来源信息
 */
public record ParsedSegment(
        String text,
        Integer pageNumber,
        String sectionTitle
) {
    public ParsedSegment {
        if (text == null) {
            throw new IllegalArgumentException("片段文本不能为 null");
        }
    }
}