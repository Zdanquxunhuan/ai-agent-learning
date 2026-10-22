
package com.yu.ai.rag.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PlainTextParserTest {

    private PlainTextParser parser;

    @BeforeEach
    void setUp() {
        parser = new PlainTextParser();
    }

    /**
     * 测试1：正常 UTF-8 文本
     */
    @Test
    void shouldParseNormalUtf8Text() {
        String originalText = "这是一个 RAG 文档解析测试";

        ParsedDocument result = parser.parse(
                originalText.getBytes(StandardCharsets.UTF_8)
        );

        assertEquals(originalText, result.text());
    }

    /**
     * 测试2：空文件返回空文本
     */
    @Test
    void shouldReturnEmptyTextForEmptyFile() {
        ParsedDocument result = parser.parse(new byte[0]);

        assertEquals("", result.text());
    }

    /**
     * 测试3：非法 UTF-8 输入应抛出解析异常
     */
    @Test
    void shouldRejectInvalidUtf8() {
        byte[] invalidUtf8 = {
                (byte) 0xC3,
                (byte) 0x28
        };

        assertThrows(
                DocumentParsingException.class,
                () -> parser.parse(invalidUtf8)
        );
    }

    /**
     * 测试4：支持 TXT 文件及带参数的 MIME 类型
     */
    @Test
    void shouldSupportTxtFile() {
        assertTrue(
                parser.supports(
                        "sample.txt",
                        "text/plain; charset=UTF-8"
                )
        );
    }

    /**
     * 测试5：不支持 PDF 文件
     */
    @Test
    void shouldNotSupportPdfFile() {
        assertFalse(
                parser.supports(
                        "sample.pdf",
                        "application/pdf"
                )
        );
    }

    /**
     * 测试6：不支持的文件名应返回 false
     */
    @Test
    void shouldNotSupportUnknownFileType() {
        assertFalse(
                parser.supports(
                        "sample.exe",
                        "application/octet-stream"
                )
        );
    }
}
