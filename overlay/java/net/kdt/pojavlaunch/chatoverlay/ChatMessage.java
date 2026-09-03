package net.kdt.pojavlaunch.chatoverlay;

public final class ChatMessage {
    public final long ts;
    public final String kind;
    public final String username;
    public final String text;

    public ChatMessage(String kind, String username, String text) {
        this.ts = System.currentTimeMillis();
        this.kind = kind;
        this.username = username;
        this.text = text;
    }
}
