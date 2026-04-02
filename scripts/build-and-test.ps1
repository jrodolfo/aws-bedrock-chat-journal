$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradleTasks = if ($env:GRADLE_TASKS) { $env:GRADLE_TASKS } else { "test build" }

if ($args.Count -gt 0 -and ($args[0] -eq "-h" -or $args[0] -eq "--help")) {
    Write-Output @"
Usage:
  ./scripts/build-and-test.ps1
  `$env:GRADLE_TASKS="clean test build"; ./scripts/build-and-test.ps1

What it does:
  Runs the configured Gradle verification/build tasks from the repository root.

Optional environment variables:
  GRADLE_TASKS    Gradle tasks to execute
                  Default: test build
"@
    exit 0
}

Push-Location $repoRoot
try {
    & "$repoRoot\gradlew.bat" $gradleTasks.Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries)
} finally {
    Pop-Location
}
