
package com.yu.ai.rag.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParserSamplePreparationTest {

    @Test
    void shouldGenerateFiveSamplesAndVerifyExtraction()
            throws Exception {

        // 样本保存到 target/parser-samples
        Path directory = Path.of("target", "parser-samples");
        Files.createDirectories(directory);

        // 1. TXT 样本：中文文本
        Path txt1 = directory.resolve("sample-01.txt");
        String expectedTxt1 = "企业知识库用于检索内部文档。";

        Files.writeString(
                txt1,
                expectedTxt1,
                StandardCharsets.UTF_8
        );

        // 2. TXT 样本：英文及技术术语
        Path txt2 = directory.resolve("sample-02.txt");
        String expectedTxt2 =
                "RAG uses SHA-256 to identify document content.";

        Files.writeString(
                txt2,
                expectedTxt2,
                StandardCharsets.UTF_8
        );

        // 3. PDF 样本：两页文本
        Path pdf = directory.resolve("sample-03.pdf");

        createPdf(
                pdf,
                "RAG PDF sample page one.",
                "Second page preserves page number."
        );

        // 4. DOCX 样本：标题和正文
        Path docx = directory.resolve("sample-04.docx");

        createDocx(docx);

        // 5. 空白 PDF 样本
        Path emptyPdf = directory.resolve("sample-05-empty.pdf");

        createBlankPdf(emptyPdf);

        // 验证 TXT 解析
        PlainTextParser txtParser = new PlainTextParser();

        assertEquals(
                expectedTxt1,
                txtParser.parse(Files.readAllBytes(txt1)).text()
        );

        assertEquals(
                expectedTxt2,
                txtParser.parse(Files.readAllBytes(txt2)).text()
        );

        // 验证 PDF 解析及页码
        PdfDocumentParser pdfParser = new PdfDocumentParser();

        ParsedDocument pdfResult =
                pdfParser.parse(Files.readAllBytes(pdf));

        assertEquals(
                "RAG PDF sample page one.\n"
                        + "Second page preserves page number.",
                pdfResult.text()
        );

        assertEquals(2, pdfResult.segments().size());
        assertEquals(
                1,
                pdfResult.segments().get(0).pageNumber()
        );
        assertEquals(
                2,
                pdfResult.segments().get(1).pageNumber()
        );

        // 验证 DOCX 解析及标题
        DocxDocumentParser docxParser =
                new DocxDocumentParser();

        ParsedDocument docxResult =
                docxParser.parse(Files.readAllBytes(docx));

        assertEquals(
                "Architecture\n"
                        + "The parser extracts paragraphs and heading metadata.",
                docxResult.text()
        );

        assertEquals(2, docxResult.segments().size());
        assertEquals(
                "Architecture",
                docxResult.segments().get(0).sectionTitle()
        );
        assertEquals(
                "Architecture",
                docxResult.segments().get(1).sectionTitle()
        );

        // 验证空白 PDF
        ParsedDocument emptyResult =
                pdfParser.parse(Files.readAllBytes(emptyPdf));

        assertTrue(emptyResult.text().isBlank());
        assertEquals(1, emptyResult.segments().size());
        assertEquals(
                1,
                emptyResult.segments().get(0).pageNumber()
        );

        // 确认 5 个文件都已生成
        try (var files = Files.list(directory)) {
            assertEquals(5, files.count());
        }
    }

    private void createPdf(Path path, String... pageTexts)
            throws Exception {

        try (PDDocument document = new PDDocument()) {

            for (String text : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);

                try (PDPageContentStream stream =
                             new PDPageContentStream(document, page)) {

                    stream.beginText();
                    stream.setFont(
                            new PDType1Font(
                                    Standard14Fonts.FontName.HELVETICA
                            ),
                            12
                    );
                    stream.newLineAtOffset(50, 700);
                    stream.showText(text);
                    stream.endText();
                }
            }

            document.save(path.toFile());
        }
    }

    private void createDocx(Path path) throws Exception {

        try (XWPFDocument document = new XWPFDocument()) {

            XWPFParagraph heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("Architecture");

            XWPFParagraph body = document.createParagraph();
            body.createRun().setText(
                    "The parser extracts paragraphs and heading metadata."
            );

            try (OutputStream output = Files.newOutputStream(path)) {
                document.write(output);
            }
        }
    }

    private void createBlankPdf(Path path) throws Exception {

        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(path.toFile());
        }
    }
}
