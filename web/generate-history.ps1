param(
  [string]$OutputPath = "web/data.json"
)

$historyPath = ".waylog/history"

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

if (-not (Test-Path $historyPath)) {
  Write-Error "History path not found: $historyPath"
  exit 1
}

$rawCommits = git -c core.quotepath=false log --pretty=format:'%H|%cI|%s'
if ($LASTEXITCODE -ne 0) {
  Write-Error "git log failed"
  exit 1
}

$commits = @()
foreach ($line in $rawCommits) {
  if ([string]::IsNullOrWhiteSpace($line)) {
    continue
  }

  $parts = $line -split '\|', 3
  if ($parts.Count -lt 3) {
    continue
  }

  $hash = $parts[0]
  $date = $parts[1]
  $subject = $parts[2]

  $fileList = git -c core.quotepath=false diff-tree --no-commit-id --name-only -r $hash -- $historyPath
  if ($LASTEXITCODE -ne 0) {
    $fileList = @()
  }

  $files = @()
  foreach ($file in $fileList) {
    if ([string]::IsNullOrWhiteSpace($file)) {
      continue
    }

    $content = git -c core.quotepath=false show "${hash}:$file" 2>$null | Out-String
    if ($LASTEXITCODE -ne 0) {
      $content = ""
    }

    $files += [pscustomobject]@{
      path = $file
      content = $content
    }
  }

  $commits += [pscustomobject]@{
    hash = $hash
    shortHash = $hash.Substring(0, 7)
    date = $date
    subject = $subject
    files = $files
  }
}

$data = [pscustomobject]@{
  generatedAt = (Get-Date).ToString("o")
  commitCount = $commits.Count
  commits = $commits
}

$dir = Split-Path -Parent $OutputPath
if ($dir -and -not (Test-Path $dir)) {
  New-Item -ItemType Directory -Path $dir -Force | Out-Null
}

$json = $data | ConvertTo-Json -Depth 6
[System.IO.File]::WriteAllText($OutputPath, $json, [System.Text.UTF8Encoding]::new($false))
Write-Host "Wrote $OutputPath"
