package com.yu.ai.rag.parser;

/**
 * 文档解析结果
 */
public record ParsedDocument(String text) {

    public ParsedDocument {
        if (text == null) {
            throw new IllegalArgumentException("解析文本不能为 null");
        }
    }
}