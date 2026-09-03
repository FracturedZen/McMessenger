package net.kdt.pojavlaunch.chatoverlay;

import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Side channel to the game JVM agent (different heap). {@code user.dir} in the
 * game is the instance directory; we write the same file under DIR_GAME_HOME.
 */
public final class ChatControlFile {
    public static final String NAME = ".mcchat-control";

    private static File dir;

    public static void setGameDir(File gameDir) {
        dir = gameDir;
    }

    public static File file() {
        if (dir != null) return new File(dir, NAME);
        if (Tools.DIR_GAME_HOME != null) {
            return new File(Tools.DIR_GAME_HOME, NAME);
        }
        return new File(NAME);
    }

    public static void write(boolean autoRespawn, boolean requestRespawn, String version) {
        String body = "autorespawn=" + (autoRespawn ? "1" : "0") + "\n"
                + "respawn=" + (requestRespawn ? "1" : "0") + "\n"
                + "version=" + (version == null ? "" : version) + "\n";
        File f = file();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // Overlay still uses Enter on the death screen.
        }
    }
}
