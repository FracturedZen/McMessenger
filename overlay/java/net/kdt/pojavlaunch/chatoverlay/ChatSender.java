package net.kdt.pojavlaunch.chatoverlay;

import static net.kdt.pojavlaunch.game.platform.Platform.PLATFORM;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import net.kdt.pojavlaunch.CallbackBridge;

/**
 * Types into the real client: open chat (T), unicode payload, Enter.
 * Same path as Mojo's {@code TouchCharInput}. Chat keybind default is T.
 */
public final class ChatSender {
    public static final int CHAT_ANDROID_KEYCODE = KeyEvent.KEYCODE_T;
    private static final int MAX_LEN = 256;
    private static final long OPEN_DELAY_MS = 90;

    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean sending;

    public boolean send(String raw) {
        if (sending) return false;
        if (raw == null) return false;
        String text = raw.replace("\r\n", "\n").trim();
        if (text.isEmpty()) return false;
        if (text.length() > MAX_LEN) text = text.substring(0, MAX_LEN);
        sending = true;
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
