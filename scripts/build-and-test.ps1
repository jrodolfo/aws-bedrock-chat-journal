$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradleTasks = if ($env:GRADLE_TASKS) { $env:GRADLE_TASKS } else { "test build" }
$requiredJavaMajor = 25

function Fail-Script {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    [Console]::Error.WriteLine($Message)
    exit 1
}

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

function Get-JavaVersionLine {
    $javaVersionOutput = & cmd /c "java -version 2>&1"
    $javaVersionLine = $javaVersionOutput | Where-Object { $_ -match 'version "' } | Select-Object -First 1
    if (-not $javaVersionLine) {
        $renderedOutput = $javaVersionOutput -join [Environment]::NewLine
        throw "Unable to determine the installed Java version from:$([Environment]::NewLine)$renderedOutput"
    }

    return $javaVersionLine
}

function Get-JavaMajorVersion {
    $javaVersionLine = Get-JavaVersionLine
    $match = [regex]::Match($javaVersionLine, 'version "([0-9]+)')
    if (-not $match.Success) {
        throw "Unable to parse the installed Java version from: $javaVersionLine"
    }

    return [int]$match.Groups[1].Value
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Fail-Script @"
Java $requiredJavaMajor is required, but 'java' is not on PATH.

Install Java $requiredJavaMajor and set JAVA_HOME accordingly.
"@
}

$javaCommand = Get-Command java -ErrorAction SilentlyContinue
$javaVersionLine = Get-JavaVersionLine
$javaMajor = Get-JavaMajorVersion
if ($javaMajor -ne $requiredJavaMajor) {
    Fail-Script @"
Java $requiredJavaMajor is required, but found Java $javaMajor.
Detected:
  $javaVersionLine
Path:
  $($javaCommand.Source)

Set JAVA_HOME to a Java $requiredJavaMajor installation and put it first on PATH.
"@
}

Push-Location $repoRoot
try {
    & "$repoRoot\gradlew.bat" $gradleTasks.Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries)
} finally {
    Pop-Location
}
