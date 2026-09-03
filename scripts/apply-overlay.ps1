#Requires -Version 5.1
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot

# Join-Path with backslashes is a literal filename on Linux pwsh.
function Join-RepoPath {
    param([string]$Base, [string]$Rel)
    $p = $Base
    foreach ($s in ($Rel -replace '\\', '/').Split('/')) {
        if ($s) { $p = [System.IO.Path]::Combine($p, $s) }
    }
    return $p
}

$Mojo = Join-RepoPath $Root 'mojo-src'
if (-not (Test-Path $Mojo)) {
    Write-Error 'mojo-src missing. Run .\scripts\clone-mojo.ps1 first.'
}

$javaSrc = Join-RepoPath $Root 'overlay/java/net/kdt/pojavlaunch/chatoverlay'
$javaDst = Join-RepoPath $Mojo 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/chatoverlay'
$resSrc = Join-RepoPath $Root 'overlay/res/layout'
$resDst = Join-RepoPath $Mojo 'app_pojavlauncher/src/main/res/layout'
$agentJar = Join-RepoPath $Root 'overlay/prebuilt/mcmessenger-agent.jar'
$assetsDst = Join-RepoPath $Mojo 'app_pojavlauncher/src/main/assets'

New-Item -ItemType Directory -Force -Path $javaDst | Out-Null
New-Item -ItemType Directory -Force -Path $resDst | Out-Null
Copy-Item -Force (Join-Path $javaSrc '*.java') $javaDst
Copy-Item -Force (Join-Path $resSrc '*.xml') $resDst
Write-Host 'Copied overlay Java + layouts'

$drawSrc = Join-RepoPath $Root 'overlay/res/drawable'
$drawDst = Join-RepoPath $Mojo 'app_pojavlauncher/src/main/res/drawable'
$valSrc = Join-RepoPath $Root 'overlay/res/values'
$valDst = Join-RepoPath $Mojo 'app_pojavlauncher/src/main/res/values'
New-Item -ItemType Directory -Force -Path $drawDst, $valDst | Out-Null
Copy-Item -Force (Join-Path $drawSrc '*') $drawDst
Copy-Item -Force (Join-Path $valSrc '*') $valDst
Write-Host 'Copied melon theme drawables + colors'

function Set-NamedColor([string]$XmlPath, [string]$Name, [string]$Hex) {
    if (-not (Test-Path $XmlPath)) { return }
    $t = Get-Content -Path $XmlPath -Raw
    $t2 = [regex]::Replace($t, "<color name=`"$Name`">#[A-Fa-f0-9]+</color>", "<color name=`"$Name`">$Hex</color>")
    if ($t2 -ne $t) {
        Set-Content -Path $XmlPath -Value $t2 -NoNewline
    }
}

$colors = Join-RepoPath $Mojo 'app_pojavlauncher/src/main/res/values/colors.xml'
Set-NamedColor $colors 'background_app' '#142810'
Set-NamedColor $colors 'background_status_bar' '#1C3A18'
Set-NamedColor $colors 'background_bottom_bar' '#1A3216'
Set-NamedColor $colors 'background_overlay' '#2A4F24'
Set-NamedColor $colors 'minebutton_color' '#E23B4A'
Set-NamedColor $colors 'primary_text' '#F4FFE8'
Set-NamedColor $colors 'secondary_text' '#B5D99A'
Set-NamedColor $colors 'icon_outline_color' '#F4FFE8'
Set-NamedColor $colors 'divider' '#2F6B28'
$iconBg = Join-RepoPath $Mojo 'app_pojavlauncher/src/main/res/values/ic_launcher_background.xml'
Set-NamedColor $iconBg 'ic_launcher_background' '#1A3D14'
Write-Host 'Applied melon palette to launcher colors'

$strings = Join-RepoPath $Mojo 'app_pojavlauncher/src/main/res/values/strings.xml'
if (Test-Path $strings) {
    $st = Get-Content -Path $strings -Raw
    $st2 = [regex]::Replace($st, '<string name="app_short_name">[^<]*</string>', '<string name="app_short_name">McMessenger</string>')
    if ($st2 -ne $st) {
        Set-Content -Path $strings -Value $st2 -NoNewline
        Write-Host 'Renamed launcher to McMessenger'
    }
}

$adaptive = Join-RepoPath $Mojo 'app_pojavlauncher/src/main/res/mipmap-anydpi-v26/ic_launcher.xml'
if (Test-Path $adaptive) {
    $ad = Get-Content -Path $adaptive -Raw
    $ad2 = $ad.Replace('@mipmap/ic_launcher_foreground', '@drawable/mcmessenger_melon_block')
    if ($ad2 -ne $ad) {
        Set-Content -Path $adaptive -Value $ad2 -NoNewline
        Write-Host 'Adaptive icon uses melon block'
    }
}
$adaptiveRound = Join-RepoPath $Mojo 'app_pojavlauncher/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml'
if (Test-Path $adaptiveRound) {
    $ad = Get-Content -Path $adaptiveRound -Raw
    $ad2 = $ad.Replace('@mipmap/ic_launcher_foreground', '@drawable/mcmessenger_melon_block')
    if ($ad2 -ne $ad) { Set-Content -Path $adaptiveRound -Value $ad2 -NoNewline }
}

if (Test-Path $agentJar) {
    New-Item -ItemType Directory -Force -Path $assetsDst | Out-Null
    Copy-Item -Force $agentJar (Join-Path $assetsDst 'mcmessenger-agent.jar')
    Write-Host 'Copied mcmessenger-agent.jar into assets'
} else {
    Write-Host 'WARNING: overlay\prebuilt\mcmessenger-agent.jar missing. Run .\scripts\build-agent.ps1'
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

$ga = Join-RepoPath $Mojo 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/game/GameActivity.java'
$gr = Join-RepoPath $Mojo 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/jre/GameRunner.java'

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
