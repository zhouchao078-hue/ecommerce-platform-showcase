param(
    [ValidateSet("start", "stop", "status")]
    [string]$Action = "start"
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$runtimeDirectory = Join-Path $PSScriptRoot ".runtime"
$pidFile = Join-Path $runtimeDirectory "services.json"
$java = if ($env:JAVA_HOME) {
    Join-Path $env:JAVA_HOME "bin\java.exe"
} else {
    "java"
}

$services = @(
    @{
        Name = "commerce-service"
        WorkDir = Join-Path $projectRoot "platform-app"
        Jar = "target/platform-app-1.0.0-SNAPSHOT.jar"
        Port = 8081
    },
    @{
        Name = "agent-service"
        WorkDir = Join-Path $projectRoot "platform-services/platform-agent-service"
        Jar = "target/platform-agent-service-1.0.0-SNAPSHOT.jar"
        Port = 8082
    },
    @{
        Name = "gateway"
        WorkDir = Join-Path $projectRoot "gateway"
        Jar = "target/gateway-1.0.0-SNAPSHOT.jar"
        Port = 8080
    }
)

function Get-RecordedProcesses {
    if (-not (Test-Path -LiteralPath $pidFile)) {
        return
    }
    $records = Get-Content -Raw -LiteralPath $pidFile | ConvertFrom-Json
    foreach ($record in $records) {
        Write-Output $record
    }
}

function Stop-RecordedProcesses {
    foreach ($record in @(Get-RecordedProcesses)) {
        $process = Get-Process -Id $record.Pid -ErrorAction SilentlyContinue
        if ($process) {
            Stop-Process -Id $record.Pid -Force
            Write-Host "Stopped $($record.Name) (PID $($record.Pid))"
        }
    }
    if (Test-Path -LiteralPath $pidFile) {
        Remove-Item -LiteralPath $pidFile -Force
    }
}

function Show-Status {
    foreach ($record in @(Get-RecordedProcesses)) {
        $process = Get-Process -Id $record.Pid -ErrorAction SilentlyContinue
        $state = if ($process) { "running" } else { "stopped" }
        Write-Host "$($record.Name): $state (PID $($record.Pid), port $($record.Port))"
    }
}

if ($Action -eq "stop") {
    Stop-RecordedProcesses
    exit 0
}

if ($Action -eq "status") {
    Show-Status
    exit 0
}

Stop-RecordedProcesses
New-Item -ItemType Directory -Path $runtimeDirectory -Force | Out-Null

$records = @()
foreach ($service in $services) {
    $jarPath = Join-Path $service.WorkDir $service.Jar
    if (-not (Test-Path -LiteralPath $jarPath)) {
        throw "Missing executable jar: $jarPath. Run mvn clean package first."
    }

    $stdout = Join-Path $runtimeDirectory "$($service.Name).out.log"
    $stderr = Join-Path $runtimeDirectory "$($service.Name).err.log"
    $process = Start-Process `
        -FilePath $java `
        -ArgumentList "-jar", $service.Jar `
        -WorkingDirectory $service.WorkDir `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -PassThru

    $records += @{
        Name = $service.Name
        Pid = $process.Id
        Port = $service.Port
    }
    Write-Host "Started $($service.Name) (PID $($process.Id), port $($service.Port))"
}

$records | ConvertTo-Json | Set-Content -LiteralPath $pidFile -Encoding UTF8
Write-Host "Runtime state: $pidFile"
