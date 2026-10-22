
package com.yu.ai.rag.chunking;

import com.yu.ai.rag.parser.ParsedDocument;
import com.yu.ai.rag.parser.ParsedSegment;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker();

    @Test
    void emptyDocumentShouldReturnEmptyList() {
        List<TextChunk> chunks = chunker.chunk(
                "doc-empty",
                new ParsedDocument(""),
                new ChunkingConfig(10, 2)
        );

        assertTrue(chunks.isEmpty());
    }

    @Test
    void shortDocumentShouldProduceOneChunk() {
        String text = "这是一段短文本。";

        ParsedDocument document = new ParsedDocument(
                text,
                List.of(new ParsedSegment(text, 1, "简介"))
        );

        List<TextChunk> chunks = chunker.chunk(
                "doc-short",
                document,
                new ChunkingConfig(100, 10)
        );

        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0).text());
        assertEquals("doc-short", chunks.get(0).documentId());
        assertEquals(0, chunks.get(0).chunkIndex());
        assertEquals(Integer.valueOf(1), chunks.get(0).pageNumber());
        assertEquals("简介", chunks.get(0).sectionTitle());
    }

    @Test
    void longParagraphShouldBeSplitWithOverlap() {
        String text = "ABCDEFGHIJKLMNO";

        List<TextChunk> chunks = chunker.chunk(
                "doc-long",
                new ParsedDocument(text),
                new ChunkingConfig(10, 3)
        );

        assertEquals(
                List.of("ABCDEFGHIJ", "HIJKLMNO"),
                chunks.stream().map(TextChunk::text).toList()
        );

        assertTrue(chunks.stream()
                .allMatch(chunk -> chunk.text().length() <= 10));

        assertEquals(
                List.of(0, 1),
                chunks.stream().map(TextChunk::chunkIndex).toList()
        );
    }

    @Test
    void chineseTextShouldBeSplitWithoutLosingCharacters() {
        String text = "这是一个很长的中文文本内容";

        List<TextChunk> chunks = chunker.chunk(
                "doc-chinese",
                new ParsedDocument(text),
                new ChunkingConfig(6, 2)
        );

        assertTrue(chunks.size() > 1);

        assertTrue(chunks.stream()
                .allMatch(chunk -> chunk.text().length() <= 6));

        // 去除每个后续切片开头的重叠部分，还原原文。
        StringBuilder rebuilt = new StringBuilder(chunks.get(0).text());

        for (int i = 1; i < chunks.size(); i++) {
            rebuilt.append(chunks.get(i).text().substring(2));
        }

        assertEquals(text, rebuilt.toString());
    }

    @Test
    void overlapBoundaryShouldBeCorrect() {
        ChunkingConfig config = new ChunkingConfig(5, 2);

        // 文本长度刚好等于上限，不应多切一个片段。
        List<TextChunk> exactBoundary = chunker.chunk(
                "doc-exact",
                new ParsedDocument("ABCDE"),
                config
        );

        assertEquals(
                List.of("ABCDE"),
                exactBoundary.stream().map(TextChunk::text).toList()
        );

        // 超过上限一个字符时，仍然正确重叠切分。
        List<TextChunk> overBoundary = chunker.chunk(
                "doc-over",
                new ParsedDocument("ABCDEF"),
                config
        );

        assertEquals(
                List.of("ABCDE", "DEF"),
                overBoundary.stream().map(TextChunk::text).toList()
        );

        assertEquals(0, overBoundary.get(0).chunkIndex());
        assertEquals(1, overBoundary.get(1).chunkIndex());
    }

    @Test
    void paragraphsShouldBeCombinedAndMetadataPreserved() {
        String text = "第一段。\n\n第二段。";

        ParsedDocument document = new ParsedDocument(
                text,
                List.of(new ParsedSegment(text, 2, "第1章"))
        );

        List<TextChunk> chunks = chunker.chunk(
                "doc-paragraph",
                document,
                new ChunkingConfig(30, 5)
        );

        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0).text());
        assertEquals(Integer.valueOf(2), chunks.get(0).pageNumber());
        assertEquals("第1章", chunks.get(0).sectionTitle());
    }


    @Test
    void exportChunkPreviewForManualInspection() throws IOException {
        String longParagraph =
                "这是一个用于验证超长段落切分与重叠效果的中文样例。"
                        .repeat(8);

        String text = String.join(
                "\n\n",
                "RAG 系统通过检索增强生成回答。",
                "上传文档后，系统先解析文本，再切分为多个片段。",
                longParagraph,
                "切片需要保留来源信息，方便回答引用原始文档。"
        );

        ChunkingConfig config = new ChunkingConfig(80, 12);

        ParsedDocument document = new ParsedDocument(
                text,
                List.of(new ParsedSegment(text, 1, "Day10 示例"))
        );

        List<TextChunk> chunks = chunker.chunk(
                "doc-preview",
                document,
                config
        );

        assertFalse(chunks.isEmpty());

        for (int i = 0; i < chunks.size(); i++) {
            TextChunk chunk = chunks.get(i);

            assertEquals(i, chunk.chunkIndex());
            assertFalse(chunk.text().isBlank());
            assertTrue(chunk.text().length() <= config.maxChunkSize());
        }

        StringBuilder output = new StringBuilder();

        output.append("documentId=doc-preview\n");
        output.append("maxChunkSize=80, overlapSize=12\n");
        output.append("chunkCount=").append(chunks.size()).append("\n\n");

        for (TextChunk chunk : chunks) {
            output.append("========== Chunk ")
                    .append(chunk.chunkIndex())
                    .append(" ==========\n");

            output.append("length=")
                    .append(chunk.text().length())
                    .append("\n");

            output.append("pageNumber=")
                    .append(chunk.pageNumber())
                    .append("\n");

            output.append("sectionTitle=")
                    .append(chunk.sectionTitle())
                    .append("\n");

            output.append("text:\n")
                    .append(chunk.text())
                    .append("\n\n");
        }

        Path outputPath = Path.of("target", "chunk-preview.txt");

        Files.createDirectories(outputPath.getParent());

        Files.writeString(
                outputPath,
                output.toString(),
                StandardCharsets.UTF_8
        );

        System.out.println(
                "Chunk preview exported to: "
                        + outputPath.toAbsolutePath()
        );
    }

}
