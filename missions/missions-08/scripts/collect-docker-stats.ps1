param(
    [int]$DurationSeconds = 150,
    [int]$IntervalSeconds = 3,
    [string]$OutputFile = "missions/missions-08/results/docker-stats.csv",
    [string]$SummaryFile = ""
)

$containers = @(
    "shortener-nginx-mission-08"
    "shortener-app1-mission-08"
    "shortener-app2-mission-08"
    "shortener-app3-mission-08"
    "shortener-redis-mission-08"
    "shortener-db-mission-08"
)

$outputDirectory = Split-Path -Parent $OutputFile
if (-not [string]::IsNullOrWhiteSpace($outputDirectory) -and -not (Test-Path $outputDirectory)) {
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
}

if ([string]::IsNullOrWhiteSpace($SummaryFile)) {
    $SummaryFile = [System.IO.Path]::ChangeExtension($OutputFile, ".summary.txt")
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

$rows = Import-Csv -Path $OutputFile
$summaryLines = New-Object System.Collections.Generic.List[string]
$summaryLines.Add("container,cpu_avg_percent,cpu_max_percent,mem_max_percent,samples")

foreach ($group in ($rows | Group-Object container)) {
    $cpuValues = @($group.Group | ForEach-Object { [double](($_.cpu_percent -replace "%", "").Trim()) })
    $memValues = @($group.Group | ForEach-Object { [double](($_.mem_percent -replace "%", "").Trim()) })

    $cpuAvg = [Math]::Round(($cpuValues | Measure-Object -Average).Average, 2)
    $cpuMax = [Math]::Round(($cpuValues | Measure-Object -Maximum).Maximum, 2)
    $memMax = [Math]::Round(($memValues | Measure-Object -Maximum).Maximum, 2)

    $summaryLines.Add(("{0},{1},{2},{3},{4}" -f $group.Name, $cpuAvg, $cpuMax, $memMax, $group.Count))
}

$summaryLines | Set-Content -Path $SummaryFile -Encoding UTF8
Write-Host "Docker stats summary saved to $SummaryFile"
Write-Host ""
$summaryLines | ForEach-Object { Write-Host $_ }
