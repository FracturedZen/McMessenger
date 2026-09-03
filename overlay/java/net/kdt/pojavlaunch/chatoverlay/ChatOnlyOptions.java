package net.kdt.pojavlaunch.chatoverlay;

import android.util.Log;

import net.kdt.pojavlaunch.utils.MCOptionUtils;

import java.io.File;

/**
 * Force vanilla options that cut work the server and client would spend on the world.
 * {@code renderDistance} is sent as client view-distance, so the server ships fewer chunks.
 * This is not an anticheat bypass; it is the same slider as Video Settings.
 */
public final class ChatOnlyOptions {
    private static final String TAG = "McMessenger";

    public static void apply(File gameDir) {
        if (gameDir == null) return;
        try {
            MCOptionUtils.load(gameDir.getAbsolutePath());
            // View-distance 2 is the vanilla minimum on modern Java. Server then sends a 5x5 chunk window
            // instead of a 25x25 (RD 12) — the real packet cut, not a dropped TCP stream.
            set("renderDistance", "2");
            set("simulationDistance", "2");
            set("entityDistanceScaling", "0.0");
            set("graphicsMode", "0");
            set("particles", "2");
            set("ao", "false");
            set("clouds", "false");
            set("entityShadows", "false");
            set("biomeBlendRadius", "0");
            set("enableVsync", "false");
            set("maxFps", "10");
            set("fullscreen", "false");
            set("bobView", "false");
            set("fancyGraphics", "false");
            set("useVbo", "true");
            set("mipmapLevels", "0");
            set("forceUnicodeFont", "false");
            set("narrator", "0");
            set("autoJump", "false");
            set("tutorialStep", "none");
            set("soundCategory_master", "0.0");
            set("soundCategory_music", "0.0");
            set("soundCategory_record", "0.0");
            set("soundCategory_weather", "0.0");
            set("soundCategory_block", "0.0");
            set("soundCategory_hostile", "0.0");
            set("soundCategory_neutral", "0.0");
            set("soundCategory_player", "0.0");
            set("soundCategory_ambient", "0.0");
            set("soundCategory_voice", "0.0");
            MCOptionUtils.save();
            Log.i(TAG, "Applied chat-only options.txt (RD=2, no audio, cheap graphics)");
        } catch (Exception e) {
            Log.w(TAG, "Could not apply chat-only options", e);
        }
    }

    private static void set(String key, String value) {
        MCOptionUtils.set(key, value);
    }
}
