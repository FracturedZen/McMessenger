package net.kdt.pojavlaunch.chatoverlay;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.game.GameActivity;

import java.io.File;

import git.artdeell.mojo.R;

/**
 * Chat-only Android UI on top of Mojo's GameActivity.
 * Login stays in Mojo. This only covers the running client.
 */
public final class ChatOverlayController {
    private final Activity activity;
    private final ChatLogParser parser = new ChatLogParser();
    private final ChatSender sender = new ChatSender();
    private final ChatRespawn respawn = new ChatRespawn();
    private ChatLogTailer tailer;

    private View root;
    private LinearLayout transcript;
    private ScrollView scroller;
    private EditText input;
    private TextView status;
    private Button coverBtn;
    private Button autoRespawnBtn;
    private boolean cover = false;
    private boolean stickBottom = true;
    private String selfName = "";

    private ChatOverlayController(Activity activity) {
        this.activity = activity;
    }

    public static void install(GameActivity activity) {
        install(activity, null);
    }

    public static void install(GameActivity activity, File gameDir) {
        ChatOverlayController c = new ChatOverlayController(activity);
        if (gameDir != null) ChatControlFile.setGameDir(gameDir);
        try {
            Account account = Accounts.getCurrent();
            if (account != null && account.username != null) c.selfName = account.username;
        } catch (Throwable ignored) {
            // Overlay still works without a name; death detection is looser.
        }
        if (activity.getIntent() != null && activity.getIntent().getExtras() != null) {
            String ver = McVersion.extract(activity.getIntent().getExtras().getString(GameActivity.INTENT_LAUNCH_VERSION, ""));
            c.respawn.setVersion(ver);
            c.sender.setVersion(ver);
        }
        c.attach();
    }

