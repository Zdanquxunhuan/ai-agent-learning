# Day 16 - Hybrid Retrieval

## Implementation

The endpoint `POST /api/v1/retrieval/hybrid-search` accepts the same `{ "query": "...", "topK": 3 }` request as the existing retrieval endpoints.

The service calls the existing semantic and keyword retrievers. It fuses the one-based rank from each list with Reciprocal Rank Fusion:

`RRF(chunk) = (semanticRank exists ? 1 / (60 + semanticRank) : 0) + (keywordRank exists ? 1 / (60 + keywordRank) : 0)`

The service deduplicates by `chunkId`, sorts by RRF score descending, applies deterministic tie-breakers, and returns at most `topK` chunks. `similarity` is the vector score, `matchedTerms` is the keyword match count, and `rrfScore` is the fused ranking score. These values are not on the same scale.

## Fixed query set

The PowerShell comparison script uses the same Q01-Q10 identifier-oriented set from Day 15: ERR-1007, BGE-M3, v2.1.0, ORD_2026_0001, a Chinese phrase about database connection failure, a mixed Chinese/error-code query, BAAI/bge-m3, pgvector, chunkIndex, and a mixed order-status query.

## Run

1. Start PostgreSQL and the Spring Boot service.
2. Ensure the Day 15 sample document has status `READY` and at least one row in `document_chunks`.
3. From the project root, run `./mvnw.cmd test`.
4. Run `./scripts/day16-compare-retrieval.ps1` in PowerShell. The default base URL is `http://localhost:8080`, matching the uploaded `application.yml` default. If `SERVER_PORT` or `.env` overrides the port, pass the matching URL, for example `./scripts/day16-compare-retrieval.ps1 -BaseUrl http://localhost:9020`.

## Outputs

- `docs/day16-hybrid-retrieval-comparison.csv`: one row per question, with all three strategies side by side.
- `docs/day16-hybrid-retrieval-details.csv`: one row per question/strategy, including Top-K snippets.
- `docs/day16-hybrid-retrieval-analysis.md`: measured hit counts and interpretation notes.

The expected-token check is a small diagnostic, not a complete retrieval benchmark. Review the actual Top-K snippets and record both wins and failures.
