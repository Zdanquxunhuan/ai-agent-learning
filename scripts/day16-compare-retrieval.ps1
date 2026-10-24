param(
    [string]$BaseUrl = "http://localhost:8080",
    [ValidateRange(1, 20)]
    [int]$TopK = 3
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd('/')

Add-Type -AssemblyName System.Net.Http
$script:HttpClient = [System.Net.Http.HttpClient]::new()
$script:HttpClient.Timeout = [TimeSpan]::FromSeconds(60)

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$DocsDir = Join-Path $ProjectRoot "docs"
if (-not (Test-Path $DocsDir)) {
    New-Item -ItemType Directory -Path $DocsDir -Force | Out-Null
}

# Unicode characters in these JSON strings are escaped so this script remains
# compatible with Windows PowerShell 5.1 even if the file encoding is changed.
$Queries = @(
    '{"Id":"Q01","Query":"ERR-1007","ExpectedToken":"ERR-1007","Category":"error-code"}',
    '{"Id":"Q02","Query":"BGE-M3","ExpectedToken":"BGE-M3","Category":"model-name"}',
    '{"Id":"Q03","Query":"v2.1.0","ExpectedToken":"v2.1.0","Category":"version"}',
    '{"Id":"Q04","Query":"ORD_2026_0001","ExpectedToken":"ORD_2026_0001","Category":"order-id"}',
    '{"Id":"Q05","Query":"\u6570\u636e\u5e93\u8fde\u63a5\u5931\u8d25","ExpectedToken":"\u6570\u636e\u5e93\u8fde\u63a5\u5931\u8d25","Category":"chinese-phrase"}',
    '{"Id":"Q06","Query":"\u5982\u4f55\u89e3\u51b3 ERR-1007 \u9519\u8bef","ExpectedToken":"ERR-1007","Category":"mixed-semantic-and-code"}',
    '{"Id":"Q07","Query":"BAAI/bge-m3","ExpectedToken":"BAAI/bge-m3","Category":"embedding-model"}',
    '{"Id":"Q08","Query":"pgvector","ExpectedToken":"pgvector","Category":"database-extension"}',
    '{"Id":"Q09","Query":"chunkIndex","ExpectedToken":"chunkIndex","Category":"code-identifier"}',
    '{"Id":"Q10","Query":"\u8ba2\u5355 ORD_2026_0001 \u72b6\u6001","ExpectedToken":"ORD_2026_0001","Category":"mixed-order-query"}'
) | ForEach-Object { ConvertFrom-Json $_ }

function Invoke-Retrieval {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Query
    )

    $requestBody = @{
        query = $Query
        topK = $TopK
    } | ConvertTo-Json -Compress -Depth 5

    $content = $null
    $response = $null

    try {
        $content = [System.Net.Http.StringContent]::new(
            $requestBody,
            [System.Text.Encoding]::UTF8,
            "application/json"
        )

        $response = $script:HttpClient.PostAsync(
            "$BaseUrl$Path",
            $content
        ).GetAwaiter().GetResult()

        $statusCode = [int]$response.StatusCode
        $responseBytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
        $responseText = [System.Text.Encoding]::UTF8.GetString($responseBytes)

        $parsed = $null
        try {
            $parsed = $responseText | ConvertFrom-Json -ErrorAction Stop
        }
        catch {
            $parsed = $null
        }

        $apiSuccess = (
            $response.IsSuccessStatusCode -and
            $null -ne $parsed -and
            $parsed.success -eq $true
        )

        $items = @()
        if ($apiSuccess -and $null -ne $parsed.data) {
            $items = @($parsed.data)
        }

        $errorMessage = $null
        if (-not $apiSuccess) {
            if ($null -ne $parsed -and $parsed.errorMessage) {
                $errorMessage = [string]$parsed.errorMessage
            }
            elseif (-not [string]::IsNullOrWhiteSpace($responseText)) {
                $errorMessage = $responseText
            }
            else {
                $errorMessage = "HTTP request failed"
            }
        }

        return [pscustomobject]@{
            HttpStatus  = $statusCode
            ApiSuccess  = [bool]$apiSuccess
            Results     = $items
            ErrorMessage = $errorMessage
        }
    }
    catch {
        return [pscustomobject]@{
            HttpStatus   = 0
            ApiSuccess   = $false
            Results      = @()
            ErrorMessage = $_.Exception.GetBaseException().Message
        }
    }
    finally {
        if ($null -ne $content) {
            $content.Dispose()
        }
        if ($null -ne $response) {
            $response.Dispose()
        }
    }
}

