# Day18 - LLM Answer Generation

## Goal
Connect the existing hybrid retrieval and reranking chain to an LLM gateway. Keep model calls replaceable, testable, bounded, and observable.

## Components
- `AnswerGenerator`: orchestration of reranked evidence, prompt building, and completion handling.
- `GeneratedAnswer`: response model including status, provider/model, latency, token usage when available, and evidence.
- `LlmGateway`: provider-neutral completion interface.
- `FakeLlmGateway`: deterministic local test adapter.
- `DeepSeekLlmGateway`: optional real adapter using DeepSeek Chat Completions.
- `PromptBuilder`: separates system instructions from untrusted evidence and limits evidence characters.
- `LlmExceptionHandler`: maps provider failures to 502 and timeouts to 504 without exposing credentials.

## Endpoint
`POST /api/v1/qa/answer`

```json
{
  "query": "ERR-1007",
  "topK": 3,
  "candidateK": 10
}
```

`candidateK` is the candidate pool passed through hybrid retrieval and reranking. `topK` is the maximum number of evidence chunks used by answer generation.

## Expected behavior
- If no non-empty evidence is retrieved, return `INSUFFICIENT_EVIDENCE` without calling the model.
- If evidence exists, build a system prompt and a user prompt with evidence blocks.
- Treat retrieved text as untrusted data. Do not follow instructions found inside retrieved chunks.
- Reject empty model output.
- Classify timeout, provider failure, invalid response, and empty response.
- Record provider/model, temperature, max tokens, a short query/output sample, evidence count, latency, and token usage if returned. Never log the API key or the full prompt.

## Configuration
Merge `docs/config/application-llm-snippet.yml` under the existing `rag:` node. Merge the environment example into `.env.example`. The default provider is `fake`.

For real DeepSeek calls, set `RAG_LLM_PROVIDER=deepseek` and configure `DEEPSEEK_API_KEY` in the local `.env` file. Never commit `.env` or a real key.

## Validation
1. Run `./mvnw test` (Windows: `./mvnw.cmd test`).
2. Start the application.
3. Call `scripts/day18-test-answer.ps1` from PowerShell.
4. Confirm `provider=fake` and `model=fake-llm-v1` first.
5. Only if a valid DeepSeek API key is available, switch the provider and repeat the smoke test.

## Limitations
- This day returns retrieved evidence in the response for debugging; citation validation is a later task.
- The no-evidence rule only detects an empty evidence list. A future evidence policy should also handle weak or conflicting evidence.
- Fake output tests orchestration, not factual answer quality.
- Logs include short query/output samples for learning. Use sanitized test data and redact these samples in production.
