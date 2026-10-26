package com.yu.ai.rag.answer;

import com.yu.ai.rag.retrieval.HybridRetrievedChunk;
import com.yu.ai.rag.retrieval.RerankedChunk;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * A transparent baseline policy. It is a heuristic, not a semantic guarantee:
 * either a similarity threshold or a real keyword match can make a chunk strong.
 */
@Component
public class EvidencePolicy {

    private final EvidencePolicyProperties properties;

    public EvidencePolicy(EvidencePolicyProperties properties) {
        this.properties = properties;
    }

    public EvidenceAssessment assess(List<RerankedChunk> retrievedEvidence) {
        List<RerankedChunk> usable = retrievedEvidence == null
                ? List.of()
                : retrievedEvidence.stream()
                    .filter(this::hasUsableContent)
                    .toList();

        if (usable.isEmpty()) {
            return new EvidenceAssessment(false, RefusalReason.NO_RETRIEVAL, 0, 0);
        }

        int strongCount = (int) usable.stream().filter(this::isStrongEvidence).count();
        boolean sufficient = strongCount >= properties.getMinimumStrongEvidenceCount();
        return new EvidenceAssessment(
                sufficient,
                sufficient ? null : RefusalReason.LOW_EVIDENCE,
                usable.size(),
                strongCount);
    }

    private boolean hasUsableContent(RerankedChunk item) {
        return item != null
                && item.chunk() != null
                && StringUtils.hasText(item.chunk().content());
    }

    private boolean isStrongEvidence(RerankedChunk item) {
        HybridRetrievedChunk chunk = item.chunk();
        Double similarity = chunk.similarity();
        boolean semanticPass = similarity != null
                && Double.isFinite(similarity)
                && similarity >= properties.getMinimumSemanticSimilarity();

        Integer matchedTerms = chunk.matchedTerms();
        boolean keywordPass = properties.isAllowKeywordMatch()
                && matchedTerms != null
                && matchedTerms > 0;

        return semanticPass || keywordPass;
    }
}
