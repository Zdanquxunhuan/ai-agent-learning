# Day 16 Hybrid Retrieval Analysis

- Generated at: 2026-10-24 22:49:55 +08:00
- Base URL: http://localhost:8080
- TopK: 3
- Fixed query count: 10

## Measured summary

| Strategy | Successful requests | Queries with expected token in Top-K | Top-1 expected-token hits | Total returned chunks |
|---|---:|---:|---:|---:|
| Vector | 10/10 | 10/10 | 9/10 | 30 |
| Keyword | 10/10 | 10/10 | 10/10 | 18 |
| Hybrid | 10/10 | 10/10 | 10/10 | 30 |

## Per-query expected-token check

| Query | Category | Vector Top-K hit | Keyword Top-K hit | Hybrid Top-K hit |
|---|---|---:|---:|---:|
| Q01 | error-code | True | True | True |
| Q02 | model-name | True | True | True |
| Q03 | version | True | True | True |
| Q04 | order-id | True | True | True |
| Q05 | chinese-phrase | True | True | True |
| Q06 | mixed-semantic-and-code | True | True | True |
| Q07 | embedding-model | True | True | True |
| Q08 | database-extension | True | True | True |
| Q09 | code-identifier | True | True | True |
| Q10 | mixed-order-query | True | True | True |

## How to interpret the results

- Vector-only is useful for semantic paraphrases and questions whose wording differs from the source text. It can still rank exact identifiers below semantically related chunks.
- Keyword-only is useful for exact error codes, order IDs, model names, version strings, and code identifiers. This implementation uses token extraction plus case-insensitive substring matching; it is not a full Chinese search engine or synonym system.
- Hybrid combines complementary candidate lists using Reciprocal Rank Fusion (RRF). A chunk present in both lists receives contributions from both ranks, then results are deduplicated by chunkId.
- RRF scores, vector similarity scores, and keyword matchedTerms counts are different quantities. Do not compare their raw numeric values directly across strategies.
- ExpectedTokenHit is a simple diagnostic: it checks whether the expected literal appears in any returned Top-K chunk. It is not a complete relevance, precision, or recall evaluation. Manually inspect TopResults before claiming one strategy is better.

## Files

- docs/day16-hybrid-retrieval-comparison.csv: one row per question, with Vector/Keyword/Hybrid metrics side by side.
- docs/day16-hybrid-retrieval-details.csv: one row per question and strategy, including Top-K snippets and errors.
- docs/day16-hybrid-retrieval-analysis.md: this report, including measured summary counts.