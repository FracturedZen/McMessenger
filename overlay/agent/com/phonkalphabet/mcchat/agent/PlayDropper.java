package com.phonkalphabet.mcchat.agent;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Size filter for inbound frames, plus optional PERFORM_RESPAWN when the overlay
 * asks via {@code .mcchat-control} in the game directory (JVM {@code user.dir}).
 */
public final class PlayDropper {
    public static final long GRACE_MS = 45_000L;
    public static final int LARGE_BYTES = 4096;

    private static volatile long startMs;
    private static volatile Object lastCtx;
    private static volatile long lastRespawnMs;

    /** Play-state serverbound client_command ids. Wrong id can desync — unknown versions skip the packet and rely on Enter. */
    private static final Map<String, Integer> CLIENT_COMMAND_ID = new HashMap<String, Integer>();
    static {
        CLIENT_COMMAND_ID.put("1.19.4", 0x07);
        CLIENT_COMMAND_ID.put("1.20", 0x07);
        CLIENT_COMMAND_ID.put("1.20.1", 0x07);
        CLIENT_COMMAND_ID.put("1.20.2", 0x08);
        CLIENT_COMMAND_ID.put("1.20.3", 0x09);
        CLIENT_COMMAND_ID.put("1.20.4", 0x09);
        CLIENT_COMMAND_ID.put("1.20.5", 0x0A);
        CLIENT_COMMAND_ID.put("1.20.6", 0x0A);
        CLIENT_COMMAND_ID.put("1.21", 0x0A);
        CLIENT_COMMAND_ID.put("1.21.1", 0x0A);
        CLIENT_COMMAND_ID.put("1.21.2", 0x0C);
        CLIENT_COMMAND_ID.put("1.21.3", 0x0C);
        CLIENT_COMMAND_ID.put("1.21.4", 0x0C);
        CLIENT_COMMAND_ID.put("1.21.5", 0x0C);
        CLIENT_COMMAND_ID.put("1.21.6", 0x0C);
        CLIENT_COMMAND_ID.put("1.21.7", 0x0C);
        CLIENT_COMMAND_ID.put("1.21.8", 0x0C);
        CLIENT_COMMAND_ID.put("1.21.9", 0x0C);
        CLIENT_COMMAND_ID.put("1.21.10", 0x0C);
        CLIENT_COMMAND_ID.put("1.21.11", 0x0C);
    }

    public static void start() {
        startMs = System.currentTimeMillis();
        Thread t = new Thread(PlayDropper::pollLoop, "mc-chat-respawn");
        t.setDaemon(true);
        t.start();
    }

    public static void noteContext(Object ctx) {
        if (ctx != null) lastCtx = ctx;
    }

    public static boolean shouldDrop(Object msg) {
        if (msg == null) return false;
        int n = readableBytes(msg);
        if (n < LARGE_BYTES) return false;
        if (startMs == 0L) startMs = System.currentTimeMillis();
        if (System.currentTimeMillis() - startMs < GRACE_MS) return false;
        return true;
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
                File f = Path.of(System.getProperty("user.dir", "."), ".mcchat-control").toFile();
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
            System.out.println("[mc-chat-only] sent PERFORM_RESPAWN client_command id=0x" + Integer.toHexString(id));
        } catch (Throwable t) {
            System.out.println("[mc-chat-only] respawn packet failed: " + t.getMessage());
        }
    }

    private static Integer clientCommandId(String version) {
        if (version == null || version.isEmpty()) return CLIENT_COMMAND_ID.get("1.21.4");
        if (CLIENT_COMMAND_ID.containsKey(version)) return CLIENT_COMMAND_ID.get(version);
        for (int i = 0; i < 4; i++) {
            int cut = version.lastIndexOf('.');
            if (cut <= 0) break;
            version = version.substring(0, cut);
            if (CLIENT_COMMAND_ID.containsKey(version)) return CLIENT_COMMAND_ID.get(version);
        }
        if (version.startsWith("1.21")) return 0x0C;
        if (version.startsWith("1.20")) return 0x09;
        if (version.startsWith("1.19")) return 0x07;
        return null;
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
