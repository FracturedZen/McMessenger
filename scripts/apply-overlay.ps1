#Requires -Version 5.1
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$Mojo = Join-Path $Root 'mojo-src'
if (-not (Test-Path $Mojo)) {
    Write-Error 'mojo-src missing. Run .\scripts\clone-mojo.ps1 first.'
}

$javaSrc = Join-Path $Root 'overlay\java\net\kdt\pojavlaunch\chatoverlay'
$javaDst = Join-Path $Mojo 'app_pojavlauncher\src\main\java\net\kdt\pojavlaunch\chatoverlay'
$resSrc = Join-Path $Root 'overlay\res\layout'
$resDst = Join-Path $Mojo 'app_pojavlauncher\src\main\res\layout'
$agentJar = Join-Path $Root 'overlay\prebuilt\chat-only-agent.jar'
$assetsDst = Join-Path $Mojo 'app_pojavlauncher\src\main\assets'

New-Item -ItemType Directory -Force -Path $javaDst | Out-Null
New-Item -ItemType Directory -Force -Path $resDst | Out-Null
Copy-Item -Force (Join-Path $javaSrc '*.java') $javaDst
Copy-Item -Force (Join-Path $resSrc '*.xml') $resDst
Write-Host 'Copied overlay Java + layouts'

if (Test-Path $agentJar) {
    New-Item -ItemType Directory -Force -Path $assetsDst | Out-Null
    Copy-Item -Force $agentJar (Join-Path $assetsDst 'chat-only-agent.jar')
    Write-Host 'Copied chat-only-agent.jar into assets'
} else {
    Write-Host 'WARNING: overlay\prebuilt\chat-only-agent.jar missing. Run .\scripts\build-agent.ps1'
}

function Patch-Once {
    param(
        [string]$Path,
        [string]$Marker,
        [string]$Needle,
        [string]$Insert
    )
    $text = Get-Content -Path $Path -Raw
    if ($text.Contains($Marker)) {
        Write-Host "Already patched: $Marker"
        return
    }
    if (-not $text.Contains($Needle)) {
        Write-Error "Could not find patch point in ${Path}: $Needle"
    }
    $text = $text.Replace($Needle, $Insert)
    Set-Content -Path $Path -Value $text -NoNewline
    Write-Host "Patched $Marker"
}

$ga = Join-Path $Mojo 'app_pojavlauncher\src\main\java\net\kdt\pojavlaunch\game\GameActivity.java'
$gr = Join-Path $Mojo 'app_pojavlauncher\src\main\java\net\kdt\pojavlaunch\utils\jre\GameRunner.java'

Patch-Once -Path $ga -Marker 'import net.kdt.pojavlaunch.chatoverlay.ChatOverlayController;' `
    -Needle 'import net.kdt.pojavlaunch.game.platform.Platform;' `
    -Insert @"
import net.kdt.pojavlaunch.game.platform.Platform;
import net.kdt.pojavlaunch.chatoverlay.ChatOverlayController;
import net.kdt.pojavlaunch.chatoverlay.ChatOnlyOptions;
"@

# If overlay import exists without ChatOnlyOptions, add it.
$gaText = Get-Content -Path $ga -Raw
if ($gaText.Contains('import net.kdt.pojavlaunch.chatoverlay.ChatOverlayController;') -and -not $gaText.Contains('import net.kdt.pojavlaunch.chatoverlay.ChatOnlyOptions;')) {
    $gaText = $gaText.Replace(
        'import net.kdt.pojavlaunch.chatoverlay.ChatOverlayController;',
        "import net.kdt.pojavlaunch.chatoverlay.ChatOverlayController;`nimport net.kdt.pojavlaunch.chatoverlay.ChatOnlyOptions;"
    )
    Set-Content -Path $ga -Value $gaText -NoNewline
    Write-Host 'Added ChatOnlyOptions import'
}

Patch-Once -Path $ga -Marker 'MC_CHAT_OVERLAY' `
    -Needle '        bindValues();' `
    -Insert @"
        bindValues();
        // MC_CHAT_OVERLAY
        ChatOverlayController.install(this, instance.getGameDirectory());
"@

Patch-Once -Path $ga -Marker 'MC_CHAT_ONLY_OPTS' `
    -Needle '        Logger.appendToLog("--------- Starting game with Launcher Debug!");' `
    -Insert @"
        // MC_CHAT_ONLY_OPTS
        ChatOnlyOptions.apply(instance.getGameDirectory());
        Logger.appendToLog("--------- Starting game with Launcher Debug!");
"@

Patch-Once -Path $gr -Marker 'MC_CHAT_ONLY_OPTS' `
    -Needle '        GameOptionsUtils.fixOptions(isLtw);' `
    -Insert @"
        GameOptionsUtils.fixOptions(isLtw);
        // MC_CHAT_ONLY_OPTS
        net.kdt.pojavlaunch.chatoverlay.ChatOnlyOptions.apply(gamedir);
"@

Patch-Once -Path $gr -Marker 'MC_CHAT_ONLY_AGENT' `
    -Needle '        addAuthlibInjectorArgs(javaArgList, account);' `
    -Insert @"
        addAuthlibInjectorArgs(javaArgList, account);
        // MC_CHAT_ONLY_AGENT
        if (requiredJavaVersion >= 17) {
            net.kdt.pojavlaunch.chatoverlay.ChatOnlyAgentSupport.appendJavaAgent(activity, javaArgList);
        }
"@

Write-Host ''
Write-Host 'Next: .\scripts\build.ps1'
Write-Host 'Optional: change applicationId so this APK does not replace Play Store Mojo. See docs\FORK.md'