function Test-ExpectedToken {
    param(
        [object]$Item,
        [string]$ExpectedToken
    )

    if ($null -eq $Item -or $null -eq $Item.content) {
        return $false
    }

    return ([string]$Item.content).IndexOf(
        $ExpectedToken,
        [System.StringComparison]::OrdinalIgnoreCase
    ) -ge 0
}

function Get-StrategyMetrics {
    param(
        [object]$ApiResult,
        [string]$ExpectedToken
    )

    $items = @($ApiResult.Results | Where-Object { $null -ne $_ })
    $expectedHit = $false
    foreach ($item in $items) {
        if (Test-ExpectedToken -Item $item -ExpectedToken $ExpectedToken) {
            $expectedHit = $true
            break
        }
    }

    $top = $null
    if ($items.Count -gt 0) {
        $top = $items[0]
    }

    $top1Hit = Test-ExpectedToken -Item $top -ExpectedToken $ExpectedToken
    $top1Score = $null
    $top1SemanticRank = $null
    $top1KeywordRank = $null
    $top1MatchedTerms = $null
    $top1ChunkId = $null
    $top1DocumentId = $null
    $top1ChunkIndex = $null

    if ($null -ne $top) {
        $top1ChunkId = $top.chunkId
        $top1DocumentId = $top.documentId
        $top1ChunkIndex = $top.chunkIndex

        $propertyNames = @($top.PSObject.Properties | ForEach-Object { $_.Name })
        if ($propertyNames -contains "rrfScore") {
            $top1Score = $top.rrfScore
        }
        elseif ($propertyNames -contains "similarity") {
            $top1Score = $top.similarity
        }
        elseif ($propertyNames -contains "matchedTerms") {
            $top1Score = $top.matchedTerms
        }

        if ($propertyNames -contains "semanticRank") {
            $top1SemanticRank = $top.semanticRank
        }
        if ($propertyNames -contains "keywordRank") {
            $top1KeywordRank = $top.keywordRank
        }
        if ($propertyNames -contains "matchedTerms") {
            $top1MatchedTerms = $top.matchedTerms
        }
    }

    $topResults = @()
    for ($i = 0; $i -lt $items.Count; $i++) {
        $item = $items[$i]
        $snippet = ([string]$item.content -replace '\s+', ' ').Trim()
        if ($snippet.Length -gt 140) {
            $snippet = $snippet.Substring(0, 140) + "..."
        }

        $scoreText = "n/a"
        $itemProperties = @($item.PSObject.Properties | ForEach-Object { $_.Name })
        if ($itemProperties -contains "rrfScore") {
            $scoreText = "rrf=" + ([double]$item.rrfScore).ToString("F6", [Globalization.CultureInfo]::InvariantCulture)
        }
        elseif ($itemProperties -contains "similarity") {
            $scoreText = "similarity=" + ([double]$item.similarity).ToString("F6", [Globalization.CultureInfo]::InvariantCulture)
        }
        elseif ($itemProperties -contains "matchedTerms") {
            $scoreText = "matchedTerms=" + [string]$item.matchedTerms
        }

        $topResults += ("{0}: chunk={1}; {2}; {3}" -f ($i + 1), $item.chunkId, $scoreText, $snippet)
    }

    return [pscustomobject]@{
        HttpStatus             = $ApiResult.HttpStatus
        ApiSuccess             = $ApiResult.ApiSuccess
        ErrorMessage           = $ApiResult.ErrorMessage
        ResultCount            = $items.Count
        ExpectedTokenHit       = $expectedHit
        Top1ExpectedTokenHit   = $top1Hit
        Top1ChunkId            = $top1ChunkId
        Top1DocumentId         = $top1DocumentId
        Top1ChunkIndex         = $top1ChunkIndex
        Top1Score              = $top1Score
        Top1SemanticRank       = $top1SemanticRank
        Top1KeywordRank        = $top1KeywordRank
        Top1MatchedTerms       = $top1MatchedTerms
        TopResults             = ($topResults -join " || ")
    }
}

