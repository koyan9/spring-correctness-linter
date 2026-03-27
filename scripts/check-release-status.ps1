param(
    [string]$Version,
    [string]$Tag,
    [string]$Repository = "koyan9/spring-correctness-linter"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

function Resolve-ReleaseIdentity {
    param(
        [string]$RequestedVersion,
        [string]$RequestedTag
    )

    $resolvedTag = $RequestedTag
    $resolvedVersion = $RequestedVersion

    if ([string]::IsNullOrWhiteSpace($resolvedVersion) -and -not [string]::IsNullOrWhiteSpace($resolvedTag)) {
        $resolvedVersion = if ($resolvedTag.StartsWith("v")) { $resolvedTag.Substring(1) } else { $resolvedTag }
    }

    if ([string]::IsNullOrWhiteSpace($resolvedTag) -and -not [string]::IsNullOrWhiteSpace($resolvedVersion)) {
        $resolvedTag = "v$resolvedVersion"
    }

    if ([string]::IsNullOrWhiteSpace($resolvedVersion)) {
        [xml]$pom = Get-Content (Join-Path $repoRoot "pom.xml")
        $resolvedVersion = $pom.project.version
    }

    if ([string]::IsNullOrWhiteSpace($resolvedTag)) {
        $resolvedTag = "v$resolvedVersion"
    }

    return [pscustomobject]@{
        Version = $resolvedVersion
        Tag = $resolvedTag
    }
}

function Invoke-JsonRequest {
    param([string]$Url)
    return Invoke-RestMethod -Uri $Url -Headers @{ "User-Agent" = "spring-correctness-linter-release-check" }
}

function Invoke-HeadStatus {
    param([string]$Url)

    $curlCommand = Get-Command "curl.exe" -ErrorAction SilentlyContinue
    if ($null -ne $curlCommand) {
        $nullDevice = if ($env:OS -eq "Windows_NT") { "NUL" } else { "/dev/null" }
        $statusOutput = & $curlCommand.Source -I -s -o $nullDevice -w "%{http_code}" $Url
        $statusCode = $null
        if (-not [string]::IsNullOrWhiteSpace($statusOutput) -and $statusOutput -match "^\d{3}$") {
            $statusCode = [int]$statusOutput
        }
        return [pscustomobject]@{
            Url = $Url
            StatusCode = $statusCode
            Success = ($statusCode -ge 200 -and $statusCode -lt 400)
            Error = if ($statusCode -eq $null) { "curl did not return an HTTP status code" } else { $null }
        }
    }

    try {
        $response = Invoke-WebRequest -Uri $Url -Method Head -Headers @{ "User-Agent" = "spring-correctness-linter-release-check" }
        return [pscustomobject]@{
            Url = $Url
            StatusCode = [int]$response.StatusCode
            Success = $true
            Error = $null
        }
    } catch {
        $statusCode = $null
        $responseProperty = $_.Exception.PSObject.Properties["Response"]
        if ($null -ne $responseProperty -and $null -ne $responseProperty.Value) {
            try {
                $statusCode = [int]$responseProperty.Value.StatusCode
            } catch {
                $statusCode = $null
            }
        }
        return [pscustomobject]@{
            Url = $Url
            StatusCode = $statusCode
            Success = $false
            Error = $_.Exception.Message
        }
    }
}

function Resolve-TagCommit {
    param([string]$ReleaseTag)

    try {
        $commit = (& git rev-list -n 1 $ReleaseTag 2>$null)
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($commit)) {
            return $null
        }
        return $commit.Trim()
    } catch {
        return $null
    }
}

function Select-RelatedWorkflowRuns {
    param(
        [object[]]$WorkflowRuns,
        [string]$ReleaseTag,
        [string]$ReleaseCommit
    )

    return $WorkflowRuns | Where-Object {
        $_.head_branch -eq $ReleaseTag -or
        ($null -ne $ReleaseCommit -and $_.head_sha -eq $ReleaseCommit) -or
        $_.display_title -like "*$ReleaseTag*"
    } | Select-Object -First 10
}

