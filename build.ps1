param(
    [switch]$Install,
    [string]$ModsPath,
    [string]$BackupPath
)

# Builds the Quick Build add-on, always backs the result up as a NEW .qbN file
# (never overwrites an existing backup) and optionally installs it into mods/.
#
# Usage:
#   .\build.ps1                 # build + backup only
#   .\build.ps1 -Install        # build + backup + replace the jar in ../../mods

$ErrorActionPreference = 'Stop'

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $here
if (-not $ModsPath)   { $ModsPath   = Join-Path $root 'mods' }
if (-not $BackupPath) { $BackupPath = Join-Path $root 'mods_backup' }

$jdk = Join-Path $env:USERPROFILE '.gradle\jdks\eclipse_adoptium-21-amd64-windows.2'
if (Test-Path $jdk) { $env:JAVA_HOME = $jdk }
if (-not $env:JAVA_HOME) { throw 'JAVA_HOME is not set (JDK 21 required)' }

Push-Location $here
try {
    & .\gradlew.bat build --console=plain
    if ($LASTEXITCODE -ne 0) { throw "gradle build failed ($LASTEXITCODE)" }
} finally {
    Pop-Location
}

$jar = Get-ChildItem (Join-Path $here 'build\libs') -Filter 'splinequickbuild-*.jar' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { throw 'no jar produced' }

New-Item -ItemType Directory -Force $BackupPath | Out-Null

$next = 1
Get-ChildItem $BackupPath -Filter 'splinequickbuild-*.jar.qb*' -ErrorAction SilentlyContinue | ForEach-Object {
    if ($_.Name -match '\.qb(\d+)$') {
        $n = [int]$Matches[1]
        if ($n -ge $next) { $next = $n + 1 }
    }
}

$backup = Join-Path $BackupPath ('{0}.qb{1}' -f $jar.Name, $next)
Copy-Item $jar.FullName $backup
$hash = (Get-FileHash $backup -Algorithm SHA256).Hash

Write-Host ''
Write-Host ('jar     : ' + $jar.FullName)
Write-Host ('backup  : ' + $backup)
Write-Host ('sha256  : ' + $hash)
Write-Host ('build   : qb' + $next)

if ($Install) {
    Get-ChildItem $ModsPath -Filter 'splinequickbuild*.jar' -ErrorAction SilentlyContinue |
        ForEach-Object { Remove-Item $_.FullName -Force }
    $installed = Join-Path $ModsPath $jar.Name
    Copy-Item $backup $installed -Force
    Write-Host ('installed: ' + $installed)
}
