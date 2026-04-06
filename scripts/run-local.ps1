$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$port = if ($env:PORT) { $env:PORT } else { "8080" }
$requiredJavaMajor = 25

function Get-JavaInstallHints {
    @"
Set JAVA_HOME to a Java $requiredJavaMajor installation and put it first on PATH.

On macOS:
  export JAVA_HOME=`"$$(/usr/libexec/java_home -v $requiredJavaMajor)`"
  export PATH=`"`$JAVA_HOME/bin:`$PATH`"

On Windows PowerShell:
  `$env:JAVA_HOME="C:\Program Files\Java\jdk-$requiredJavaMajor"
  `$env:Path="`$env:JAVA_HOME\bin;`$env:Path"

On Windows Git Bash:
  export JAVA_HOME="/c/Program Files/Java/jdk-$requiredJavaMajor"
  export PATH="`$JAVA_HOME/bin:`$PATH"

On Amazon Linux 2023:
  sudo dnf install -y java-$requiredJavaMajor-amazon-corretto-devel
  export JAVA_HOME=/usr/lib/jvm/java-$requiredJavaMajor-amazon-corretto.x86_64
  export PATH="`$JAVA_HOME/bin:`$PATH"
"@
}

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
$(Get-JavaInstallHints)
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

$(Get-JavaInstallHints)
"@
}

Push-Location $repoRoot
try {
    & "$repoRoot\gradlew.bat" "bootRun" "--args=--server.port=$port"
    $exitCode = $LASTEXITCODE
    if ($exitCode -eq 143) {
        Write-Output "Spring Boot stopped cleanly after receiving SIGTERM."
        exit 0
    }

    exit $exitCode
} finally {
    Pop-Location
}