$summaryRows = New-Object System.Collections.Generic.List[object]
$detailRows = New-Object System.Collections.Generic.List[object]
$strategies = @(
    [pscustomobject]@{ Name = "Vector";  Path = "/api/v1/retrieval/search" },
    [pscustomobject]@{ Name = "Keyword"; Path = "/api/v1/retrieval/keyword-search" },
    [pscustomobject]@{ Name = "Hybrid";  Path = "/api/v1/retrieval/hybrid-search" }
)

foreach ($question in $Queries) {
    Write-Host ("Running {0}: {1}" -f $question.Id, $question.Query)
    $wideRow = [ordered]@{
        QuestionId   = $question.Id
        Query        = $question.Query
        ExpectedToken = $question.ExpectedToken
        Category     = $question.Category
    }

    foreach ($strategy in $strategies) {
        $apiResult = Invoke-Retrieval -Path $strategy.Path -Query $question.Query
        $metrics = Get-StrategyMetrics -ApiResult $apiResult -ExpectedToken $question.ExpectedToken

        foreach ($property in $metrics.PSObject.Properties) {
            $wideRow[($strategy.Name + $property.Name)] = $property.Value
        }

        $detailRows.Add([pscustomobject]@{
            QuestionId           = $question.Id
            Query                = $question.Query
            ExpectedToken        = $question.ExpectedToken
            Category             = $question.Category
            Strategy             = $strategy.Name
            HttpStatus           = $metrics.HttpStatus
            ApiSuccess           = $metrics.ApiSuccess
            ErrorMessage         = $metrics.ErrorMessage
            ResultCount          = $metrics.ResultCount
            ExpectedTokenHit     = $metrics.ExpectedTokenHit
            Top1ExpectedTokenHit = $metrics.Top1ExpectedTokenHit
            Top1ChunkId          = $metrics.Top1ChunkId
            Top1DocumentId       = $metrics.Top1DocumentId
            Top1ChunkIndex       = $metrics.Top1ChunkIndex
            Top1Score            = $metrics.Top1Score
            Top1SemanticRank     = $metrics.Top1SemanticRank
            Top1KeywordRank      = $metrics.Top1KeywordRank
            Top1MatchedTerms     = $metrics.Top1MatchedTerms
            TopResults           = $metrics.TopResults
        })

        if (-not $metrics.ApiSuccess) {
            Write-Warning ("{0} {1} failed: {2}" -f $question.Id, $strategy.Name, $metrics.ErrorMessage)
        }
    }

    $summaryRows.Add([pscustomobject]$wideRow)
}

$summaryPath = Join-Path $DocsDir "day16-hybrid-retrieval-comparison.csv"
$detailsPath = Join-Path $DocsDir "day16-hybrid-retrieval-details.csv"
$analysisPath = Join-Path $DocsDir "day16-hybrid-retrieval-analysis.md"

$summaryRows | Export-Csv -Path $summaryPath -NoTypeInformation -Encoding UTF8
$detailRows | Export-Csv -Path $detailsPath -NoTypeInformation -Encoding UTF8

$reportLines = New-Object System.Collections.Generic.List[string]
$reportLines.Add("# Day 16 Hybrid Retrieval Analysis")
$reportLines.Add("")
$reportLines.Add(("- Generated at: {0}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz")))
$reportLines.Add(("- Base URL: {0}" -f $BaseUrl))
$reportLines.Add(("- TopK: {0}" -f $TopK))
$reportLines.Add(("- Fixed query count: {0}" -f $Queries.Count))
$reportLines.Add("")
$reportLines.Add("## Measured summary")
$reportLines.Add("")
$reportLines.Add("| Strategy | Successful requests | Queries with expected token in Top-K | Top-1 expected-token hits | Total returned chunks |")
$reportLines.Add("|---|---:|---:|---:|---:|")

