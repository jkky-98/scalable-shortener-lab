param(
    [int]$DurationSeconds = 120,
    [int]$IntervalSeconds = 1,
    [string]$OutputFile = "missions/missions-05/results/docker-stats.csv"
)

$containers = @(
    "shortener-nginx-mission-05"
    "shortener-app1-mission-05"
    "shortener-app2-mission-05"
    "shortener-app3-mission-05"
    "shortener-db-mission-05"
)

$outputDirectory = Split-Path -Parent $OutputFile
if (-not [string]::IsNullOrWhiteSpace($outputDirectory) -and -not (Test-Path $outputDirectory)) {
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
}

"timestamp,container,cpu_percent,mem_usage,mem_percent,net_io,block_io,pids" |
    Set-Content -Path $OutputFile -Encoding UTF8

$endAt = (Get-Date).AddSeconds($DurationSeconds)

while ((Get-Date) -lt $endAt) {
    $timestamp = Get-Date -Format "yyyy-MM-ddTHH:mm:ssK"
    $format = "$timestamp,{{.Name}},{{.CPUPerc}},{{.MemUsage}},{{.MemPerc}},{{.NetIO}},{{.BlockIO}},{{.PIDs}}"

    & docker stats --no-stream --format $format @containers |
        ForEach-Object { Add-Content -Path $OutputFile -Value $_ -Encoding UTF8 }

    Start-Sleep -Seconds $IntervalSeconds
}

Write-Host "Docker stats saved to $OutputFile"
