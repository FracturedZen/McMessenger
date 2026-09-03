#Requires -Version 5.1
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

function Resolve-Javac {
    $candidates = @(
        (Join-Path ${env:JAVA_HOME} 'bin\javac.exe'),
        'C:\Program Files\Java\jdk-21\bin\javac.exe',
        'C:\Program Files\Java\jdk-17\bin\javac.exe',
        'C:\Program Files\Eclipse Adoptium\jdk-17.0.12-hotspot\bin\javac.exe'
    )
    foreach ($c in $candidates) {
        if ($c -and (Test-Path $c)) { return $c }
    }
    $cmd = Get-Command javac -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

$javac = Resolve-Javac
if (-not $javac) {
    Write-Error "javac not found. Install JDK 17+ (full JDK). The agent must be desktop JVM bytecode (it runs inside Minecraft's JRE, not Android ART)."
}
Write-Host "javac: $javac"

$work = Join-Path $Root 'overlay\agent\build'
$lib = Join-Path $Root 'overlay\agent\lib'
$outJar = Join-Path $Root 'overlay\prebuilt\chat-only-agent.jar'
if (Test-Path $work) { Remove-Item -Recurse -Force $work }
New-Item -ItemType Directory -Force -Path $work, $lib, (Split-Path $outJar) | Out-Null

$asmJar = Join-Path $lib 'asm-9.7.jar'
if (-not (Test-Path $asmJar)) {
    Write-Host "Downloading ASM 9.7 (shaded into the agent)..."
    Invoke-WebRequest -UseBasicParsing -Uri 'https://repo1.maven.org/maven2/org/ow2/asm/asm/9.7/asm-9.7.jar' -OutFile $asmJar
}

$srcRoot = Join-Path $Root 'overlay\agent'
Write-Host "Compiling chat-only javaagent..."
& $javac --release 17 -cp $asmJar -d $work `
    (Join-Path $srcRoot 'com\phonkalphabet\mcchat\agent\PlayDropper.java') `
    (Join-Path $srcRoot 'com\phonkalphabet\mcchat\agent\FireChannelReadTransformer.java') `
    (Join-Path $srcRoot 'com\phonkalphabet\mcchat\agent\AgentMain.java')
if ($LASTEXITCODE -ne 0) { Write-Error "javac failed" }

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$asmUnpack = Join-Path $work 'asm-unpack'
New-Item -ItemType Directory -Force -Path $asmUnpack | Out-Null
[System.IO.Compression.ZipFile]::ExtractToDirectory($asmJar, $asmUnpack)
Remove-Item -Recurse -Force (Join-Path $asmUnpack 'META-INF') -ErrorAction SilentlyContinue
Copy-Item -Recurse -Force (Join-Path $asmUnpack '*') $work

if (Test-Path $outJar) { Remove-Item -Force $outJar }
$zip = [System.IO.Compression.ZipFile]::Open($outJar, 'Create')
try {
    $manifest = Join-Path $srcRoot 'MANIFEST.MF'
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $zip, $manifest, 'META-INF/MANIFEST.MF',
        [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    Get-ChildItem $work -Recurse -File -Filter *.class | Where-Object {
        $_.FullName -notmatch 'asm-unpack'
    } | ForEach-Object {
        $rel = $_.FullName.Substring($work.Length).TrimStart('\').Replace('\', '/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $zip, $_.FullName, $rel,
            [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally {
    $zip.Dispose()
}

Write-Host "Wrote $outJar"
Write-Host "Next: .\scripts\apply-overlay.ps1"
