# Fork notes

Upstream: https://github.com/MojoLauncher/MojoLauncher (`v3_openjdk`)
License: GNU LGPLv3 (see upstream `LICENSE`)

## Why a fork instead of "use Mojo from Capacitor"

Mojo is the JVM + renderer. Capacitor cannot speak Minecraft TCP. Opening the Play Store Mojo app also cannot feed this overlay its login. The only legitimate way to put a chat GUI on the real client is to **change the launcher** that owns the game process.

## What we patch

`scripts/apply-overlay.ps1` copies files into a local clone (`mojo-src/`) and inserts one call in `GameActivity.initLayout`:

```java
ChatOverlayController.install(this, instance.getGameDirectory());
```

New files (ours):

- `net.kdt.pojavlaunch.chatoverlay.*`
- `res/layout/view_chat_overlay.xml`
- `res/layout/item_chat_line.xml`

We do not replace Mojo's authenticator. Microsoft device-code / local accounts stay theirs.

## Standalone app (not Pojav / not Mojo)

`scripts/apply-overlay.ps1` rebrands the fork:

- Launcher name **McMessenger** (all locales, window title, `Tools.APP_NAME`)
- `applicationId` **`com.fracturedzen.mcmessenger`** (debug: `.debug`)

It uses Mojo's engine internally. It does **not** require Pojav or Play Store Mojo, and it does **not** replace them.

## Re-applying after `git pull`

```powershell
cd mojo-src
git pull
cd ..
.\scripts\apply-overlay.ps1
.\scripts\build.ps1
```

The install marker `// MC_CHAT_OVERLAY` is idempotent. Overlay Java/XML are overwritten from `overlay/`.

## Chat-only load cut (not a silent world cheat)

The overlay does not just paint over the world. Before the JVM starts we rewrite `options.txt`:

- `renderDistance:2` / `simulationDistance:2` — the client **tells the server** its view-distance, so vanilla ships a 5×5 chunk window instead of a 25×25. That is the same Video Settings slider, not a hidden packet.
- Audio categories 0, cheap graphics, max 10 FPS, no clouds/particles/shadows.

When Cover is on, the GL backbuffer is shrunk to 16×16 so we are not shading a full-screen frame nobody sees.

After a 45s login grace, a **javaagent** (`mcmessenger-agent.jar`) drops inbound Netty **ByteBuf** frames larger than 4 KiB. Chunk and light payloads are large; chat, keepalive, and teleport confirms are small and still flow. We do **not** skip keepalives, teleport confirms, or movement acks. We do **not** hide you from the tab list.

The agent is JVM bytecode shipped as an asset and passed as `-javaagent` to Minecraft’s JRE. It is not a Fabric/Forge mod.

## Auto-respawn

The overlay has **Auto-respawn: on/off** (saved) and a one-shot **Respawn** button.

On death (chat/log: “You died”, “was slain…”, etc. for your username):

1. Enter/Space — vanilla death screen focuses Respawn.
2. The agent sends play `client_command` / PERFORM_RESPAWN (action 0) using a small version→packet-id table. Wrong ids are skipped; Enter still runs.

This is the same packet the Respawn button sends. It is not a ghost/killaura hook.

Build it with JDK 17:

```powershell
.\scripts\build-agent.ps1
.\scripts\apply-overlay.ps1
```

## Version coverage

Mojo already launches ~rd-132211 through current snapshots. McMessenger rides that:

- Chat log: 1.6 `[CHAT]` through 1.19+ `[System] [CHAT]` / `[Not Secure]`.
- Chat length: 100 characters before 1.11, 256 from 1.11.
- `options.txt` uses both old and new keys; unknown keys are ignored.
- Respawn packet ids are tabulated for 1.7.10–1.21.x. Unknown / snapshot versions **do not send a guessed id** (that desyncs). Enter/Space on the death screen still runs.
- Frame drop is 16 KiB after a 60s login grace so 1.19 signed chat is not discarded.
- The javaagent is only attached when the instance needs Java 17+ (1.18+). Older instances still get overlay, options, and Enter-respawn.

## Limits

- Incoming chat depends on the client writing chat to `latestlog.txt`. Vanilla does. Some clients mute it.
- Send injects **T**, then characters, then **Enter**. If the player rebound chat off T, change `ChatSender.CHAT_ANDROID_KEYCODE`.
- Cover mode blocks touch to the world. Use **Game** to click menus (title screen, inventory).
- 1.19+ online-mode signed chat still works because we are the real client, not Mineflayer.

## Mineflayer later

PC headless relay remains `C:\Users\Z\Desktop\mc-chat-relay`. Use it when the phone should not run a JVM.
