package net.kdt.pojavlaunch.chatoverlay;

import android.util.Log;

import net.kdt.pojavlaunch.utils.MCOptionUtils;

import java.io.File;

/**
 * Cheap-client options. Unknown keys are ignored by older GameOptions parsers.
 * {@code renderDistance:2} is 2 chunks on 1.8+ and "Short" on 1.6–1.7 — both small.
 */
public final class ChatOnlyOptions {
    private static final String TAG = "McMessenger";

    public static void apply(File gameDir) {
        if (gameDir == null) return;
        try {
            MCOptionUtils.load(gameDir.getAbsolutePath());
            set("renderDistance", "2");
            set("simulationDistance", "2");
            set("entityDistanceScaling", "0.5");
            set("graphicsMode", "0");
            set("fancyGraphics", "false");
            set("particles", "2");
            set("ao", "0");
            set("clouds", "false");
            set("entityShadows", "false");
            set("biomeBlendRadius", "0");
            set("enableVsync", "false");
            set("vsync", "false");
            set("maxFps", "10");
            set("fpsLimit", "10");
            set("limitFramerate", "10");
            set("fullscreen", "false");
            set("bobView", "false");
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
            Log.i(TAG, "Applied chat-only options.txt (small view-distance, muted audio)");
        } catch (Exception e) {
            Log.w(TAG, "Could not apply chat-only options", e);
        }
    }

    private static void set(String key, String value) {
        MCOptionUtils.set(key, value);
    }
}
