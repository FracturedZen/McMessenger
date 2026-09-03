package net.kdt.pojavlaunch.chatoverlay;

import android.app.Activity;
import android.util.Log;
import android.view.View;

import net.kdt.pojavlaunch.CallbackBridge;
import net.kdt.pojavlaunch.game.GameView;

import git.artdeell.mojo.R;
import git.artdeell.mojoexec.MojoExec;

/**
 * When the overlay covers the world, shrink the GL backbuffer so the GPU is not
 * meshing a full-screen frame we never show.
 */
public final class ChatOnlySurface {
    private static final String TAG = "McMessenger";
    private static int savedW;
    private static int savedH;
    private static boolean shrunk;

    public static void shrink(Activity activity) {
        GameView view = activity.findViewById(R.id.main_game_render_view);
        if (view == null) return;
        if (!shrunk) {
            savedW = Math.max(CallbackBridge.windowWidth, 16);
            savedH = Math.max(CallbackBridge.windowHeight, 16);
        }
        apply(16, 16, 10f);
        view.setVisibility(View.INVISIBLE);
        shrunk = true;
        Log.i(TAG, "GL surface shrunk to 16x16");
    }

    public static void restore(Activity activity) {
        GameView view = activity.findViewById(R.id.main_game_render_view);
        if (view == null) return;
        view.setVisibility(View.VISIBLE);
        if (shrunk && savedW > 16 && savedH > 16) {
            apply(savedW, savedH, CallbackBridge.windowRate > 1f ? CallbackBridge.windowRate : 60f);
        } else {
            view.refreshSize();
        }
        shrunk = false;
    }

    private static void apply(int w, int h, float rate) {
        CallbackBridge.windowWidth = w;
        CallbackBridge.windowHeight = h;
        CallbackBridge.windowRate = rate;
        try {
            MojoExec.setDisplayParams(w, h, rate);
        } catch (Throwable t) {
            Log.w(TAG, "setDisplayParams failed", t);
        }
    }
}
