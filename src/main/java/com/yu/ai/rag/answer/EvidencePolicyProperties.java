package com.yu.ai.rag.answer;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configurable, deliberately simple evidence-quality heuristic. */
@Component
@ConfigurationProperties(prefix = "rag.answer.evidence-policy")
public class EvidencePolicyProperties {

    /** Minimum 1 - cosine similarity is not a probability or correctness score. */
    private double minimumSemanticSimilarity = 0.35;
    /** Number of chunks that must pass at least one evidence-quality rule. */
    private int minimumStrongEvidenceCount = 1;
    /** Allow an actual keyword match to support exact identifiers and codes. */
    private boolean allowKeywordMatch = true;

    @PostConstruct
    public void validate() {
        if (!Double.isFinite(minimumSemanticSimilarity)
                || minimumSemanticSimilarity < -1.0
                || minimumSemanticSimilarity > 1.0) {
            throw new IllegalStateException(
                    "rag.answer.evidence-policy.minimum-semantic-similarity must be between -1 and 1");
        }
        if (minimumStrongEvidenceCount < 1 || minimumStrongEvidenceCount > 20) {
            throw new IllegalStateException(
                    "rag.answer.evidence-policy.minimum-strong-evidence-count must be between 1 and 20");
        }
    }

    public double getMinimumSemanticSimilarity() { return minimumSemanticSimilarity; }
    public void setMinimumSemanticSimilarity(double value) { this.minimumSemanticSimilarity = value; }
    public int getMinimumStrongEvidenceCount() { return minimumStrongEvidenceCount; }
    public void setMinimumStrongEvidenceCount(int value) { this.minimumStrongEvidenceCount = value; }
    public boolean isAllowKeywordMatch() { return allowKeywordMatch; }
    public void setAllowKeywordMatch(boolean value) { this.allowKeywordMatch = value; }
}
