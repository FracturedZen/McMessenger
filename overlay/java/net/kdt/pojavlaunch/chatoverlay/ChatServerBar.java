package net.kdt.pojavlaunch.chatoverlay;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

import git.artdeell.mojo.R;

/** Host/port on the launcher so Play joins a Java server with no Minecraft menus. */
public final class ChatServerBar {
    private ChatServerBar() {}

    public static void install(View root, Button playButton) {
        if (root == null) return;
        if (root.findViewById(R.id.mc_server_bar) == null) {
            ViewGroup parent = playButton != null ? (ViewGroup) playButton.getParent() : (ViewGroup) root;
            View bar = LayoutInflater.from(root.getContext()).inflate(R.layout.view_server_bar, parent, false);
            if (parent instanceof ConstraintLayout && playButton != null) {
                insertAbovePlay((ConstraintLayout) parent, playButton, bar);
            } else if (playButton != null) {
                int idx = parent.indexOfChild(playButton);
                parent.addView(bar, Math.max(idx, 0));
            } else {
                parent.addView(bar);
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
    }

    private static void insertAbovePlay(ConstraintLayout cl, View play, View bar) {
        cl.addView(bar);
        View spinner = cl.findViewById(R.id.mc_version_spinner);
        ConstraintSet cs = new ConstraintSet();
        cs.clone(cl);
        cs.constrainWidth(bar.getId(), ConstraintSet.MATCH_CONSTRAINT);
        cs.constrainHeight(bar.getId(), ConstraintSet.WRAP_CONTENT);
        cs.connect(bar.getId(), ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
        cs.connect(bar.getId(), ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
        cs.connect(bar.getId(), ConstraintSet.BOTTOM, play.getId(), ConstraintSet.TOP);
        if (spinner != null) {
            cs.connect(bar.getId(), ConstraintSet.TOP, spinner.getId(), ConstraintSet.BOTTOM);
            cs.connect(spinner.getId(), ConstraintSet.BOTTOM, bar.getId(), ConstraintSet.TOP);
        }
        cs.applyTo(cl);
    }
}
