$ErrorActionPreference = "Stop"

# Default port from the uploaded application.yml is 8080.
# If your local SERVER_PORT is different, change this value.
$BaseUrl = "http://localhost:8080"
$TopK = 3

$OutputPath = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "..\docs\day15-retrieval-comparison.csv")
)
$OutputDirectory = Split-Path $OutputPath -Parent
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function ConvertFrom-Base64Utf8 {
    param([string]$Value)
    return [System.Text.Encoding]::UTF8.GetString(
        [System.Convert]::FromBase64String($Value)
    )
}

function Invoke-JsonPost {
    param(
        [string]$Path,
        [string]$Query,
        [int]$Limit
    )

    $json = @{
        query = $Query
        topK = $Limit
    } | ConvertTo-Json -Depth 10 -Compress

    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    $uri = $BaseUrl + $Path

    try {
        $response = Invoke-WebRequest `
            -Uri $uri `
            -Method Post `
            -ContentType "application/json; charset=utf-8" `
            -Body $bodyBytes `
            -UseBasicParsing

        $raw = [string]$response.Content
        $parsed = $null
        try {
            $parsed = $raw | ConvertFrom-Json -ErrorAction Stop
        }
        catch {
            $parsed = $null
        }

        return [pscustomobject]@{
            StatusCode = [int]$response.StatusCode
            Body = $parsed
            Raw = $raw
        }
    }
    catch {
        $requestError = $_
        $statusCode = 0
        $raw = $requestError.Exception.Message
        $parsed = $null

        try {
            $httpResponse = $requestError.Exception.Response
            if ($null -ne $httpResponse) {
                $statusCode = [int]$httpResponse.StatusCode
                $stream = $httpResponse.GetResponseStream()
                if ($null -ne $stream) {
                    $reader = [System.IO.StreamReader]::new(
                        $stream,
                        [System.Text.Encoding]::UTF8
                    )
                    $raw = $reader.ReadToEnd()
                    $reader.Dispose()
                    try {
                        $parsed = $raw | ConvertFrom-Json -ErrorAction Stop
                    }
                    catch {
                        $parsed = $null
                    }
                }
            }
        }
        catch {
            # Keep the original request error if no HTTP response is available.
        }

        return [pscustomobject]@{
            StatusCode = $statusCode
            Body = $parsed
            Raw = $raw
        }
    }
}

function Get-ResultItems {
    param($Call)

    if ($null -eq $Call -or $null -eq $Call.Body) {
        return @()
    }

    $dataProperty = $Call.Body.PSObject.Properties["data"]
    if ($null -eq $dataProperty -or $null -eq $dataProperty.Value) {
        return @()
    }

    return @($dataProperty.Value)
}

function Get-ItemText {
    param($Item)

    if ($null -eq $Item) {
        return ""
    }

    foreach ($propertyName in @("content", "text")) {
        $property = $Item.PSObject.Properties[$propertyName]
        if ($null -ne $property -and $null -ne $property.Value) {
            return [string]$property.Value
        }
    }

    $chunkProperty = $Item.PSObject.Properties["chunk"]
    if ($null -ne $chunkProperty -and $null -ne $chunkProperty.Value) {
        $chunk = $chunkProperty.Value
        if ($chunk -is [string]) {
            return $chunk
        }
        foreach ($propertyName in @("content", "text")) {
            $property = $chunk.PSObject.Properties[$propertyName]
            if ($null -ne $property -and $null -ne $property.Value) {
                return [string]$property.Value
            }
        }
    }

    return ""
}

function Get-ItemPropertyValue {
    param(
        $Item,
        [string]$PropertyName
    )

    if ($null -eq $Item) {
        return $null
    }

    $property = $Item.PSObject.Properties[$PropertyName]
    if ($null -ne $property) {
        return $property.Value
    }

    $chunkProperty = $Item.PSObject.Properties["chunk"]
    if ($null -ne $chunkProperty -and $null -ne $chunkProperty.Value) {
        $property = $chunkProperty.Value.PSObject.Properties[$PropertyName]
        if ($null -ne $property) {
            return $property.Value
        }
    }

    return $null
}

function Format-TopResults {
    param(
        [object[]]$Items,
        [string]$ScoreField
    )

    $parts = @()
    foreach ($item in $Items) {
        $content = (Get-ItemText -Item $item) -replace '\s+', ' '
        $content = $content.Trim()
        if ($content.Length -gt 140) {
            $content = $content.Substring(0, 140) + "..."
        }

        $score = Get-ItemPropertyValue -Item $item -PropertyName $ScoreField
        if ($null -eq $score) {
            $score = Get-ItemPropertyValue -Item $item -PropertyName "score"
        }

        $chunkIndex = Get-ItemPropertyValue -Item $item -PropertyName "chunkIndex"
        $parts += ("chunk={0}; score={1}; {2}" -f $chunkIndex, $score, $content)
    }

    return ($parts -join " || ")
}

# Chinese queries are Base64-encoded so this .ps1 source contains ASCII only.
$Cases = @(
    [pscustomobject]@{
        QuestionId = "Q01"
        Query = "ERR-1007"
        ExpectedKeyword = "ERR-1007"
        Scenario = "Error code exact match"
    },
    [pscustomobject]@{
        QuestionId = "Q02"
        Query = "BGE-M3"
        ExpectedKeyword = "BGE-M3"
        Scenario = "Product model match"
    },
    [pscustomobject]@{
        QuestionId = "Q03"
        Query = "v2.1.0"
        ExpectedKeyword = "v2.1.0"
        Scenario = "Version match"
    },
    [pscustomobject]@{
        QuestionId = "Q04"
        Query = "ORD_2026_0001"
        ExpectedKeyword = "ORD_2026_0001"
        Scenario = "Order number exact match"
    },
    [pscustomobject]@{
        QuestionId = "Q05"
        Query = (ConvertFrom-Base64Utf8 "5pWw5o2u5bqT6L+e5o6l5aSx6LSl")
        ExpectedKeyword = (ConvertFrom-Base64Utf8 "5pWw5o2u5bqT6L+e5o6l5aSx6LSl")
        Scenario = "Chinese phrase match"
    },
    [pscustomobject]@{
        QuestionId = "Q06"
        Query = (ConvertFrom-Base64Utf8 "5aaC5L2V6Kej5YazIEVSUi0xMDA3IOmUmeivrw==")
        ExpectedKeyword = "ERR-1007"
        Scenario = "Chinese and error code mixed query"
    },
    [pscustomobject]@{
        QuestionId = "Q07"
        Query = "BAAI/bge-m3"
        ExpectedKeyword = "BAAI/bge-m3"
        Scenario = "Model name with slash and hyphen"
    },
    [pscustomobject]@{
        QuestionId = "Q08"
        Query = "pgvector"
        ExpectedKeyword = "pgvector"
        Scenario = "English technical term"
    },
    [pscustomobject]@{
        QuestionId = "Q09"
        Query = "chunkIndex"
        ExpectedKeyword = "chunkIndex"
        Scenario = "Camel case technical term"
    },
    [pscustomobject]@{
        QuestionId = "Q10"
        Query = (ConvertFrom-Base64Utf8 "6K6i5Y2VIE9SRF8yMDI2XzAwMDEg54q25oCB")
        ExpectedKeyword = "ORD_2026_0001"
        Scenario = "Chinese and order number mixed query"
    }
)

$Rows = @()

foreach ($case in $Cases) {
    Write-Host ("Testing {0}: {1}" -f $case.QuestionId, $case.Query)

    $vectorCall = Invoke-JsonPost `
        -Path "/api/v1/retrieval/search" `
        -Query $case.Query `
        -Limit $TopK

    $keywordCall = Invoke-JsonPost `
        -Path "/api/v1/retrieval/keyword-search" `
        -Query $case.Query `
        -Limit $TopK

    $vectorResults = @(Get-ResultItems -Call $vectorCall)
    $keywordResults = @(Get-ResultItems -Call $keywordCall)

    $vectorTop1 = $null
    if ($vectorResults.Count -gt 0) {
        $vectorTop1 = $vectorResults[0]
    }

    $keywordTop1 = $null
    if ($keywordResults.Count -gt 0) {
        $keywordTop1 = $keywordResults[0]
    }

    $vectorSuccess = $false
    if ($null -ne $vectorCall.Body -and $null -ne $vectorCall.Body.success) {
        $vectorSuccess = [bool]$vectorCall.Body.success
    }

    $keywordSuccess = $false
    if ($null -ne $keywordCall.Body -and $null -ne $keywordCall.Body.success) {
        $keywordSuccess = [bool]$keywordCall.Body.success
    }

    $keywordMatchedExpected = $false
    foreach ($item in $keywordResults) {
        $content = Get-ItemText -Item $item
        if ($content.IndexOf(
                [string]$case.ExpectedKeyword,
                [System.StringComparison]::OrdinalIgnoreCase
            ) -ge 0) {
            $keywordMatchedExpected = $true
            break
        }
    }

    $vectorError = ""
    if ($null -ne $vectorCall.Body -and $vectorCall.Body.errorMessage) {
        $vectorError = [string]$vectorCall.Body.errorMessage
    }
    elseif ($vectorCall.StatusCode -ge 400 -or $vectorCall.StatusCode -eq 0) {
        $vectorError = [string]$vectorCall.Raw
    }

    $keywordError = ""
    if ($null -ne $keywordCall.Body -and $keywordCall.Body.errorMessage) {
        $keywordError = [string]$keywordCall.Body.errorMessage
    }
    elseif ($keywordCall.StatusCode -ge 400 -or $keywordCall.StatusCode -eq 0) {
        $keywordError = [string]$keywordCall.Raw
    }

    $vectorTop1DocumentId = Get-ItemPropertyValue -Item $vectorTop1 -PropertyName "documentId"
    $vectorTop1ChunkIndex = Get-ItemPropertyValue -Item $vectorTop1 -PropertyName "chunkIndex"
    $vectorTop1Similarity = Get-ItemPropertyValue -Item $vectorTop1 -PropertyName "similarity"
    $vectorTopResults = Format-TopResults -Items $vectorResults -ScoreField "similarity"

    $keywordTop1DocumentId = Get-ItemPropertyValue -Item $keywordTop1 -PropertyName "documentId"
    $keywordTop1ChunkIndex = Get-ItemPropertyValue -Item $keywordTop1 -PropertyName "chunkIndex"
    $keywordTop1MatchedTerms = Get-ItemPropertyValue -Item $keywordTop1 -PropertyName "matchedTerms"
    $keywordTopResults = Format-TopResults -Items $keywordResults -ScoreField "matchedTerms"

    $Rows += [pscustomobject]@{
        QuestionId = $case.QuestionId
        Query = $case.Query
        Scenario = $case.Scenario
        ExpectedKeyword = $case.ExpectedKeyword
        VectorHttpStatus = $vectorCall.StatusCode
        VectorApiSuccess = $vectorSuccess
        VectorResultCount = $vectorResults.Count
        VectorTop1DocumentId = $vectorTop1DocumentId
        VectorTop1ChunkIndex = $vectorTop1ChunkIndex
        VectorTop1Similarity = $vectorTop1Similarity
        VectorTopResults = $vectorTopResults
        VectorErrorMessage = $vectorError
        KeywordHttpStatus = $keywordCall.StatusCode
        KeywordApiSuccess = $keywordSuccess
        KeywordResultCount = $keywordResults.Count
        KeywordTop1DocumentId = $keywordTop1DocumentId
        KeywordTop1ChunkIndex = $keywordTop1ChunkIndex
        KeywordTop1MatchedTerms = $keywordTop1MatchedTerms
        KeywordTopResults = $keywordTopResults
        KeywordMatchedExpected = $keywordMatchedExpected
        KeywordErrorMessage = $keywordError
    }
}

$Rows | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding UTF8

Write-Host ""
Write-Host "Day 15 retrieval comparison finished."
Write-Host ("CSV file: {0}" -f $OutputPath)
Write-Host ""
$Rows |
    Select-Object QuestionId, Query, VectorHttpStatus, VectorResultCount, KeywordHttpStatus, KeywordResultCount, KeywordMatchedExpected |
    Format-Table -AutoSize
