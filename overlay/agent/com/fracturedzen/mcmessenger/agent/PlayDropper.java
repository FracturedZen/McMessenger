package com.fracturedzen.mcmessenger.agent;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inbound size filter + optional PERFORM_RESPAWN. Packet ids are version-specific;
 * unknown versions skip the packet and the overlay still uses Enter on the death screen.
 */
public final class PlayDropper {
    /** Join Game / command tree / dimension codec can be huge. Do not drop during login. */
    public static final long GRACE_MS = 60_000L;
    /**
     * 1.19+ signed chat can be several KB. Chunks/light are typically much larger.
     * 16 KiB keeps chat; still drops bulk terrain.
     */
    public static final int LARGE_BYTES = 16384;

    private static final Pattern VER = Pattern.compile("(?:^|[^0-9])(1\\.\\d{1,2}(?:\\.\\d{1,2})?|26\\.\\d+(?:\\.\\d+)?)(?:[^0-9]|$)");

    private static volatile long startMs;
    private static volatile Object lastCtx;
    private static volatile long lastRespawnMs;

    /** Play serverbound client_command / Client Status. Action 0 = PERFORM_RESPAWN on every version here. */
    private static final Map<String, Integer> CLIENT_COMMAND_ID = new LinkedHashMap<String, Integer>();
    static {
        putFamily("1.7", 0x16, "1.7.10");
        putFamily("1.8", 0x16, "1.8.8", "1.8.9");
        putFamily("1.9", 0x03, "1.9.4");
        putFamily("1.10", 0x03, "1.10.2");
        putFamily("1.11", 0x03, "1.11.2");
        putFamily("1.12", 0x03, "1.12.1", "1.12.2");
        putFamily("1.13", 0x03, "1.13.1", "1.13.2");
        putFamily("1.14", 0x04, "1.14.1", "1.14.2", "1.14.3", "1.14.4");
        putFamily("1.15", 0x04, "1.15.1", "1.15.2");
        putFamily("1.16", 0x04, "1.16.1", "1.16.2", "1.16.3", "1.16.4", "1.16.5");
        putFamily("1.17", 0x04, "1.17.1");
        putFamily("1.18", 0x04, "1.18.1", "1.18.2");
        CLIENT_COMMAND_ID.put("1.19", 0x06);
        CLIENT_COMMAND_ID.put("1.19.1", 0x07);
        CLIENT_COMMAND_ID.put("1.19.2", 0x07);
        CLIENT_COMMAND_ID.put("1.19.3", 0x06);
        CLIENT_COMMAND_ID.put("1.19.4", 0x07);
        putFamily("1.20", 0x07, "1.20.1");
        CLIENT_COMMAND_ID.put("1.20.2", 0x08);
        CLIENT_COMMAND_ID.put("1.20.3", 0x09);
        CLIENT_COMMAND_ID.put("1.20.4", 0x09);
        CLIENT_COMMAND_ID.put("1.20.5", 0x0A);
        CLIENT_COMMAND_ID.put("1.20.6", 0x0A);
        CLIENT_COMMAND_ID.put("1.21", 0x09);
        CLIENT_COMMAND_ID.put("1.21.1", 0x09);
        putFamily("1.21.2", 0x0C,
                "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8",
                "1.21.9", "1.21.10", "1.21.11");
        CLIENT_COMMAND_ID.put("26.1", 0x0C);
    }

    private static void putFamily(String base, int id, String... extra) {
        CLIENT_COMMAND_ID.put(base, id);
        for (String e : extra) CLIENT_COMMAND_ID.put(e, id);
    }

    public static void start() {
        startMs = System.currentTimeMillis();
        Thread t = new Thread(PlayDropper::pollLoop, "mcmessenger-respawn");
        t.setDaemon(true);
        t.start();
    }

    public static void noteContext(Object ctx) {
        if (ctx != null) lastCtx = ctx;
    }

    public static boolean shouldDrop(Object msg) {
        // Off: Velocity /queue switches send a new Join Game + registry (often
        // hundreds of KB) on the same TCP connection after you have been in
        // the lobby longer than GRACE_MS. Dropping that kicks you, then the
        // client may Transfer to the short server name and hit UnknownHost.
        return false;
    }

    public static void release(Object msg) {
        if (msg == null) return;
        try {
            Class<?> util = Class.forName("io.netty.util.ReferenceCountUtil");
            util.getMethod("release", Object.class).invoke(null, msg);
        } catch (Throwable ignored) {
            // Best-effort; leaking a dropped buf is better than crashing the client.
        }
    }

    private static void pollLoop() {
        while (true) {
            try {
                Thread.sleep(250);
                File f = new File(System.getProperty("user.dir", "."), ".mcmessenger-control");
                if (!f.isFile()) continue;
                boolean want = false;
                String version = "";
                try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("respawn=1")) want = true;
                        if (line.startsWith("version=")) version = line.substring(8).trim();
                    }
                }
                if (want) tryRespawn(version);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable ignored) {
                // never kill the game JVM
            }
        }
    }

    private static void tryRespawn(String version) {
        long now = System.currentTimeMillis();
        if (now - lastRespawnMs < 500) return;
        lastRespawnMs = now;
        Integer id = clientCommandId(version);
        if (id == null || lastCtx == null) return;
        try {
            byte[] payload = concat(varInt(id), varInt(0));
            byte[] frame = concat(varInt(payload.length), payload);
            Object buf = unpooled(frame);
            Object channel = lastCtx.getClass().getMethod("channel").invoke(lastCtx);
            Method write = channel.getClass().getMethod("writeAndFlush", Object.class);
            write.invoke(channel, buf);
            System.out.println("[mcmessenger] sent PERFORM_RESPAWN client_command id=0x" + Integer.toHexString(id));
        } catch (Throwable t) {
            System.out.println("[mcmessenger] respawn packet failed: " + t.getMessage());
        }
    }

    static Integer clientCommandId(String raw) {
        String v = extractVersion(raw);
        if (v.isEmpty()) return null;
        if (CLIENT_COMMAND_ID.containsKey(v)) return CLIENT_COMMAND_ID.get(v);
        String cur = v;
        for (int i = 0; i < 3; i++) {
            int cut = cur.lastIndexOf('.');
            if (cut <= 0) break;
            cur = cur.substring(0, cut);
            if (CLIENT_COMMAND_ID.containsKey(cur)) return CLIENT_COMMAND_ID.get(cur);
        }
        return null;
    }

    static String extractVersion(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        Matcher m = VER.matcher(raw);
        String last = "";
        while (m.find()) last = m.group(1);
        return last;
    }

    private static Object unpooled(byte[] bytes) throws Exception {
        Class<?> u = Class.forName("io.netty.buffer.Unpooled");
        return u.getMethod("wrappedBuffer", byte[].class).invoke(null, (Object) bytes);
    }

    private static byte[] varInt(int value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(5);
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v);
        return out.toByteArray();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] n = new byte[a.length + b.length];
        System.arraycopy(a, 0, n, 0, a.length);
        System.arraycopy(b, 0, n, a.length, b.length);
        return n;
    }

    private static int readableBytes(Object msg) {
        try {
            Object n = msg.getClass().getMethod("readableBytes").invoke(msg);
            if (n instanceof Integer) return (Integer) n;
        } catch (Throwable ignored) {
            // Not a ByteBuf (already-decoded packet object) — never drop those here.
        }
        return -1;
    }
}
