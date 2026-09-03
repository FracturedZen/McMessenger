#Requires -Version 5.1
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$dest = Join-Path $Root 'mojo-src'
$git = Get-Command git -ErrorAction SilentlyContinue
if (-not $git) {
    Write-Error "git is not on PATH. Install Git for Windows, then re-run."
}

if (Test-Path $dest) {
    Write-Host "mojo-src already exists. Pulling v3_openjdk..."
    Set-Location $dest
    git fetch --depth 1 origin v3_openjdk
    git checkout v3_openjdk
    git submodule update --init --recursive --depth 1
    if ($LASTEXITCODE -ne 0) { git submodule update --init --recursive }
    Set-Location $Root
} else {
    Write-Host "Shallow-cloning MojoLauncher (v3_openjdk) with submodules..."
    Write-Host "This is large. Wait."
    git clone --depth 1 --branch v3_openjdk --single-branch https://github.com/MojoLauncher/MojoLauncher.git $dest
    Set-Location $dest
    git submodule update --init --recursive --depth 1
    if ($LASTEXITCODE -ne 0) { git submodule update --init --recursive }
    Set-Location $Root
}

Write-Host ""
Write-Host "Next: .\scripts\apply-overlay.ps1"
