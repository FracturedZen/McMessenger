package net.kdt.pojavlaunch.chatoverlay;

import android.content.Context;
import android.util.Log;

import java.util.List;

/**
 * Vanilla client flags so we never need the title-screen Multiplayer button.
 * PC direct-connect does SRV ({@code _minecraft._tcp}) and omits the port.
 * We do the same unless the user typed an explicit port.
 */
public final class ChatServerLaunch {
    private static final String TAG = "McMessenger";

    private ChatServerLaunch() {}

    public static void appendClientArgs(Context ctx, List<String> launchArgs) {
        appendClientArgs(ctx, launchArgs, "");
    }

    public static void appendClientArgs(Context ctx, List<String> launchArgs, String versionId) {
        String host = ChatServerPrefs.host(ctx);
        if (host == null || host.isEmpty()) {
            Log.w(TAG, "No server address — game will sit on the title screen under the overlay");
            return;
        }
        Integer explicitPort = ChatServerPrefs.explicitPort(ctx);
        stripFlag(launchArgs, "--server");
        stripFlag(launchArgs, "--port");
        stripFlag(launchArgs, "--quickPlayMultiplayer");

        // Pass the typed hostname. Pre-resolving SRV into a different name
        // made some networks Transfer/reconnect to a name Android cannot look up.
        // 1.20+ quickPlay without a port does SRV the same way the PC list does.
        boolean quickPlay = McVersion.atLeast(versionId, 1, 20, 0) || McVersion.code(versionId) >= 26 * 10000;
        if (quickPlay) {
            String addr = explicitPort != null ? (host + ":" + explicitPort) : host;
            launchArgs.add("--quickPlayMultiplayer");
            launchArgs.add(addr);
            Log.i(TAG, "quickPlayMultiplayer " + addr);
            return;
        }
        launchArgs.add("--server");
        launchArgs.add(host);
        if (explicitPort != null) {
            launchArgs.add("--port");
            launchArgs.add(Integer.toString(explicitPort));
            Log.i(TAG, "Auto-join " + host + ":" + explicitPort);
        } else {
            Log.i(TAG, "Auto-join " + host + " (no port; client default/SRV)");
        }
    }

    private static void stripFlag(List<String> args, String flag) {
        for (int i = args.size() - 1; i >= 0; i--) {
            if (!flag.equals(args.get(i))) continue;
            args.remove(i);
            if (i < args.size() && !args.get(i).startsWith("--")) args.remove(i);
        }
    }
}
