package net.kdt.pojavlaunch.chatoverlay;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;

import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

import git.artdeell.mojo.R;

/** Host/port on the launcher so Connect joins a Java server with no Minecraft menus. */
public final class ChatServerBar {
    private static final String TAG = "McMessenger";

    private ChatServerBar() {}

    public static void install(View root, Button playButton) {
        try {
            if (root == null) return;
            if (root.findViewById(R.id.mc_server_bar) == null && playButton != null) {
                ViewGroup parent = (ViewGroup) playButton.getParent();
                View bar = LayoutInflater.from(root.getContext()).inflate(R.layout.view_server_bar, parent, false);
                if (parent instanceof ConstraintLayout) {
                    insertAbovePlay((ConstraintLayout) parent, playButton, bar);
                } else {
                    int idx = parent.indexOfChild(playButton);
                    parent.addView(bar, Math.max(idx, 0));
                }
            }
            ChatServerPrefs.bind(root);
            if (playButton != null) {
                playButton.setOnClickListener(v -> {
                    if (!ChatServerPrefs.saveFrom(root) || ChatServerPrefs.host(root.getContext()).isEmpty()) {
                        Toast.makeText(root.getContext(), "Enter a Java server address first", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
                });
            }
        } catch (Throwable t) {
            Log.e(TAG, "server bar failed; launcher still opens", t);
        }
    }

    private static void insertAbovePlay(ConstraintLayout cl, View play, View bar) {
        ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT);
        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;

        View spinner = cl.findViewById(R.id.mc_version_spinner);
        if (spinner != null) {
            ConstraintLayout.LayoutParams slp = (ConstraintLayout.LayoutParams) spinner.getLayoutParams();
            if (slp != null && slp.bottomToTop == play.getId()) {
                slp.bottomToTop = bar.getId();
                spinner.setLayoutParams(slp);
                lp.topToBottom = spinner.getId();
                lp.bottomToTop = play.getId();
            } else {
                lp.bottomToTop = spinner.getId();
            }
        } else {
            lp.bottomToTop = play.getId();
        }
        cl.addView(bar, lp);
    }
}