foreach ($strategyName in @("Vector", "Keyword", "Hybrid")) {
    $strategyRows = @($detailRows | Where-Object { $_.Strategy -eq $strategyName })
    $successCount = @($strategyRows | Where-Object { $_.ApiSuccess }).Count
    $hitCount = @($strategyRows | Where-Object { $_.ExpectedTokenHit }).Count
    $top1HitCount = @($strategyRows | Where-Object { $_.Top1ExpectedTokenHit }).Count
    $totalResults = ($strategyRows | Measure-Object -Property ResultCount -Sum).Sum
    if ($null -eq $totalResults) { $totalResults = 0 }
    $reportLines.Add(("| {0} | {1}/{2} | {3}/{2} | {4}/{2} | {5} |" -f $strategyName, $successCount, $Queries.Count, $hitCount, $top1HitCount, $totalResults))
}

$reportLines.Add("")
$reportLines.Add("## Per-query expected-token check")
$reportLines.Add("")
$reportLines.Add("| Query | Category | Vector Top-K hit | Keyword Top-K hit | Hybrid Top-K hit |")
$reportLines.Add("|---|---|---:|---:|---:|")
foreach ($question in $Queries) {
    $v = @($detailRows | Where-Object { $_.QuestionId -eq $question.Id -and $_.Strategy -eq "Vector" })[0]
    $k = @($detailRows | Where-Object { $_.QuestionId -eq $question.Id -and $_.Strategy -eq "Keyword" })[0]
    $h = @($detailRows | Where-Object { $_.QuestionId -eq $question.Id -and $_.Strategy -eq "Hybrid" })[0]
    $reportLines.Add(("| {0} | {1} | {2} | {3} | {4} |" -f $question.Id, $question.Category, $v.ExpectedTokenHit, $k.ExpectedTokenHit, $h.ExpectedTokenHit))
}

$reportLines.Add("")
$reportLines.Add("## How to interpret the results")
$reportLines.Add("")
$reportLines.Add("- Vector-only is useful for semantic paraphrases and questions whose wording differs from the source text. It can still rank exact identifiers below semantically related chunks.")
$reportLines.Add("- Keyword-only is useful for exact error codes, order IDs, model names, version strings, and code identifiers. This implementation uses token extraction plus case-insensitive substring matching; it is not a full Chinese search engine or synonym system.")
$reportLines.Add("- Hybrid combines complementary candidate lists using Reciprocal Rank Fusion (RRF). A chunk present in both lists receives contributions from both ranks, then results are deduplicated by chunkId.")
$reportLines.Add("- RRF scores, vector similarity scores, and keyword matchedTerms counts are different quantities. Do not compare their raw numeric values directly across strategies.")
$reportLines.Add("- ExpectedTokenHit is a simple diagnostic: it checks whether the expected literal appears in any returned Top-K chunk. It is not a complete relevance, precision, or recall evaluation. Manually inspect TopResults before claiming one strategy is better.")
$reportLines.Add("")
$reportLines.Add("## Files")
$reportLines.Add("")
$reportLines.Add("- `docs/day16-hybrid-retrieval-comparison.csv`: one row per question, with Vector/Keyword/Hybrid metrics side by side.")
$reportLines.Add("- `docs/day16-hybrid-retrieval-details.csv`: one row per question and strategy, including Top-K snippets and errors.")
$reportLines.Add("- `docs/day16-hybrid-retrieval-analysis.md`: this report, including measured summary counts.")

[System.IO.File]::WriteAllText(
    $analysisPath,
    ($reportLines -join [Environment]::NewLine),
    [System.Text.UTF8Encoding]::new($true)
)

$script:HttpClient.Dispose()

Write-Host ""
Write-Host "Day 16 comparison completed."
Write-Host ("Summary CSV: {0}" -f $summaryPath)
Write-Host ("Details CSV: {0}" -f $detailsPath)
Write-Host ("Analysis report: {0}" -f $analysisPath)
