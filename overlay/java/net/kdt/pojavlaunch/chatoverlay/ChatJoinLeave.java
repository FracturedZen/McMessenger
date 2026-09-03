package net.kdt.pojavlaunch.chatoverlay;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Player join/leave from vanilla chat and client log lines. */
public final class ChatJoinLeave {
    private static final Pattern JOIN = Pattern.compile(
            "(?:^|\\s)([A-Za-z0-9_]{1,16}) (?:joined the game|has joined|joined)\\.?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEAVE = Pattern.compile(
            "(?:^|\\s)([A-Za-z0-9_]{1,16}) (?:left the game|has left|left the server|disconnected|lost connection)(?:[:.].*)?\\.?$",
            Pattern.CASE_INSENSITIVE);

    private ChatJoinLeave() {}

    public static ChatMessage parse(String text) {
        if (text == null) return null;
        String t = ChatLogParser.stripSection(text).trim();
        if (t.isEmpty()) return null;
        Matcher leave = LEAVE.matcher(t);
        if (leave.find()) {
            String name = leave.group(1);
            return new ChatMessage("leave", name, name + " left");
        }
        Matcher join = JOIN.matcher(t);
        if (join.find()) {
            String name = join.group(1);
            return new ChatMessage("join", name, name + " joined");
        }
        return null;
    }
}
