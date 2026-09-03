package net.kdt.pojavlaunch.chatoverlay;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

/**
 * Copies {@code assets/mcmessenger-agent.jar} into app files and appends {@code -javaagent}.
 * That agent runs inside the <em>game</em> JVM (not ART) and drops large inbound Netty
 * frames after login — chunks/light — while leaving small packets (chat, keepalive, teleport).
 */
public final class ChatOnlyAgentSupport {
    private static final String TAG = "McMessenger";
    private static final String ASSET = "mcmessenger-agent.jar";

    public static void appendJavaAgent(Context context, List<String> javaArgList) {
        File jar = extract(context);
        if (jar == null || !jar.isFile() || jar.length() < 32) {
            Log.w(TAG, "mcmessenger-agent.jar missing — large play packets will not be dropped. Run scripts/build-agent.ps1");
            return;
        }
        javaArgList.add("-javaagent:" + jar.getAbsolutePath());
        Log.i(TAG, "Using chat-only javaagent " + jar.getAbsolutePath());
    }

    private static File extract(Context context) {
        File out = new File(Tools.DIR_DATA, ASSET);
        try (InputStream in = context.getAssets().open(ASSET);
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
            return out;
        } catch (Exception e) {
            Log.w(TAG, "Could not extract " + ASSET, e);
            return out.exists() ? out : null;
        }
    }
}
