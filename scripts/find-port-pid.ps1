$ErrorActionPreference = "Stop"

$port = if ($args.Count -gt 0) { $args[0] } else { "8080" }

if ($args.Count -gt 0 -and ($args[0] -eq "-h" -or $args[0] -eq "--help")) {
    Write-Output @"
Usage:
  ./scripts/find-port-pid.ps1
  ./scripts/find-port-pid.ps1 8081

What it does:
  Prints the PID of the process listening on the selected TCP port.

Arguments:
  port            TCP port to inspect
                  Default: 8080
"@
    exit 0
}

if ($port -notmatch '^\d+$') {
    [Console]::Error.WriteLine("Port must be a number: $port")
    exit 1
}

$lines = netstat -ano -p tcp
$pattern = "(^|\s)TCP\s+\S+:" + $port + "\s+\S+\s+LISTENING\s+(\d+)\s*$"
$match = $lines | Select-String -Pattern $pattern | Select-Object -First 1

if (-not $match) {
    [Console]::Error.WriteLine("No listening process found on port $port.")
    exit 1
}

$pid = $match.Matches[0].Groups[2].Value
Write-Output "Listening PID on port ${port}: $pid"
