package com.yu.ai.rag.parser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * DOCX 文档解析器
 */
@Component
public class DocxDocumentParser implements DocumentParser {

    private static final String DOCX_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Override
    public boolean supports(String filename, String contentType) {
        if (filename == null
                || !filename.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            return false;
        }

        if (contentType == null || contentType.isBlank()) {
            return true;
        }

        String mimeType = contentType
                .split(";", 2)[0]
                .trim()
                .toLowerCase(Locale.ROOT);

        return DOCX_MIME_TYPE.equals(mimeType);
    }

    @Override
    public ParsedDocument parse(byte[] content) {
        if (content == null) {
            throw new DocumentParsingException(
                    "DOCX 文件内容不能为 null"
            );
        }

        try (XWPFDocument document = new XWPFDocument(
                new ByteArrayInputStream(content))) {

            List<ParsedSegment> segments = new ArrayList<>();

            // 当前章节标题
            String currentHeading = null;

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String paragraphText = paragraph.getText();

                if (paragraphText == null || paragraphText.isBlank()) {
                    continue;
                }

                paragraphText = paragraphText.strip();

                // 遇到标题时，更新当前章节
                if (isHeading(document, paragraph.getStyleID())) {
                    currentHeading = paragraphText;
                }

                // 保留段落与所属章节
                segments.add(new ParsedSegment(
                        paragraphText,
                        null,
                        currentHeading
                ));
            }

            String text = segments.stream()
                    .map(ParsedSegment::text)
                    .collect(Collectors.joining("\n"));

            return new ParsedDocument(text, segments);

        } catch (IOException | RuntimeException e) {
            throw new DocumentParsingException(
                    "DOCX 文件解析失败，请检查文件是否损坏或格式是否正确",
                    e
            );
        }
    }

    /**
     * 判断段落是否为标题
     */
    private boolean isHeading(
            XWPFDocument document,
            String styleId) {

        if (styleId == null || styleId.isBlank()) {
            return false;
        }

        String normalizedStyleId = styleId
                .toLowerCase(Locale.ROOT)
                .replace(" ", "");

        if (normalizedStyleId.startsWith("heading")
                || normalizedStyleId.startsWith("标题")) {
            return true;
        }

        // 尝试从样式定义中获取标题名称
        XWPFStyles styles = document.getStyles();

        if (styles != null) {
            XWPFStyle style = styles.getStyle(styleId);

            if (style != null && style.getName() != null) {
                String styleName = style.getName()
                        .toLowerCase(Locale.ROOT);

                return styleName.startsWith("heading")
                        || styleName.startsWith("标题");
            }
        }

        return false;
    }
}