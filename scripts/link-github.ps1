#Requires -Version 5.1
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$ghUser = git config --global user.name
if (-not $ghUser) { $ghUser = 'FracturedZen' }
# noreply emails look like 140035389+FracturedZen@users.noreply.github.com
$email = git config --global user.email
if ($email -match '\+([^@]+)@users\.noreply\.github\.com') {
    $ghUser = $Matches[1]
}

$repoName = 'mc-chat-mojo'
$remote = "https://github.com/$ghUser/$repoName.git"

if (-not (Test-Path (Join-Path $Root '.git'))) {
    git init -b main
    Write-Host "Initialized git repo on branch main"
}

$existing = git remote get-url origin 2>$null
if ($LASTEXITCODE -ne 0 -or -not $existing) {
    git remote add origin $remote
    Write-Host "Added origin $remote"
} else {
    Write-Host "origin already $existing"
}

Write-Host ""
Write-Host "GitHub CLI is not required for a local remote, but creating the empty repo on GitHub is."
Write-Host "1. Open https://github.com/new  (logged in as $ghUser)"
Write-Host "2. Name: $repoName   Public or private. Do not add a README (local already has files)."
Write-Host "3. Then:"
Write-Host "   git add -A"
Write-Host "   git commit -m `"MC Chat Mojo overlay`""
Write-Host "   git push -u origin main"
Write-Host ""
Write-Host "After the first push, Actions -> Build debug APK produces a sideload APK artifact."
Write-Host "This machine has no Android SDK, so APKs are built on GitHub, not here."
