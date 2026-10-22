package com.yu.ai.rag.parser;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文档解析器注册中心
 */
@Component
public class ParserRegistry {

    private final List<DocumentParser> parsers;

    public ParserRegistry(List<DocumentParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    /**
     * 根据文件名和 MIME 类型选择解析器
     */
    public DocumentParser getParser(
            String filename,
            String contentType) {

        return parsers.stream()
                .filter(parser ->
                        parser.supports(filename, contentType))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "没有找到对应的文档解析器"));
    }
}