
package com.yu.ai.rag.chunking;

import com.yu.ai.rag.parser.ParsedDocument;
import com.yu.ai.rag.parser.ParsedSegment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class TextChunker {

    public List<TextChunk> chunk(
            String documentId,
            ParsedDocument document,
            ChunkingConfig config
    ) {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException(
                    "documentId must not be blank"
            );
        }

        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(config, "config must not be null");

        List<TextChunk> chunks = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();

        Integer currentPageNumber = null;
        String currentSectionTitle = null;

        List<ParsedSegment> segments = document.segments();

        if (segments == null || segments.isEmpty()) {
            if (document.text() == null || document.text().isBlank()) {
                return List.of();
            }

            segments = List.of(
                    new ParsedSegment(document.text(), null, null)
            );
        }

        for (ParsedSegment segment : segments) {
            if (segment == null
                    || segment.text() == null
                    || segment.text().isBlank()) {
                continue;
            }

            // 优先按空行切分段落。
            String[] paragraphs = segment.text().split(
                    "\\R[\\t ]*\\R+"
            );

            for (String rawParagraph : paragraphs) {
                String paragraph = rawParagraph.trim();

                if (paragraph.isEmpty()) {
                    continue;
                }

                // 超长段落按长度切分，并保留相邻切片的重叠内容。
                if (paragraph.length() > config.maxChunkSize()) {
                    addChunk(
                            chunks,
                            documentId,
                            currentText,
                            currentPageNumber,
                            currentSectionTitle
                    );
                    currentText.setLength(0);

                    List<String> pieces = splitByLength(
                            paragraph,
                            config.maxChunkSize(),
                            config.overlapSize()
                    );

                    for (String piece : pieces) {
                        chunks.add(new TextChunk(
                                documentId,
                                chunks.size(),
                                piece,
                                segment.pageNumber(),
                                segment.sectionTitle()
                        ));
                    }

                    currentPageNumber = null;
                    currentSectionTitle = null;
                    continue;
                }

                boolean hasCurrentText = currentText.length() > 0;

                // 页码或章节变化时，不跨来源元数据切片。
                boolean metadataChanged = hasCurrentText
                        && (!Objects.equals(
                        currentPageNumber,
                        segment.pageNumber()
                ) || !Objects.equals(
                        currentSectionTitle,
                        segment.sectionTitle()
                ));

                int separatorLength = hasCurrentText ? 2 : 0;

                boolean exceedsLimit = hasCurrentText
                        && currentText.length()
                        + separatorLength
                        + paragraph.length()
                        > config.maxChunkSize();

                if (metadataChanged || exceedsLimit) {
                    String previousText = currentText.toString();

                    addChunk(
                            chunks,
                            documentId,
                            currentText,
                            currentPageNumber,
                            currentSectionTitle
                    );

                    currentText.setLength(0);

                    // 同一来源内才添加 overlap。
                    // 优先保留完整段落；空间不足时减少重叠长度。
                    if (!metadataChanged) {
                        int availableForOverlap =
                                config.maxChunkSize()
                                        - paragraph.length()
                                        - 2;

                        int overlapLength = Math.min(
                                config.overlapSize(),
                                Math.min(
                                        previousText.length(),
                                        Math.max(0, availableForOverlap)
                                )
                        );

                        if (overlapLength > 0) {
                            currentText.append(
                                    previousText.substring(
                                            previousText.length()
                                                    - overlapLength
                                    )
                            );
                        }
                    }
                }

                if (currentText.length() == 0) {
                    currentPageNumber = segment.pageNumber();
                    currentSectionTitle = segment.sectionTitle();
                } else {
                    currentText.append("\n\n");
                }

                currentText.append(paragraph);
            }
        }

        // 保存最后一个切片。
        addChunk(
                chunks,
                documentId,
                currentText,
                currentPageNumber,
                currentSectionTitle
        );

        return List.copyOf(chunks);
    }

    /**
     * 按最大长度切分超长段落。
     * 下一片从上一片末尾向前 overlapSize 个字符处开始。
     */
    private List<String> splitByLength(
            String text,
            int maxChunkSize,
            int overlapSize
    ) {
        List<String> pieces = new ArrayList<>();

        int step = maxChunkSize - overlapSize;

        for (int start = 0; start < text.length();) {
            int end = Math.min(
                    start + maxChunkSize,
                    text.length()
            );

            pieces.add(text.substring(start, end));

            if (end == text.length()) {
                break;
            }

            start += step;
        }

        return pieces;
    }

    private void addChunk(
            List<TextChunk> chunks,
            String documentId,
            StringBuilder text,
            Integer pageNumber,
            String sectionTitle
    ) {
        if (text.length() == 0) {
            return;
        }

        chunks.add(new TextChunk(
                documentId,
                chunks.size(),
                text.toString(),
                pageNumber,
                sectionTitle
        ));
    }
}
