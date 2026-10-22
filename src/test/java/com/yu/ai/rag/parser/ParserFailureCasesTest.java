
package com.yu.ai.rag.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ParserFailureCasesTest {

    private final PdfDocumentParser pdfParser =
            new PdfDocumentParser();

    private final DocxDocumentParser docxParser =
            new DocxDocumentParser();

    /**
     * 测试1：损坏的 PDF 应返回明确的解析异常
     */
    @Test
    void shouldRejectDamagedPdf() {
        byte[] content = "this is not a pdf"
                .getBytes(StandardCharsets.UTF_8);

        assertThrows(
                DocumentParsingException.class,
                () -> pdfParser.parse(content)
        );
    }

    /**
     * 测试2：加密 PDF 未提供密码时应解析失败
     */
    @Test
    void shouldRejectEncryptedPdf() throws Exception {
        byte[] content = createEncryptedPdf();

        assertThrows(
                DocumentParsingException.class,
                () -> pdfParser.parse(content)
        );
    }

    /**
     * 测试3：空白 PDF 应正常解析为空文本
     */
    @Test
    void shouldHandleBlankPdf() throws Exception {
        byte[] content = createBlankPdf();

        ParsedDocument result = pdfParser.parse(content);

        assertTrue(result.text().isBlank());
        assertEquals(1, result.segments().size());
        assertEquals(
                1,
                result.segments().get(0).pageNumber()
        );
    }

    /**
     * 测试4：损坏的 DOCX 应返回明确的解析异常
     */
    @Test
    void shouldRejectDamagedDocx() {
        byte[] content = "this is not a docx"
                .getBytes(StandardCharsets.UTF_8);

        assertThrows(
                DocumentParsingException.class,
                () -> docxParser.parse(content)
        );
    }

    /**
     * 测试5：加密 DOCX 当前不支持直接解析
     */
    @Test
    void shouldRejectEncryptedDocx() throws Exception {
        byte[] content = createEncryptedDocx();

        assertThrows(
                DocumentParsingException.class,
                () -> docxParser.parse(content)
        );
    }

    /**
     * 测试6：空白 DOCX 应正常解析为空文本
     */
    @Test
    void shouldHandleBlankDocx() throws Exception {
        byte[] content = createBlankDocx();

        ParsedDocument result = docxParser.parse(content);

        assertEquals("", result.text());
        assertTrue(result.segments().isEmpty());
    }

    private byte[] createBlankPdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output =
                     new ByteArrayOutputStream()) {

            document.addPage(new PDPage());
            document.save(output);

            return output.toByteArray();
        }
    }

    private byte[] createEncryptedPdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output =
                     new ByteArrayOutputStream()) {

            document.addPage(new PDPage());

            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy(
                            "owner-password",
                            "user-password",
                            new AccessPermission()
                    );

            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(output);

            return output.toByteArray();
        }
    }

    private byte[] createBlankDocx() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output =
                     new ByteArrayOutputStream()) {

            document.write(output);

            return output.toByteArray();
        }
    }

    private byte[] createEncryptedDocx() throws Exception {
        byte[] plainDocx;

        // 先生成正常 DOCX
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output =
                     new ByteArrayOutputStream()) {

            document.createParagraph()
                    .createRun()
                    .setText("Encrypted document");

            document.write(output);
            plainDocx = output.toByteArray();
        }

        // 再将 DOCX 内容加密
        try (POIFSFileSystem fileSystem =
                     new POIFSFileSystem();
             ByteArrayOutputStream encryptedOutput =
                     new ByteArrayOutputStream()) {

            EncryptionInfo encryptionInfo =
                    new EncryptionInfo(EncryptionMode.agile);

            Encryptor encryptor =
                    encryptionInfo.getEncryptor();

            encryptor.confirmPassword("test-password");

            try (OutputStream encryptedStream =
                         encryptor.getDataStream(fileSystem)) {
                encryptedStream.write(plainDocx);
            }

            fileSystem.writeFilesystem(encryptedOutput);

            return encryptedOutput.toByteArray();
        }
    }
}
