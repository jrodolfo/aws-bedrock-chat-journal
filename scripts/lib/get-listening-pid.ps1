param(
    [Parameter(Mandatory = $true)]
    [int]$Port
)

$lines = netstat -ano -p tcp
$pattern = "(^|\s)TCP\s+\S+:" + $Port + "\s+\S+\s+LISTENING\s+(\d+)\s*$"
$match = $lines | Select-String -Pattern $pattern | Select-Object -First 1

if ($match) {
    Write-Output $match.Matches[0].Groups[2].Value
}
