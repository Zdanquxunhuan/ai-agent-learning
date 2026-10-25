# Day 19 - Citation Source Validation

## Goal

Attach request-local citation IDs to retrieved evidence, require a structured JSON response from the LLM, validate every returned ID against the evidence actually included in the prompt, and resolve accepted citations to document metadata.

## API behavior

The existing `POST /api/v1/qa/answer` endpoint is retained. The response adds:

- `citationIds`: only validated citation IDs.
- `citations`: validated source metadata (`citationId`, document name, document ID, chunk ID/index, page, and section).

The existing `evidence` array is retained for debugging and evaluation.

## Validation rules

1. Citation IDs are assigned by the server as `C1`, `C2`, and so on for each request.
2. Only evidence blocks actually included in the prompt are eligible for citation.
3. IDs absent from the current request's evidence map are rejected.
4. Duplicate valid IDs are deduplicated.
5. Inline markers such as `[C999]` are removed unless that ID is validated.
6. If the model claims to answer but no valid citation remains, the server returns `INSUFFICIENT_EVIDENCE` with `NO_VALID_CITATIONS` instead of returning an unverified claim.
7. If the model returns `insufficientEvidence=true`, the response remains a refusal and exposes no citations.

## Test

Run the full test suite from PowerShell:

```powershell
$env:JAVA_HOME = "F:\ayd\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd test
```

Then start the service and run:

```powershell
.\scripts\day19-test-citations.ps1 -BaseUrl http://localhost:8080
```

Use port 9020 if that is the active local server port.

## Limitations

Citation validation proves that a citation ID maps to evidence returned for this request. It does not prove that every factual statement in the answer is entailed by the cited text. That requires further answer-quality evaluation and, if needed, claim-level verification.
