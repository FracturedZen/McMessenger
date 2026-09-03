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

# Home-screen name, recents title, crash copy: every locale still says MJLauncher/Pojav.
$resRoot = Join-RepoPath $Mojo 'app_pojavlauncher/src/main/res'
Get-ChildItem $resRoot -Directory | Where-Object { $_.Name -like 'values*' } | ForEach-Object {
    $sf = Join-Path $_.FullName 'strings.xml'
    if (-not (Test-Path $sf)) { return }
    $st = Get-Content -Path $sf -Raw
    $st2 = $st
    $named = @{
        'app_short_name' = 'McMessenger'
        'mcl_tab_wiki' = 'Credits'
        'mcl_button_social_media' = 'GitHub'
        'lazy_service_default_title' = 'McMessenger'
        'modpack_install_notification_title' = 'McMessenger'
        'notif_channel_name' = 'McMessenger'
        'error_fatal' = 'McMessenger has unexpectedly crashed'
        'storage_required' = 'McMessenger requires external storage to be attached. Please reconnect it and restart the app.'
        'notification_permission_dialog_text' = 'McMessenger needs notification permission so game downloads can continue when you leave the app.'
        'main_play' = 'Connect'
    }
    foreach ($name in $named.Keys) {
        $st2 = [regex]::Replace($st2, "<string name=`"$name`"[^>]*>[^<]*</string>", "<string name=`"$name`">$($named[$name])</string>")
    }
    $st2 = [regex]::Replace($st2, '<string name="social_media_invite"[^>]*>[^<]*</string>', '<string name="social_media_invite" translatable="false">https://github.com/FracturedZen/McMessenger</string>')
    $st2 = $st2.Replace('MJLauncher', 'McMessenger').Replace('MojoLauncher', 'McMessenger').Replace('PojavLauncher', 'McMessenger')
    $st2 = $st2.Replace('https://mojolauncher.ru', 'https://github.com/FracturedZen/McMessenger')
    $st2 = $st2.Replace('https://t.me/MojoLauncher', 'https://github.com/FracturedZen/McMessenger')
    $st2 = [regex]::Replace($st2, '(>[^<]*?)\bMJ\b', '${1}McMessenger')
    $st2 = [regex]::Replace($st2, '(>[^<]*?)\bMojo\b', '${1}McMessenger')
    $st2 = [regex]::Replace($st2, '(>[^<]*?)\bPojav\b', '${1}McMessenger')
    if ($st2 -ne $st) {
        Set-Content -Path $sf -Value $st2 -NoNewline
    }
}
Write-Host 'Renamed launcher to McMessenger in all locales'

# Own package so this APK is not Mojo/Pojav and does not replace their Play Store app.
$appGradle = Join-RepoPath $Mojo 'app_pojavlauncher/build.gradle'
if (Test-Path $appGradle) {
    $bg = Get-Content -Path $appGradle -Raw
    $bg2 = $bg.Replace('applicationId "git.artdeell.mjlaunch"', 'applicationId "com.fracturedzen.mcmessenger"')
    $bg2 = $bg2.Replace("'git.artdeell.mjlaunch.debug'", "'com.fracturedzen.mcmessenger.debug'")
    $bg2 = $bg2.Replace("'git.artdeell.mjlaunch.scoped.gamefolder.debug'", "'com.fracturedzen.mcmessenger.scoped.gamefolder.debug'")
    $bg2 = $bg2.Replace("'git.artdeell.mjlaunch.scoped.gamefolder'", "'com.fracturedzen.mcmessenger.scoped.gamefolder'")
    $bg2 = $bg2.Replace("'git.artdeell.mjlaunch'", "'com.fracturedzen.mcmessenger'")
    $bg2 = $bg2.Replace("resValue 'string', 'group_id', 'git.artdeell'", "resValue 'string', 'group_id', 'com.fracturedzen'")
    if ($bg2 -ne $bg) {
        Set-Content -Path $appGradle -Value $bg2 -NoNewline
        Write-Host 'applicationId is com.fracturedzen.mcmessenger (standalone, not Mojo)'
    }
}

# Gradle's default file is app_pojavlauncher-full-debug.apk (module name). Rename the output.
$appGradle = Join-RepoPath $Mojo 'app_pojavlauncher/build.gradle'
if ((Test-Path $appGradle) -and -not ((Get-Content -Path $appGradle -Raw).Contains('MC_APK_NAME'))) {
    $bg = (Get-Content -Path $appGradle -Raw).Replace("`r`n", "`n")
    $needle = "    buildFeatures {`n        buildConfig true`n    }`n}"
    $insert = @'
    buildFeatures {
        buildConfig true
    }
}

// MC_APK_NAME
android.applicationVariants.configureEach { variant ->
    variant.outputs.configureEach { output ->
        output.outputFileName = "McMessenger-${variant.buildType.name}.apk"
    }
}
'@
    $insert = $insert.Replace("`r`n", "`n")
    if ($bg.Contains($needle)) {
        Set-Content -Path $appGradle -Value $bg.Replace($needle, $insert) -NoNewline
        Write-Host 'APK output file is McMessenger-debug.apk'
    } else {
        Write-Host 'WARNING: could not set APK outputFileName (CI still copies McMessenger-debug.apk)'
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

$melonPng = Join-RepoPath $Root 'overlay/res/drawable/mcmessenger_melon_block.png'
if (Test-Path $melonPng) {
    foreach ($dens in @('mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi')) {
        $mip = Join-RepoPath $Mojo "app_pojavlauncher/src/main/res/mipmap-$dens"
        if (-not (Test-Path $mip)) { continue }
        foreach ($leaf in @('ic_launcher.webp', 'ic_launcher_round.webp', 'ic_launcher_foreground.webp')) {
            $old = Join-Path $mip $leaf
            if (Test-Path $old) { Remove-Item -Force $old }
        }
        Copy-Item -Force $melonPng (Join-Path $mip 'ic_launcher.png')
        Copy-Item -Force $melonPng (Join-Path $mip 'ic_launcher_round.png')
        Copy-Item -Force $melonPng (Join-Path $mip 'ic_launcher_foreground.png')
    }
    Write-Host 'Replaced density launcher icons with melon block'
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

Patch-Once -Path $ga -Marker 'MC_CHAT_IME_FULLSCREEN' `
    -Needle 'public class GameActivity extends BaseActivity implements ControlButtonMenuListener, EditorExitable, ServiceConnection {' `
    -Insert @"
public class GameActivity extends BaseActivity implements ControlButtonMenuListener, EditorExitable, ServiceConnection {
    @Override
    public boolean setFullscreen() {
        // MC_CHAT_IME_FULLSCREEN
        return false;
    }
"@

Patch-Once -Path $ga -Marker 'MC_CHAT_IME' `
    -Needle '        if(androidCompat)
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);' `
    -Insert @"
        // MC_CHAT_IME
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
"@

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

Patch-Once -Path $gr -Marker 'MC_SERVER_ARGS' `
    -Needle '        List<String> launchArgs = getMoJsonClientArgs(account, versionInfo, gamedir);' `
    -Insert @"
        List<String> launchArgs = getMoJsonClientArgs(account, versionInfo, gamedir);
        // MC_SERVER_ARGS
        net.kdt.pojavlaunch.chatoverlay.ChatServerLaunch.appendClientArgs(activity, launchArgs, versionId);
"@

$toolsJava = Join-RepoPath $Mojo 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/Tools.java'
Patch-Once -Path $toolsJava -Marker 'MC_APP_NAME' `
    -Needle '    public static String APP_NAME = "PojavLauncher";' `
    -Insert @"
    // MC_APP_NAME
    public static String APP_NAME = "McMessenger";
"@
Patch-Once -Path $toolsJava -Marker 'MC_GAME_HOME' `
    -Needle '    public static String DIR_GAME_HOME = Environment.getExternalStorageDirectory().getAbsolutePath() + "/games/PojavLauncher";' `
    -Insert @"
    // MC_GAME_HOME
    public static String DIR_GAME_HOME = Environment.getExternalStorageDirectory().getAbsolutePath() + "/games/McMessenger";
"@
Patch-Once -Path $toolsJava -Marker 'MC_STORAGE_ROOT' `
    -Needle '        File launcherRoot = new File(externalStorageDirectory,"games/PojavLauncher");' `
    -Insert @"
        // MC_STORAGE_ROOT
        File launcherRoot = new File(externalStorageDirectory,"games/McMessenger");
"@

Patch-Once -Path $ga -Marker 'MC_BACK_TO_MENU' `
    -Needle '        boolean handleEvent;' `
    -Insert @"
        // MC_BACK_TO_MENU
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && (touchCharInput == null || !touchCharInput.isEnabled())) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (!net.kdt.pojavlaunch.chatoverlay.ChatOverlayController.onSystemBack()) {
                    try {
                        Tools.restartLauncherActivity(this);
                        Tools.fullyExit();
                    } catch (Throwable ignored) {
                        finish();
                    }
                }
            }
            return true;
        }
        boolean handleEvent;
"@

Patch-Once -Path $ga -Marker 'MC_WINDOW_TITLE' `
    -Needle '            setTitle("MojoLauncher (" + version + ")");' `
    -Insert @"
            // MC_WINDOW_TITLE
            setTitle("McMessenger (" + version + ")");
"@

Patch-Once -Path $toolsJava -Marker 'MC_URL_HOME' `
    -Needle '    public static final String URL_HOME = "https://mojolauncher.ru";' `
    -Insert @"
    // MC_URL_HOME
    public static final String URL_HOME = "https://github.com/FracturedZen/McMessenger";
"@

$menuJava = Join-RepoPath $Mojo 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java'
Patch-Once -Path $menuJava -Marker 'MC_CREDITS' `
    -Needle '        mNewsButton.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));' `
    -Insert @"
        // MC_CREDITS
        mNewsButton.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(requireActivity())
            .setTitle(R.string.mcmessenger_credits_title)
            .setMessage(R.string.mcmessenger_credits_body)
            .setPositiveButton(android.R.string.ok, null)
            .show());
