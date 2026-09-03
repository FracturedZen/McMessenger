package net.kdt.pojavlaunch.chatoverlay;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import net.kdt.pojavlaunch.CallbackBridge;

/**
 * Vanilla death screen focuses Respawn; Enter/Space activates it.
 * The javaagent also sends ServerboundClientCommand PERFORM_RESPAWN when asked,
 * which still works if the GL surface is 16×16.
 */
public final class ChatRespawn {
    public interface Listener {
        void onDeath(boolean auto);
        void onAlive();
        void onAutoFailed();
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::tick;
    private Listener listener;
    private boolean auto;
    private boolean dead;
    private boolean pulsing;
    private String version = "";
    private int pulses;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setAuto(boolean auto) {
        this.auto = auto;
        ChatControlFile.write(auto, dead && auto, version);
        if (auto && dead) startPulse();
        else if (!auto) stopPulse();
    }

    public boolean isAuto() {
        return auto;
    }

    public void setVersion(String version) {
        this.version = version == null ? "" : version;
        ChatControlFile.write(auto, dead && auto, this.version);
    }

    public void onDeath() {
        if (dead) return;
        dead = true;
        pulses = 0;
        ChatControlFile.write(auto, auto, version);
        if (listener != null) listener.onDeath(auto);
        if (auto) startPulse();
    }

    public void onAlive() {
        if (!dead) return;
        dead = false;
        pulses = 0;
        stopPulse();
        ChatControlFile.write(auto, false, version);
        if (listener != null) listener.onAlive();
    }

    public boolean isDead() {
        return dead;
    }

    /** One-shot even if auto is off. */
    public void respawnNow() {
        dead = true;
        pulses = 0;
        ChatControlFile.write(auto, true, version);
        fireKeys();
        startPulse();
    }

    private void startPulse() {
        if (pulsing) return;
        pulsing = true;
        main.post(tick);
    }

    private void stopPulse() {
        pulsing = false;
        main.removeCallbacks(tick);
        ChatControlFile.write(auto, false, version);
    }

    private void tick() {
        if (!pulsing) return;
        if (!dead) {
            stopPulse();
            return;
        }
        if (!auto && pulses > 4) {
            stopPulse();
            return;
        }
        if (auto && pulses == 10 && listener != null) listener.onAutoFailed();
        if (pulses > 20) {
            stopPulse();
            return;
        }
        ChatControlFile.write(auto, true, version);
        fireKeys();
        pulses++;
        main.postDelayed(tick, 700);
    }

    private void fireKeys() {
        // Default-focused Respawn button on the vanilla death screen.
        CallbackBridge.sendKeyPress(KeyEvent.KEYCODE_ENTER);
        CallbackBridge.sendKeyPress(KeyEvent.KEYCODE_SPACE);
    }
}
