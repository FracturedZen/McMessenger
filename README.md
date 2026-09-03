# McMessenger

This is a **chat-only skin** for [MojoLauncher](https://github.com/MojoLauncher/MojoLauncher) (LGPL-3.0, based on PojavLauncher). It is not a Minecraft mod. It does not steal sessions from the Play Store app.

You log in with **Mojo's own Microsoft / offline account UI**. The game still joins the Java server as you. After launch, an Android chat overlay covers the world. Incoming chat is parsed from Mojo's log. Outgoing chat is typed into the real client the same way Mojo's on-screen keyboard already does.

Mineflayer is a separate path (`../mc-chat-relay`). Do that after this builds.

## What this is / is not

| This fork | Not this |
|-----------|----------|
| Real Minecraft Java client on the phone | Headless bot |
| Mojo's Microsoft + offline login | Reading another app's token files |
| Chat overlay on `GameActivity` | Fabric/Forge/Quilt mods |
| You occupy your own player slot | A second RelayBot slot |

The client still **joins play state** (keepalive + teleport confirm or the server kicks you). We do not draw or keep the world:

- `options.txt` view-distance 2 so the server sends far fewer chunks
- Cover mode shrinks the GL surface to 16×16
- After login, large inbound Netty frames (chunks/light) are dropped; chat packets stay
- **Auto-respawn** toggle (and a Respawn button) if you die — Enter on the death screen plus the vanilla PERFORM_RESPAWN packet

Vanilla Java has no official chat-only client. This is the launcher cutting work, not a game mod.

The whole UI is **Minecraft melon-themed**: dark rind launcher colors, striped rind backgrounds, flesh-red buttons, melon-block adaptive icon, slice in the chat header.

Aimed at **1.8 through current** (and whatever else Mojo will boot). Chat length, respawn packet ids, and log formats switch by version. Unknown versions skip the respawn *packet* and still use the death-screen Enter key.

## GitHub

Remote: **https://github.com/FracturedZen/McMessenger**

```powershell
cd C:\Users\Z\Desktop\McMessenger
.\scripts\link-github.ps1
```

Create the empty repo on GitHub named **McMessenger** under **FracturedZen** (no README), then `git push -u origin main`.

[Actions → Build debug APK](https://github.com/FracturedZen/McMessenger/actions) (after the first push) uploads a sideload debug APK (`assembleFullDebug` — Mojo has `full` / `noruntime` flavors, so plain `assembleDebug` is not the task). This Windows box has JDK 21 but **no Android SDK / Android Studio**, so the APK cannot be assembled here.

## What you run (Windows)

Android Studio + JDK 17 + Android SDK are required to produce an APK. Same as stock Mojo.

```powershell
cd C:\Users\Z\Desktop\McMessenger
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\clone-mojo.ps1
.\scripts\apply-overlay.ps1
.\scripts\build.ps1
```

APK lands in `mojo-src\app_pojavlauncher\build\outputs\apk\full\debug\`.

Sideload it. The home-screen name is **McMessenger** (`com.fracturedzen.mcmessenger`) — it is not Pojav/Mojo and does not replace those apps. Sign in (Microsoft or offline) inside the launcher, pick a version, Play. When the game boots, the **McMessenger** overlay is on top.

To auto-join a server (so you do not need the title-screen Multiplayer button), put this in the instance's **game arguments**:

```
--server YOUR.HOST --port 25565
```

Vanilla Java accepts those flags.

## How chat works

```
  You (Microsoft / offline, Mojo authenticator)
                 |
          Mojo JVM + LWJGL
          (full Java client)
                 |
          Java server TCP
                 |
  Overlay  <-- tail latestlog.txt  (incoming)
  Overlay  --> T, unicode, Enter   (outgoing, CallbackBridge)
```

No bytecode injection. No anti-cheat bypass. Servers that ban "weird clients" may still dislike a phone JVM; that is a Mojo/Pojav issue, not this overlay.

## License

Overlay code in `overlay/` is LGPL-3.0, same as MojoLauncher, so it can be linked into the fork. You must keep Mojo's LICENSE and copyright when you distribute a built APK. See `docs/FORK.md`.
