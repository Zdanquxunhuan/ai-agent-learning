package com.yu.ai.rag.answer;

import com.yu.ai.rag.retrieval.RerankedChunk;

/** A request-local citation ID paired with the exact evidence shown to the LLM. */
public record CitationCandidate(String citationId, RerankedChunk evidence) {
}
