package com.yu.ai.rag.answer;

import com.yu.ai.rag.document.DocumentRepository;
import com.yu.ai.rag.retrieval.HybridRetrievedChunk;
import com.yu.ai.rag.retrieval.RerankedChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates model-provided IDs against evidence included in the current request only. */
@Component
public class CitationValidator {

    private static final Pattern CITATION_ID_PATTERN = Pattern.compile("C[1-9][0-9]*");
    private static final Pattern INLINE_CITATION_PATTERN = Pattern.compile("\\[(C[0-9]+)\\]");

    private final DocumentRepository documentRepository;

    public CitationValidator(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public CitationValidationResult validate(
            List<String> requestedCitationIds,
            Map<String, RerankedChunk> currentEvidence) {

        Map<String, RerankedChunk> safeEvidence = currentEvidence == null
                ? Map.of()
                : currentEvidence;
        LinkedHashMap<String, CitationSource> accepted = new LinkedHashMap<>();
        int rejected = 0;

        if (requestedCitationIds == null) {
            return new CitationValidationResult(List.of(), List.of(), 0);
        }

        for (String rawId : requestedCitationIds) {
            if (rawId == null) {
                rejected++;
                continue;
            }

            String citationId = rawId.trim();
            if (!CITATION_ID_PATTERN.matcher(citationId).matches()) {
                rejected++;
                continue;
            }
            if (accepted.containsKey(citationId)) {
                continue;
            }

            RerankedChunk rerankedChunk = safeEvidence.get(citationId);
            if (rerankedChunk == null || rerankedChunk.chunk() == null) {
                rejected++;
                continue;
            }

            HybridRetrievedChunk chunk = rerankedChunk.chunk();
            Optional<String> documentName = documentRepository.findOriginalFilenameById(chunk.documentId());
            if (documentName.isEmpty() || documentName.get().isBlank()) {
                rejected++;
                continue;
            }

            CitationSource source = new CitationSource(
                    citationId,
                    chunk.documentId(),
                    documentName.get(),
                    chunk.chunkId(),
                    chunk.chunkIndex(),
                    chunk.pageNumber(),
                    chunk.sectionTitle()
            );
            accepted.put(citationId, source);
        }

        return new CitationValidationResult(
                new ArrayList<>(accepted.keySet()),
                new ArrayList<>(accepted.values()),
                rejected
        );
    }

    /** Remove inline [C123] markers that did not survive server-side validation. */
    public String removeUnacceptedInlineMarkers(String answer, Set<String> acceptedCitationIds) {
        if (answer == null || answer.isEmpty()) {
            return answer == null ? "" : answer;
        }
        Set<String> safeAccepted = acceptedCitationIds == null ? Set.of() : acceptedCitationIds;
        Matcher matcher = INLINE_CITATION_PATTERN.matcher(answer);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String id = matcher.group(1);
            String replacement = safeAccepted.contains(id) ? matcher.group() : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString()
                .replaceAll("[ \\t]{2,}", " ")
                .replaceAll(" +([,.!?;:])", "$1")
                .trim();
    }
}
