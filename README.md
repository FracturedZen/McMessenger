# McMessenger

Chat-only **Minecraft Java** client for Android. Sign in as you (Microsoft or offline), join a server, and talk. The world is not shown.

This app is **McMessenger**. It is not PojavLauncher or MojoLauncher. You do not need those apps installed.

## Credits

Launcher engine forked from [MojoLauncher](https://github.com/MojoLauncher/MojoLauncher) (LGPL-3.0), which is based on PojavLauncher. Thank you to those projects. We keep their license and copyright; the product name, icon, and package are ours.

## What this is / is not

| McMessenger | Not this |
|-----------|----------|
| Real Minecraft Java client on the phone | Headless bot |
| Built-in Microsoft + offline login | Reading another app's token files |
| Chat overlay after launch | Fabric/Forge/Quilt mods |
| You occupy your own player slot | A second RelayBot slot |

The client still **joins play state** (keepalive + teleport confirm or the server kicks you). We do not draw or keep the world:

- `options.txt` view-distance 2 so the server sends far fewer chunks
- Cover mode shrinks the GL surface to 16×16
- After login, large inbound Netty frames (chunks/light) are dropped; chat packets stay
- **Auto-respawn** toggle (and a Respawn button) if you die — Enter on the death screen plus the vanilla PERFORM_RESPAWN packet

The whole UI is **Minecraft melon-themed**. Aimed at **1.8 through current**. Chat length, respawn packet ids, and log formats switch by version.

## GitHub

Remote: **https://github.com/FracturedZen/McMessenger**

[Actions → Build debug APK](https://github.com/FracturedZen/McMessenger/actions) uploads a sideload debug APK. Package id: `com.fracturedzen.mcmessenger`.

## Install

1. Download the latest **mcmessenger-debug-apk** artifact from Actions (run titled with the latest commit).
2. Uninstall any older debug build that still used the Mojo package id (`git.artdeell.mjlaunch.debug`) so you do not have two icons.
3. Sideload the `.apk`. Home screen name is **McMessenger**.
4. Sign in (Microsoft or offline), pick a Java version, Play. The chat overlay is on top after the client boots.

To auto-join a server, put this in the instance **game arguments**:

```
--server YOUR.HOST --port 25565
```

## How chat works

```
  You (Microsoft / offline login)
                 |
          Phone JVM + LWJGL
          (full Java client)
                 |
          Java server TCP
                 |
  Overlay  <-- tail latestlog.txt  (incoming)
  Overlay  --> T, unicode, Enter   (outgoing)
```

No anti-cheat bypass. Servers that ban "weird clients" may still dislike a phone JVM.

## License

LGPL-3.0. Overlay code in `overlay/` is the same license as the upstream engine so it can be linked into the fork. You must keep upstream LICENSE and copyright when you distribute a built APK. See `docs/FORK.md`.
