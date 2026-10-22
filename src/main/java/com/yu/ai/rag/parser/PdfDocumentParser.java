package com.yu.ai.rag.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * PDF 文档解析器
 */
@Component
public class PdfDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String filename, String contentType) {
        if (filename == null
                || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return false;
        }

        if (contentType == null || contentType.isBlank()) {
            return true;
        }

        String mimeType = contentType
                .split(";", 2)[0]
                .trim()
                .toLowerCase(Locale.ROOT);

        return "application/pdf".equals(mimeType);
    }

    @Override
    public ParsedDocument parse(byte[] content) {
        if (content == null) {
            throw new DocumentParsingException(
                    "PDF 文件内容不能为 null"
            );
        }

        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();

            List<ParsedSegment> segments = new ArrayList<>();

            // 逐页提取文本，保留页码
            for (int page = 1;
                 page <= document.getNumberOfPages();
                 page++) {

                stripper.setStartPage(page);
                stripper.setEndPage(page);

                String pageText = stripper.getText(document);

                if (pageText == null) {
                    pageText = "";
                }

                pageText = pageText.strip();

                segments.add(new ParsedSegment(
                        pageText,
                        page,
                        null
                ));
            }

            String text = segments.stream()
                    .map(ParsedSegment::text)
                    .collect(Collectors.joining("\n"));

            return new ParsedDocument(text, segments);

        } catch (IOException e) {
            throw new DocumentParsingException(
                    "PDF 文件解析失败，请检查文件是否损坏或加密",
                    e
            );
        }
    }
}