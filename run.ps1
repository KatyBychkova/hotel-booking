param([switch]$SmokeTest)
$ErrorActionPreference = 'Stop'
$modules = @('discovery-server', 'hotel-service', 'booking-service', 'api-gateway')
$ports = @(8761, 8081, 8082, 8080)
$children = @()
$logDir = Join-Path $PSScriptRoot 'target/run'
New-Item -ItemType Directory -Path $logDir -Force | Out-Null

function New-RandomSecret([int]$length) {
    $bytes = New-Object byte[] $length
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($bytes)
}

foreach ($module in $modules) {
    $jar = Join-Path $PSScriptRoot "$module/target/$module-1.0.0.jar"
    if (!(Test-Path -LiteralPath $jar)) { throw "Build the project first: mvn clean verify" }
}
$listeners = [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
foreach ($port in $ports) {
    if ($listeners.Port -contains $port) { throw "Port $port is already in use." }
}

if (!$env:JWT_SECRET) { $env:JWT_SECRET = New-RandomSecret 32 }
if (!$env:ADMIN_PASSWORD) {
    $env:ADMIN_PASSWORD = New-RandomSecret 12
    Write-Host "Demo admin password for this run: $env:ADMIN_PASSWORD"
}

try {
    foreach ($module in $modules) {
        $jar = Join-Path $PSScriptRoot "$module/target/$module-1.0.0.jar"
        $proc = Start-Process -FilePath 'java' -ArgumentList @('-Xmx256m', '-jar', ('"' + $jar + '"')) `
            -WindowStyle Hidden -PassThru -RedirectStandardOutput "$logDir/$module.log" `
            -RedirectStandardError "$logDir/$module.err.log"
        $children += $proc
    }
    Write-Host "Starting services. Logs: $logDir"
    Write-Host "Gateway: http://localhost:8080   Eureka: http://localhost:8761"
    if ($SmokeTest) {
        & (Join-Path $PSScriptRoot 'scripts/smoke-test.ps1')
    } else {
        Write-Host "Keep this terminal open. Press Ctrl+C to stop all four services."
    }
    while (!$SmokeTest) {
        for ($i = 0; $i -lt $children.Count; $i++) {
            if ($children[$i].HasExited) { throw "$($modules[$i]) stopped. Check its log in $logDir." }
        }
        Start-Sleep -Seconds 1
    }
} finally {
    foreach ($proc in $children) {
        if (!$proc.HasExited) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue }
    }
}
