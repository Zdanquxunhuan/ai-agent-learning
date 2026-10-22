package com.yu.ai.rag.parser;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * TXT 文档解析器
 */
@Component
public class PlainTextParser implements DocumentParser {

    /**
     * 判断是否支持当前文件
     */
    @Override
    public boolean supports(String filename, String contentType) {

        if (filename == null || filename.isBlank()) {
            return false;
        }

        // 只处理 TXT 文件
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            return false;
        }

        // MIME 类型未提供时，以扩展名作为判断依据
        if (contentType == null || contentType.isBlank()) {
            return true;
        }

        // 去除 MIME 参数，例如 charset=UTF-8
        String mimeType = contentType
                .split(";", 2)[0]
                .trim()
                .toLowerCase(Locale.ROOT);

        return "text/plain".equals(mimeType);
    }

    /**
     * 将文件字节解析为 UTF-8 文本
     */
    @Override
    public ParsedDocument parse(byte[] content) {

        if (content == null) {
            throw new DocumentParsingException(
                    "TXT 文件内容不能为 null"
            );
        }

        try {
            String text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();

            // 空文件返回空文本，由后续索引流程决定如何处理
            return new ParsedDocument(text);

        } catch (CharacterCodingException e) {
            throw new DocumentParsingException(
                    "TXT 文件不是有效的 UTF-8 编码",
                    e
            );
        }
    }
}