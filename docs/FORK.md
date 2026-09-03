# Fork notes

McMessenger's launcher engine started as a fork of [MojoLauncher](https://github.com/MojoLauncher/MojoLauncher) (`v3_openjdk`, GNU LGPLv3), which itself is based on PojavLauncher.

**Credits:** MojoLauncher and PojavLauncher authors. We keep their LICENSE and copyright notices.

The shipped app is **McMessenger** (`com.fracturedzen.mcmessenger`). It does not require Pojav or Play Store Mojo, does not replace them, and does not show their product name, icon, Discord, or website.

## What we patch

`scripts/apply-overlay.ps1` copies files into a local clone (`mojo-src/`) and rebrands plus inserts:

```java
ChatOverlayController.install(this, instance.getGameDirectory());
```

New files (ours):

- `net.kdt.pojavlaunch.chatoverlay.*` (internal package name kept for compile compatibility)
- `res/layout/view_chat_overlay.xml`
- `res/layout/item_chat_line.xml`
- melon drawables + `mcmessenger_strings.xml`

Microsoft device-code / local accounts stay the engine's authenticator.

## Standalone identity

- Launcher name **McMessenger** (all locales, window title, `Tools.APP_NAME`)
- `applicationId` **`com.fracturedzen.mcmessenger`**
- Home-screen / density icons: melon block
- Wiki button → in-app **Credits** (LGPL attribution)
- Social button → GitHub `FracturedZen/McMessenger`
- Default instance icon: melon block

Java runtime zips may still be fetched from the upstream JRE host so the game can boot. That is a file mirror, not product branding.

## Re-applying after `git pull`

```powershell
cd mojo-src
git pull
cd ..
.\scripts\apply-overlay.ps1
.\scripts\build.ps1
```

Install markers (`// MC_CHAT_OVERLAY`, `// MC_APP_NAME`, …) are idempotent. Overlay Java/XML are overwritten from `overlay/`.

## Chat-only load cut (not a silent world cheat)

The overlay does not just paint over the world. Before the JVM starts we rewrite `options.txt`:

- `renderDistance:2` / `simulationDistance:2` — the client **tells the server** its view-distance, so vanilla ships a 5×5 chunk window instead of a 25×25. That is the same Video Settings slider, not a hidden packet.
- Audio categories 0, cheap graphics, max 10 FPS, no clouds/particles/shadows.

When Cover is on, the GL backbuffer is shrunk to 16×16 so we are not shading a full-screen frame nobody sees.

After a login grace, a **javaagent** (`mcmessenger-agent.jar`) drops inbound Netty **ByteBuf** frames larger than 16 KiB. Chunk and light payloads are large; chat, keepalive, and teleport confirms are small and still flow. We do **not** skip keepalives, teleport confirms, or movement acks. We do **not** hide you from the tab list.

The agent is JVM bytecode shipped as an asset and passed as `-javaagent` to Minecraft’s JRE. It is not a Fabric/Forge mod.

## Auto-respawn

The overlay has **Auto-respawn: on/off** (saved) and a one-shot **Respawn** button.

On death (chat/log: “You died”, “was slain…”, etc. for your username):

1. Enter/Space — vanilla death screen focuses Respawn.
2. The agent sends play `client_command` / PERFORM_RESPAWN (action 0) using a small version→packet-id table. Wrong ids are skipped; Enter still runs.

This is the same packet the Respawn button sends. It is not a ghost/killaura hook.