function Write-ReleaseStatusArtifacts {
    param(
        [string]$ReleaseVersion,
        [pscustomobject]$Payload
    )

    $outputDirectory = Join-Path $repoRoot "target/release-status"
    New-Item -ItemType Directory -Force $outputDirectory | Out-Null

    $jsonPath = Join-Path $outputDirectory "release-status-$ReleaseVersion.json"
    $markdownPath = Join-Path $outputDirectory "release-status-$ReleaseVersion.md"

    $Payload | ConvertTo-Json -Depth 8 | Set-Content -Path $jsonPath

    $markdown = New-Object System.Text.StringBuilder
    [void]$markdown.AppendLine("# Release Status $ReleaseVersion")
    [void]$markdown.AppendLine("")
    [void]$markdown.AppendLine("## GitHub Release")
    if ($null -ne $Payload.GitHubRelease) {
        [void]$markdown.AppendLine(('- Tag: `{0}`' -f $Payload.GitHubRelease.tag_name))
        [void]$markdown.AppendLine(('- Published: `{0}`' -f $Payload.GitHubRelease.published_at))
        [void]$markdown.AppendLine(('- URL: {0}' -f $Payload.GitHubRelease.html_url))
    } else {
        [void]$markdown.AppendLine("- Not found")
    }
    [void]$markdown.AppendLine("")
    [void]$markdown.AppendLine("## Workflow Runs")
    if ($Payload.WorkflowRuns.Count -eq 0) {
        [void]$markdown.AppendLine("- No matching workflow runs found")
    } else {
        foreach ($run in $Payload.WorkflowRuns) {
            [void]$markdown.AppendLine(('- {0}: status=`{1}`, conclusion=`{2}`, url={3}' -f $run.name, $run.status, $run.conclusion, $run.html_url))
        }
    }
    [void]$markdown.AppendLine("")
    [void]$markdown.AppendLine("## Maven Central Direct URLs")
    foreach ($artifact in $Payload.MavenCentral.DirectArtifacts) {
        [void]$markdown.AppendLine(('- {0}: status=`{1}`, success=`{2}`, url={3}' -f $artifact.Artifact, $artifact.StatusCode, $artifact.Success, $artifact.Url))
    }
    [void]$markdown.AppendLine("")
    [void]$markdown.AppendLine("## Maven Central Search")
    [void]$markdown.AppendLine(('- numFound=`{0}`' -f $Payload.MavenCentral.Search.numFound))

    Set-Content -Path $markdownPath -Value $markdown.ToString()

    Write-Host ""
    Write-Host "Wrote release-status artifacts:"
    Write-Host "  $jsonPath"
    Write-Host "  $markdownPath"
}

$identity = Resolve-ReleaseIdentity -RequestedVersion $Version -RequestedTag $Tag
$tagCommit = Resolve-TagCommit -ReleaseTag $identity.Tag

Write-Host "==> Checking release status for $($identity.Tag)"

$release = $null
try {
    $release = Invoke-JsonRequest "https://api.github.com/repos/$Repository/releases/tags/$($identity.Tag)"
} catch {
    Write-Warning "GitHub release lookup failed: $($_.Exception.Message)"
}

$workflowRuns = @()
try {
    $actionsResponse = Invoke-JsonRequest "https://api.github.com/repos/$Repository/actions/runs?per_page=30"
    $workflowRuns = @(Select-RelatedWorkflowRuns -WorkflowRuns $actionsResponse.workflow_runs -ReleaseTag $identity.Tag -ReleaseCommit $tagCommit)
} catch {
    Write-Warning "GitHub Actions lookup failed: $($_.Exception.Message)"
}

$directArtifacts = @(
    @{
        Artifact = "spring-correctness-linter-core"
        Url = "https://repo1.maven.org/maven2/io/github/koyan9/spring-correctness-linter-core/$($identity.Version)/spring-correctness-linter-core-$($identity.Version).pom"
    },
    @{
        Artifact = "spring-correctness-linter-maven-plugin"
        Url = "https://repo1.maven.org/maven2/io/github/koyan9/spring-correctness-linter-maven-plugin/$($identity.Version)/spring-correctness-linter-maven-plugin-$($identity.Version).pom"
    }
) | ForEach-Object {
    $status = Invoke-HeadStatus -Url $_.Url
    [pscustomobject]@{
        Artifact = $_.Artifact
        Url = $status.Url
        StatusCode = $status.StatusCode
        Success = $status.Success
        Error = $status.Error
    }
}

$searchResponse = $null
try {
    $searchUrl = "https://search.maven.org/solrsearch/select?q=g:%22io.github.koyan9%22+AND+a:%22spring-correctness-linter-maven-plugin%22+AND+v:%22{0}%22&rows=20&wt=json" -f $identity.Version
    $searchResponse = Invoke-JsonRequest $searchUrl
} catch {
    Write-Warning "Maven Central search lookup failed: $($_.Exception.Message)"
}

$payload = [pscustomobject]@{
    Version = $identity.Version
    Tag = $identity.Tag
    TagCommit = $tagCommit
    GitHubRelease = if ($null -ne $release) {
        [pscustomobject]@{
            tag_name = $release.tag_name
            name = $release.name
            draft = $release.draft
            prerelease = $release.prerelease
            published_at = $release.published_at
            html_url = $release.html_url
        }
    } else {
        $null
    }
    WorkflowRuns = @($workflowRuns | ForEach-Object {
        [pscustomobject]@{
            name = $_.name
            display_title = $_.display_title
            event = $_.event
            status = $_.status
            conclusion = $_.conclusion
            head_branch = $_.head_branch
            head_sha = $_.head_sha
            html_url = $_.html_url
            created_at = $_.created_at
        }
    })
    MavenCentral = [pscustomobject]@{
        DirectArtifacts = $directArtifacts
        Search = [pscustomobject]@{
            numFound = if ($null -ne $searchResponse) { $searchResponse.response.numFound } else { $null }
            docs = if ($null -ne $searchResponse) { @($searchResponse.response.docs) } else { @() }
        }
    }
}

$payload
Write-Host ""
$directArtifacts | Format-Table Artifact, StatusCode, Success -AutoSize

Write-ReleaseStatusArtifacts -ReleaseVersion $identity.Version -Payload $payload
