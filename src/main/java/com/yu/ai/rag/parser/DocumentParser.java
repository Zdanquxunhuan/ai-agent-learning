package com.yu.ai.rag.parser;

/**
 * 文档解析器接口
 */
public interface DocumentParser {

    /**
     * 判断当前解析器是否支持该文件
     */
    boolean supports(String filename, String contentType);

    /**
     * 解析文件字节内容
     */
    ParsedDocument parse(byte[] content);
}