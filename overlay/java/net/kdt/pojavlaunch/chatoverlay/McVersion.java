package net.kdt.pojavlaunch.chatoverlay;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pull a vanilla-ish {@code 1.x} / {@code 26.x} version out of Mojo instance ids
 * ({@code fabric-loader-…-1.21.4}, {@code 1.12.2-forge}, snapshots).
 */
public final class McVersion {
    private static final Pattern MC = Pattern.compile("(?:^|[^0-9])(1\\.\\d{1,2}(?:\\.\\d{1,2})?|26\\.\\d+(?:\\.\\d+)?)(?:[^0-9]|$)");

    public static String extract(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        Matcher m = MC.matcher(raw);
        String last = "";
        while (m.find()) last = m.group(1);
        return last;
    }

    /** Comparable like 1.12.2 → 1_12_02. Empty → 0. */
    public static int code(String raw) {
        String v = extract(raw);
        if (v.isEmpty()) return 0;
        String[] p = v.split("\\.");
        int a = n(p, 0), b = n(p, 1), c = n(p, 2);
        return a * 10000 + b * 100 + c;
    }

    public static boolean atLeast(String raw, int major, int minor, int patch) {
        return code(raw) >= major * 10000 + minor * 100 + patch;
    }

    /** Chat length: 100 before 1.11, 256 from 1.11. */
    public static int maxChatLength(String raw) {
        int c = code(raw);
        if (c == 0) return 256;
        if (c < 1 * 10000 + 11 * 100) return 100;
        return 256;
    }

    public static String fromLogLine(String line) {
        if (line == null) return "";
        String low = line.toLowerCase();
        if (!(low.contains("minecraft") || low.contains("lwjgl") || low.contains("starting"))) {
            return extract(line);
        }
        return extract(line);
    }

    private static int n(String[] p, int i) {
        if (i >= p.length) return 0;
        try {
            return Integer.parseInt(p[i]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
