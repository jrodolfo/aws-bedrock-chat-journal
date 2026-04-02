$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$port = if ($env:PORT) { $env:PORT } else { "8080" }

if ($args.Count -gt 0 -and ($args[0] -eq "-h" -or $args[0] -eq "--help")) {
    Write-Output @"
Usage:
  ./scripts/run-local.ps1
  `$env:PORT=8081; ./scripts/run-local.ps1

What it does:
  Starts the Spring Boot application with Gradle using the selected local port.

Optional environment variables:
  PORT            Server port to use for this run
                  Default: 8080
"@
    exit 0
}

Push-Location $repoRoot
try {
    & "$repoRoot\gradlew.bat" "bootRun" "--args=--server.port=$port"
} finally {
    Pop-Location
}
