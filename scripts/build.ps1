#Requires -Version 5.1
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$Mojo = Join-Path $Root 'mojo-src'
if (-not (Test-Path $Mojo)) {
    Write-Error "mojo-src missing. Run clone-mojo.ps1 then apply-overlay.ps1."
}

$overlayJava = Join-Path $Mojo 'app_pojavlauncher\src\main\java\net\kdt\pojavlaunch\chatoverlay\ChatOverlayController.java'
if (-not (Test-Path $overlayJava)) {
    Write-Error "Overlay not applied. Run .\scripts\apply-overlay.ps1"
}

$agent = Join-Path $Root 'overlay\prebuilt\chat-only-agent.jar'
if (-not (Test-Path $agent)) {
    Write-Host "Agent jar missing — building it (needs javac / JDK 17)..."
    & (Join-Path $Root 'scripts\build-agent.ps1')
    & (Join-Path $Root 'scripts\apply-overlay.ps1')
}

Set-Location $Mojo
Write-Host "Building debug APK (needs Android SDK + JDK 17 on PATH / ANDROID_HOME)"
if (Test-Path (Join-Path $Mojo 'gradlew.bat')) {
    .\gradlew.bat :app_pojavlauncher:assembleDebug
} else {
    Write-Error "gradlew.bat missing in mojo-src"
}

$apkDir = Join-Path $Mojo 'app_pojavlauncher\build\outputs\apk'
Write-Host ""
Write-Host "Look for the APK under:"
Write-Host $apkDir