    private void attach() {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        root = LayoutInflater.from(activity).inflate(R.layout.view_chat_overlay, content, false);
        content.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        transcript = root.findViewById(R.id.mc_chat_transcript);
        scroller = root.findViewById(R.id.mc_chat_scroll);
        input = root.findViewById(R.id.mc_chat_input);
        status = root.findViewById(R.id.mc_chat_status);
        coverBtn = root.findViewById(R.id.mc_chat_cover);
        autoRespawnBtn = root.findViewById(R.id.mc_chat_autorespawn);
        Button sendBtn = root.findViewById(R.id.mc_chat_send);
        Button respawnNowBtn = root.findViewById(R.id.mc_chat_respawn_now);

        SharedPreferences prefs = activity.getSharedPreferences("mcmessenger", 0);
        respawn.setAuto(prefs.getBoolean("auto_respawn", false));
        syncAutoRespawnButton();

        scroller.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            int childH = scroller.getChildAt(0) != null ? scroller.getChildAt(0).getHeight() : 0;
            stickBottom = childH - y - scroller.getHeight() < 80;
        });

        sendBtn.setOnClickListener(v -> send());
        input.setOnEditorActionListener((v, actionId, event) -> {
            send();
            return true;
        });
        coverBtn.setOnClickListener(v -> setCover(!cover));
        root.findViewById(R.id.mc_chat_hide).setOnClickListener(v -> setCover(false));
        autoRespawnBtn.setOnClickListener(v -> {
            boolean next = !respawn.isAuto();
            respawn.setAuto(next);
            activity.getSharedPreferences("mcmessenger", 0).edit().putBoolean("auto_respawn", next).apply();
            syncAutoRespawnButton();
            addLine(new ChatMessage("system", null, next
                    ? "Auto-respawn on. On death, Respawn is pressed for you."
                    : "Auto-respawn off. Use Respawn if you die."));
        });
        respawnNowBtn.setOnClickListener(v -> {
            respawn.respawnNow();
            setStatus("Respawning…");
            addLine(new ChatMessage("system", null, "Respawn sent."));
        });
        Button queueBtn = root.findViewById(R.id.mc_chat_queue);
        if (queueBtn != null) {
            queueBtn.setOnClickListener(v -> sendQueueCmd());
            queueBtn.setOnLongClickListener(v -> {
                editQueueCmd();
                return true;
            });
        }

        String host = ChatServerPrefs.host(activity);
        Integer port = ChatServerPrefs.explicitPort(activity);
        if (host.isEmpty()) {
            addLine(new ChatMessage("system", null, "No server set. Go back, type an address, then Connect."));
            setStatus("No server");
        } else {
            String dest = port == null ? host : (host + ":" + port);
            addLine(new ChatMessage("system", null, "Joining " + dest + " (SRV like PC if no port). Queue button sends "
                    + ChatServerPrefs.queueCmd(activity) + " — long-press to change."));
            setStatus("Joining " + dest + "…");
        }
        setCover(true);
        View coverBtnView = root.findViewById(R.id.mc_chat_cover);
        View peekBtn = root.findViewById(R.id.mc_chat_hide);
        if (coverBtnView != null) coverBtnView.setVisibility(View.GONE);
        if (peekBtn != null) peekBtn.setVisibility(View.GONE);

        File log = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
        tailer = new ChatLogTailer(log, this::onLogLine);
        tailer.start();
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}
            @Override public void onViewDetachedFromWindow(View v) {
                if (tailer != null) tailer.stop();
            }
        });
    }

    private void onLogLine(String line) {
        ChatMessage msg = parser.parseLine(line);
        if (msg != null) {
            addLine(msg);
            handleDeathLine(msg.text);
            markConnected(msg.text);
            return;
        }
        String detected = McVersion.fromLogLine(line);
        if (!detected.isEmpty()) {
            respawn.setVersion(detected);
            sender.setVersion(detected);
        }
        String low = line.toLowerCase();
        if (low.contains("connecting to")) setStatus("Connecting…");
        if (low.contains("failed to connect") || low.contains("connection refused")
                || low.contains("timed out") || low.contains("unknown host")) {
            setStatus("Join failed");
            addLine(new ChatMessage("system", null, line));
        }
        if (low.contains("joined the game") || low.contains("logged in")
                || low.contains("multiplayer") && low.contains("joined")) {
            markConnected(line);
        }
        handleDeathLine(line);
    }

    private void markConnected(String text) {
        respawn.onAlive();
        String t = text == null ? "" : text.toLowerCase();
        if (t.contains("queue")) setStatus("In queue");
        else setStatus("Connected");
        if (!cover) setCover(true);
    }

    private void sendQueueCmd() {
        String cmd = ChatServerPrefs.queueCmd(activity);
        if (!sender.send(cmd)) return;
        addLine(new ChatMessage("you", "You", cmd));
        setStatus("Sent " + cmd);
    }

    private void editQueueCmd() {
        final EditText box = new EditText(activity);
        box.setText(ChatServerPrefs.queueCmd(activity));
        box.setHint("/queue");
        box.setSelectAllOnFocus(true);
        new AlertDialog.Builder(activity)
                .setTitle("Queue / join command")
                .setMessage("Sent as chat, same as typing it. Long-press Queue to change.")
                .setView(box)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    ChatServerPrefs.saveQueueCmd(activity, box.getText().toString());
                    Toast.makeText(activity, "Queue button: " + ChatServerPrefs.queueCmd(activity), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void handleDeathLine(String text) {
        if (!ChatDeath.isSelfDeath(text, selfName)) return;
        boolean wasDead = respawn.isDead();
        respawn.onDeath();
        if (!wasDead) {
            setStatus(respawn.isAuto() ? "Dead · auto-respawn" : "Dead · tap Respawn");
            addLine(new ChatMessage("system", null, respawn.isAuto()
                    ? "You died. Auto-respawn is on."
                    : "You died. Tap Respawn, or turn on Auto-respawn."));
        }
    }

    private void syncAutoRespawnButton() {
        if (autoRespawnBtn == null) return;
        boolean on = respawn.isAuto();
        autoRespawnBtn.setText(on ? "Auto-respawn: on" : "Auto-respawn: off");
    }

    private static boolean looksOnline(String text) {
        String t = text.toLowerCase();
        return t.contains("joined the game") || t.contains("logged in");
    }

    private void send() {
        String text = input.getText() != null ? input.getText().toString() : "";
        if (!sender.send(text)) return;
        addLine(new ChatMessage("you", "You", text.trim()));
        input.setText("");
    }

    private void addLine(ChatMessage msg) {
        TextView tv = new TextView(activity);
        tv.setTextColor(Color.parseColor("#F4FFE8"));
        tv.setTextSize(15f);
        tv.setPadding(8, 6, 8, 6);
        String prefix;
        if ("you".equals(msg.kind)) prefix = "You: ";
        else if ("player".equals(msg.kind) && msg.username != null) prefix = msg.username + ": ";
        else prefix = "System: ";
        tv.setText(prefix + msg.text);
        if ("system".equals(msg.kind)) tv.setTextColor(Color.parseColor("#C8E090"));
        if ("you".equals(msg.kind)) tv.setTextColor(Color.parseColor("#FF8A96"));
        transcript.addView(tv);
        if (stickBottom) {
            scroller.post(() -> scroller.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void setCover(boolean on) {
        cover = on;
        if (on) root.setBackgroundResource(R.drawable.mcmessenger_app_bg);
        else root.setBackgroundColor(Color.TRANSPARENT);
        coverBtn.setText(on ? "Game" : "Cover");
        root.setClickable(on);
        scroller.setVisibility(on ? View.VISIBLE : View.GONE);
        hideStockControls(on);
        if (on) ChatOnlySurface.shrink(activity);
        else ChatOnlySurface.restore(activity);
    }

    private void hideStockControls(boolean hide) {
        int vis = hide ? View.INVISIBLE : View.VISIBLE;
        View controls = activity.findViewById(R.id.main_control_layout);
        View hotbar = activity.findViewById(R.id.hotbar_view);
        View drawerBtn = activity.findViewById(R.id.drawer_button);
        if (controls != null) controls.setVisibility(vis);
        if (hotbar != null) hotbar.setVisibility(vis);
        if (drawerBtn != null) drawerBtn.setVisibility(hide ? View.GONE : View.VISIBLE);
    }

    private void setStatus(String s) {
        if (status != null) status.setText(s);
    }
}
