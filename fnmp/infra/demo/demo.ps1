param(
    [string]$BaseUrl = "http://localhost:8080",
    [switch]$RunBurstTest,
    [int]$BurstCount = 20
)

$ErrorActionPreference = "Stop"
$started = Get-Date

function Write-Step($title) {
    Write-Host "`n========== $title ==========" -ForegroundColor Cyan
}

function Write-Ok($msg) {
    Write-Host "  [OK] $msg" -ForegroundColor Green
}

function Write-Fail($msg) {
    Write-Host "  [FAIL] $msg" -ForegroundColor Red
    $script:hasErrors = $true
}

function Write-Info($msg) {
    Write-Host "  [..] $msg" -ForegroundColor Yellow
}

function Invoke-Api($method, $path, $body, $expected) {
    $url = "$BaseUrl$path"
    $params = @{ Method = $method; Uri = $url; ContentType = "application/json" }
    if ($body) { $params.Body = ($body | ConvertTo-Json -Compress) }

    try {
        $t = Measure-Command { $resp = Invoke-RestMethod @params -SkipCertificateCheck -ErrorAction Stop }
        $code = 200
        $ok = $code -eq $expected -or -not $expected
        $label = if ($ok) { "OK" } else { "FAIL" }
        Write-Host "    $($method) $path -> $code ($($t.TotalMilliseconds.ToString('F0'))ms)" -ForegroundColor $(if ($ok) { "Green" } else { "Red" })
        return $resp, $t
    }
    catch {
        $code = $_.Exception.Response.StatusCode.value__
        $ok = $code -eq $expected
        Write-Host "    $($method) $path -> $code ($($_.Exception.Message.Split("`n")[0]))" -ForegroundColor $(if ($ok) { "Green" } else { "Red" })
        if (-not $ok) { $script:hasErrors = $true }
        return $null, $null
    }
}

$hasErrors = $false

Write-Step "1. Health Check"
Write-Info "Checking if the app is alive..."
try {
    $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -SkipCertificateCheck -ErrorAction Stop
    Write-Ok "Status: $($health.status)"
}
catch {
    Write-Fail "App not reachable at $BaseUrl — start the app first with: docker compose up -d"
    exit 1
}

Write-Step "2. Create Article"
$article = @{
    headline             = "FNMP Demo: Market Surges on Tech Rally"
    summary              = "Technology stocks drove a broad market rally in today's trading session."
    body                 = "Major indices climbed as technology shares posted strong gains. The Nasdaq Composite led the charge, rising 2.3% amid optimism around AI-driven earnings growth. Analysts pointed to robust demand for cloud infrastructure and enterprise software as key catalysts. The S&P 500 added 1.1%, while the Dow Jones Industrial Average gained 0.4%."
    source               = "DemoScript"
    category             = "MARKET_MOVEMENTS"
    publicationTimestamp = (Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ")
    tags                 = @("tech", "markets", "rally")
}
$created, $createTime = Invoke-Api -Method Post -Path "/api/v1/articles" -Body $article -expected 201
if ($created) {
    $articleId = $created.id
    Write-Ok "Article created with ID: $articleId"
}
else {
    Write-Fail "Article creation failed"
    $articleId = $null
}

Write-Step "3. Get Article by ID"
if ($articleId) {
    $_, $getTime = Invoke-Api -Method Get -Path "/api/v1/articles/$articleId" -expected 200
}
else {
    Write-Info "Skipped (no article ID)"
}

Write-Step "4. List Articles (first 5, paged)"
$listResult, $listTime = Invoke-Api -Method Get -Path "/api/v1/articles?size=5&sort=id.publicationTimestamp,desc" -expected 200
if ($listResult) {
    $total = $listResult.totalElements
    $pageCount = $listResult.totalPages
    Write-Ok "$total article(s) found across $pageCount page(s)"
}

Write-Step "5. Search Articles"
$searchResult, $searchTime = Invoke-Api -Method Get -Path "/api/v1/articles/search?q=tech+rally&limit=5" -expected 200
if ($searchResult -and $searchResult.Count -gt 0) {
    Write-Ok "Search returned $($searchResult.Count) result(s)"
}
elseif ($searchResult) {
    Write-Info "Search returned 0 results (OpenSearch may not be connected; PG fallback used)"
}

Write-Step "6. Error Handling Demo (RFC 7807)"
# Invalid UUID — should return 400 with RFC 7807 body
Invoke-Api -Method Get -Path "/api/v1/articles/not-a-valid-uuid" -expected 400
# Duplicate article (same headline/source/time)
Invoke-Api -Method Post -Path "/api/v1/articles" -Body $article -expected 409

Write-Step "7. Delete Article"
if ($articleId) {
    $_, $deleteTime = Invoke-Api -Method Delete -Path "/api/v1/articles/$articleId" -expected 204
    Write-Ok "Article deleted"
}
else {
    Write-Info "Skipped (no article ID)"
}

if ($RunBurstTest) {
    Write-Step "8. Burst Test ($BurstCount concurrent creates)"
    Write-Info "Sending $BurstCount articles in parallel to demonstrate Kafka buffering..."
    $burstStart = Get-Date
    $jobs = 1..$BurstCount | ForEach-Object {
        $i = $_
        Start-Job -ScriptBlock {
            param($url, $i)
            $body = @{
                headline             = "Burst test article #$i — $(Get-Date -Format 'HH:mm:ss.fff')"
                summary              = "Part of burst test"
                body                 = "Burst test article body."
                source               = "BurstTest"
                category             = "MARKET_MOVEMENTS"
                publicationTimestamp = (Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ")
                tags                 = @("burst", "test")
            } | ConvertTo-Json -Compress
            try {
                $r = Invoke-WebRequest -Uri "$url/api/v1/articles" -Method Post -Body $body -ContentType "application/json" -SkipCertificateCheck -UseBasicParsing
                return @{ Index = $i; Status = [int]$r.StatusCode; Time = (Get-Date) }
            }
            catch {
                $code = $_.Exception.Response.StatusCode.value__
                return @{ Index = $i; Status = $code; Time = (Get-Date) }
            }
        } -ArgumentList $BaseUrl, $i
    }
    $results = $jobs | Wait-Job | Receive-Job
    $jobs | Remove-Job
    $burstElapsed = (Get-Date) - $burstStart
    $success = ($results | Where-Object { $_.Status -eq 201 }).Count
    $failed = ($results | Where-Object { $_.Status -ne 201 }).Count
    $avgMs = [Math]::Round($burstElapsed.TotalMilliseconds / $BurstCount, 0)
    Write-Host "    Burst complete: $success created, $failed failed in $($burstElapsed.TotalSeconds.ToString('F1'))s ($avgMs ms/req)" -ForegroundColor $(if ($failed -eq 0) { "Green" } else { "Yellow" })
    if ($failed -gt 0) {
        $failures = $results | Where-Object { $_.Status -ne 201 }
        Write-Info "  Failures: $($failures.Count) requests with status codes: $($failures.Status -join ', ')"
    }
}

$total = (Get-Date) - $started
Write-Step "DONE"
Write-Host "  Total time: $($total.TotalSeconds.ToString('F1'))s" -ForegroundColor Cyan
if (-not $hasErrors) {
    Write-Host "  All checks passed!" -ForegroundColor Green
}
else {
    Write-Host "  Some checks failed (see above)" -ForegroundColor Yellow
}
Write-Host "  Elapsed: $($total.TotalMilliseconds.ToString('F0')) ms"