"@

$menuFrag = Join-RepoPath $Mojo 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java'
Patch-Once -Path $menuFrag -Marker 'MC_SERVER_BAR' `
    -Needle '        mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));' `
    -Insert @"
        mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
        // MC_SERVER_BAR
        try {
            net.kdt.pojavlaunch.chatoverlay.ChatServerBar.install(view, mPlayButton);
        } catch (Throwable ignored) {}
"@

$fragPortrait = Join-RepoPath $Mojo 'app_pojavlauncher/src/main/res/layout/fragment_launcher.xml'
if ((Test-Path $fragPortrait) -and -not ((Get-Content -Path $fragPortrait -Raw).Contains('view_server_bar'))) {
    $fp = (Get-Content -Path $fragPortrait -Raw).Replace("`r`n", "`n")
    $fp = $fp.Replace(
        "		app:layout_constraintBottom_toTopOf=`"@id/play_button`"`n		app:layout_constraintEnd_toStartOf=`"@+id/edit_profile_button`"",
        "		app:layout_constraintBottom_toTopOf=`"@id/mc_server_bar`"`n		app:layout_constraintEnd_toStartOf=`"@+id/edit_profile_button`""
    )
    $fp = $fp.Replace(
        "	<com.kdt.mcgui.MineButton`n		android:id=`"@+id/play_button`"",
        @"
	<include
		layout="@layout/view_server_bar"
		android:layout_width="0dp"
		android:layout_height="wrap_content"
		app:layout_constraintBottom_toTopOf="@id/play_button"
		app:layout_constraintEnd_toEndOf="parent"
		app:layout_constraintStart_toStartOf="parent" />

	<com.kdt.mcgui.MineButton
		android:id="@+id/play_button"
"@.Replace("`r`n", "`n")
    )
    Set-Content -Path $fragPortrait -Value $fp -NoNewline
    Write-Host 'Added server bar to portrait launcher'
}

