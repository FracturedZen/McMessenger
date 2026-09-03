package net.kdt.pojavlaunch.chatoverlay;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat from Mojo {@code latestlog.txt} across vanilla log formats:
 * 1.6–1.12 {@code [Client thread/INFO]: [CHAT]}, 1.13–1.18 {@code [CHAT]},
 * 1.19+ {@code [System] [CHAT]} / {@code [Not Secure] [CHAT]}.
 */
public final class ChatLogParser {
    private static final Pattern SECTION = Pattern.compile("§.");
    private static final Pattern TIMESTAMP = Pattern.compile("^\\[\\d{1,2}:\\d{2}:\\d{2}]\\s*");
    private static final Pattern LOGGER = Pattern.compile("^\\[[^\\]]+]\\s+\\[[^\\]]+]\\s*:\\s*");
    private static final Pattern CHAT_TAG = Pattern.compile(
            "(?:\\[(?:System|Not Secure|Secure|Modified)]\\s*)?\\[CHAT]\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ANGLE = Pattern.compile("^<([A-Za-z0-9_]{1,16})>\\s+(.*)$");
    private static final Pattern COLON = Pattern.compile("^([A-Za-z0-9_]{1,16}):\\s+(.*)$");
    private static final Pattern JSON_TEXT = Pattern.compile("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    public ChatMessage parseLine(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String line = stripSection(raw).trim();
        if (line.isEmpty()) return null;
        line = TIMESTAMP.matcher(line).replaceFirst("");
        line = LOGGER.matcher(line).replaceFirst("");

        Matcher tagged = CHAT_TAG.matcher(line);
        if (tagged.find()) return classify(unescape(tagged.group(1)));

        if (line.startsWith("{") && line.contains("\"text\"")) {
            StringBuilder acc = new StringBuilder();
            Matcher jt = JSON_TEXT.matcher(line);
            while (jt.find()) acc.append(unescape(jt.group(1)));
            if (acc.length() > 0) return classify(acc.toString());
        }

        Matcher angle = ANGLE.matcher(line);
        if (angle.matches()) {
            return new ChatMessage("player", angle.group(1), angle.group(2));
        }
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

    private static String unescape(String s) {
        if (s == null) return "";
        return s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
