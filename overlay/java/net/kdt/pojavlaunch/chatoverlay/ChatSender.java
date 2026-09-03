package net.kdt.pojavlaunch.chatoverlay;

import static net.kdt.pojavlaunch.game.platform.Platform.PLATFORM;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import net.kdt.pojavlaunch.CallbackBridge;

/**
 * Types into the real client: open chat (T), unicode payload, Enter.
 * Same path as Mojo's {@code TouchCharInput}. Default bind is T on every vanilla version.
 * Length cap is 100 before 1.11 and 256 from 1.11 (unknown version → 256, truncated by older servers).
 */
public final class ChatSender {
    public static final int CHAT_ANDROID_KEYCODE = KeyEvent.KEYCODE_T;
    private static final long OPEN_DELAY_MS = 140;

    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean sending;
    private String version = "";

    public void setVersion(String version) {
        this.version = version == null ? "" : version;
    }

    public boolean send(String raw) {
        if (sending) return false;
        if (raw == null) return false;
        String text = raw.replace("\r\n", "\n").trim();
        if (text.isEmpty()) return false;
        int max = McVersion.maxChatLength(version);
        if (text.length() > max) text = text.substring(0, max);
        sending = true;
        ChatControlFile.pulseGrace();
        final String payload = text;
        CallbackBridge.sendKeyPress(CHAT_ANDROID_KEYCODE);
        main.postDelayed(() -> {
            try {
                PLATFORM.sendBulkUnicodeEvent(payload, CallbackBridge.getCurrentMods());
                CallbackBridge.sendKeyPress(KeyEvent.KEYCODE_ENTER);
            } finally {
                sending = false;
            }
        }, OPEN_DELAY_MS);
        return true;
    }
}
