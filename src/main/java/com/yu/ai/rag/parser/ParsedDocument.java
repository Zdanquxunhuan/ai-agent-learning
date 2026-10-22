package com.yu.ai.rag.parser;

import java.util.List;

/**
 * 文档解析结果
 */
public record ParsedDocument(
        String text,
        List<ParsedSegment> segments
) {
    public ParsedDocument {
        if (text == null) {
            throw new IllegalArgumentException("解析文本不能为 null");
        }

        segments = segments == null
                ? List.of()
                : List.copyOf(segments);
    }

    /**
     * 兼容原有 TXT 解析器的调用方式
     */
    public ParsedDocument(String text) {
        this(
                text,
                text == null || text.isEmpty()
                        ? List.of()
                        : List.of(new ParsedSegment(text, null, null))
        );
    }
}