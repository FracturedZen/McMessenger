package net.kdt.pojavlaunch.chatoverlay;

import android.content.Context;
import android.util.Log;

import java.util.List;

/**
 * Vanilla client flags so we never need the title-screen Multiplayer button.
 * {@code --server}/{@code --port} work from 1.3 through current Java.
 */
public final class ChatServerLaunch {
    private static final String TAG = "McMessenger";

    private ChatServerLaunch() {}

    public static void appendClientArgs(Context ctx, List<String> launchArgs) {
        String host = ChatServerPrefs.host(ctx);
        if (host == null || host.isEmpty()) {
            Log.w(TAG, "No server address — game will sit on the title screen under the overlay");
            return;
        }
        int port = ChatServerPrefs.port(ctx);
        stripFlag(launchArgs, "--server");
        stripFlag(launchArgs, "--port");
        launchArgs.add("--server");
        launchArgs.add(host);
        launchArgs.add("--port");
        launchArgs.add(Integer.toString(port));
        Log.i(TAG, "Auto-join " + host + ":" + port);
    }

    private static void stripFlag(List<String> args, String flag) {
        for (int i = args.size() - 1; i >= 0; i--) {
            if (!flag.equals(args.get(i))) continue;
            args.remove(i);
            if (i < args.size() && !args.get(i).startsWith("--")) args.remove(i);
        }
    }
}
