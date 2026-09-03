package net.kdt.pojavlaunch.chatoverlay;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pull visible chat out of Mojo's latestlog.txt / Logger lines.
 * Vanilla prints chat as {@code [CHAT] ...}. No click-events are executed.
 */
public final class ChatLogParser {
    private static final Pattern CHAT_TAG = Pattern.compile("\\[CHAT]\\s*(.*)$");
    private static final Pattern SYSTEM_CHAT = Pattern.compile("\\[System]\\s*\\[CHAT]\\s*(.*)$");
    private static final Pattern ANGLE = Pattern.compile("^<([A-Za-z0-9_]{1,16})>\\s+(.*)$");
    private static final Pattern COLON = Pattern.compile("^([A-Za-z0-9_]{1,16}):\\s+(.*)$");
    private static final Pattern SECTION = Pattern.compile("§.");

    public ChatMessage parseLine(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String line = stripSection(raw).trim();
        if (line.isEmpty()) return null;

        Matcher sys = SYSTEM_CHAT.matcher(line);
        if (sys.find()) return classify(sys.group(1));

        Matcher tagged = CHAT_TAG.matcher(line);
        if (tagged.find()) return classify(tagged.group(1));

        return null;
    }

    private ChatMessage classify(String body) {
        if (body == null) return null;
        String text = body.trim();
        if (text.isEmpty()) return null;
        Matcher angle = ANGLE.matcher(text);
        if (angle.matches()) {
            return new ChatMessage("player", angle.group(1), angle.group(2));
        }
        Matcher colon = COLON.matcher(text);
        if (colon.matches()) {
            return new ChatMessage("player", colon.group(1), colon.group(2));
        }
        return new ChatMessage("system", null, text);
    }

    static String stripSection(String s) {
        return SECTION.matcher(s).replaceAll("");
    }
}