$fragLand = Join-RepoPath $Mojo 'app_pojavlauncher/src/main/res/layout-land/fragment_launcher.xml'
if ((Test-Path $fragLand) -and -not ((Get-Content -Path $fragLand -Raw).Contains('view_server_bar'))) {
    $fl = Get-Content -Path $fragLand -Raw
    $needleLand = "    <com.kdt.mcgui.mcVersionSpinner"
    $insertLand = @"
    <include
        layout="@layout/view_server_bar"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        app:layout_constraintBottom_toTopOf="@id/mc_version_spinner"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent" />

    <com.kdt.mcgui.mcVersionSpinner
"@
    if ($fl.Contains($needleLand)) {
        Set-Content -Path $fragLand -Value $fl.Replace($needleLand, $insertLand) -NoNewline
        Write-Host 'Added server bar to landscape launcher'
    }
}

$manifest = Join-RepoPath $Mojo 'app_pojavlauncher/src/main/AndroidManifest.xml'
if (Test-Path $manifest) {
    $mf = Get-Content -Path $manifest -Raw
    if ($mf -notmatch 'game\.GameActivity[\s\S]{0,400}fullUser') {
        $mf2 = [regex]::Replace(
            $mf,
            '(android:name="net\.kdt\.pojavlaunch\.game\.GameActivity"[\s\S]*?)android:screenOrientation="sensorLandscape"',
            '${1}android:windowSoftInputMode="adjustResize"' + "`n            " + 'android:screenOrientation="fullUser"'
        )
        if ($mf2 -ne $mf) {
            Set-Content -Path $manifest -Value $mf2 -NoNewline
            Write-Host 'GameActivity allows portrait; keyboard adjustResize'
        }
    }
}

$iconJava = Join-RepoPath $Mojo 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/instances/InstanceIconProvider.java'
Patch-Once -Path $iconJava -Marker 'MC_DEFAULT_ICON' `
    -Needle '        sStaticIcons.put("default", R.drawable.ic_mojo_full);' `
    -Insert @"
        // MC_DEFAULT_ICON
        sStaticIcons.put("default", R.drawable.mcmessenger_melon_block);
"@

Write-Host ''
Write-Host 'Next: .\scripts\build.ps1'
Write-Host 'This APK is McMessenger (com.fracturedzen.mcmessenger), not Pojav/Mojo. Engine is still their launcher code.'
