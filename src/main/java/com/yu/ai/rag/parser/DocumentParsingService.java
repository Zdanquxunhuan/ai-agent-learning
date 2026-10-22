
package com.yu.ai.rag.parser;

import org.springframework.stereotype.Service;

/**
 * 文档解析应用服务
 *
 * 负责选择合适的解析器，并执行文档解析。
 */
@Service
public class DocumentParsingService {

    private final ParserRegistry parserRegistry;

    public DocumentParsingService(ParserRegistry parserRegistry) {
        this.parserRegistry = parserRegistry;
    }

    /**
     * 根据文件名和 MIME 类型选择解析器并解析文档
     *
     * @param filename    原始文件名
     * @param contentType 文件 MIME 类型
     * @param content     文件字节内容
     * @return 解析后的文档
     */
    public ParsedDocument parse(
            String filename,
            String contentType,
            byte[] content) {

        DocumentParser parser = parserRegistry.getParser(
                filename,
                contentType
        );

        return parser.parse(content);
    }
}
