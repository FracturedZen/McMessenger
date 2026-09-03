package net.kdt.pojavlaunch.chatoverlay;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.game.GameActivity;

import java.io.File;
import java.lang.ref.WeakReference;

import git.artdeell.mojo.R;

/**
 * Chat-only Android UI on top of Mojo's GameActivity.
 * Login stays in Mojo. This only covers the running client.
 */
public final class ChatOverlayController {
    private static WeakReference<ChatOverlayController> sActive;

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
    private Button autoRespawnBtn;
    private View deathBar;
    private TextView deathText;
    private boolean cover = true;
    private boolean stickBottom = true;
    private boolean leaveDialogOpen = false;
    private String selfName = "";
    private String lastProgress = "";

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
        autoRespawnBtn = root.findViewById(R.id.mc_chat_autorespawn);
        deathBar = root.findViewById(R.id.mc_chat_death);
        deathText = root.findViewById(R.id.mc_chat_death_text);
        Button sendBtn = root.findViewById(R.id.mc_chat_send);
        Button respawnNowBtn = root.findViewById(R.id.mc_chat_respawn_now);
        Button deathRespawnBtn = root.findViewById(R.id.mc_chat_death_respawn);

        sActive = new WeakReference<>(this);
        respawn.setListener(new ChatRespawn.Listener() {
            @Override public void onDeath(boolean auto) {
                if (auto) {
                    setStatus("Dead · auto-respawn");
                    addLine(new ChatMessage("system", null, "You died. Auto-respawn is on."));
                } else {
                    showDeathBanner("You died. Auto-respawn is off — tap Respawn.");
                }
            }
            @Override public void onAlive() {
                hideDeathBanner();
            }
            @Override public void onAutoFailed() {
                showDeathBanner("Auto-respawn didn't work — tap Respawn.");
            }
        });

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
        View leaveBtn = root.findViewById(R.id.mc_chat_leave);
        if (leaveBtn != null) leaveBtn.setOnClickListener(v -> leaveToMenu());
        bindKeyboardShare();
        autoRespawnBtn.setOnClickListener(v -> {
            boolean next = !respawn.isAuto();
            respawn.setAuto(next);
            activity.getSharedPreferences("mcmessenger", 0).edit().putBoolean("auto_respawn", next).apply();
            syncAutoRespawnButton();
            addLine(new ChatMessage("system", null, next
                    ? "Auto-respawn on. On death, Respawn is pressed for you."
                    : "Auto-respawn off. Use Respawn if you die."));
        });
        View.OnClickListener doRespawn = v -> clickRespawn();
        respawnNowBtn.setOnClickListener(doRespawn);
        if (deathRespawnBtn != null) deathRespawnBtn.setOnClickListener(doRespawn);
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
            addLine(new ChatMessage("system", null, "Joining " + dest + " (SRV like PC if no port). Queue sends "
                    + ChatServerPrefs.queueCmd(activity) + " — long-press Queue to change (simpcraft is /queue simpcraft)."));
            addLine(new ChatMessage("system", null, "Menu or Back returns to the launcher."));
            setStatus("Joining " + dest + "…");
        }
        setCover(true);

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
                || low.contains("timed out") || low.contains("unable to connect")) {
            setStatus("Join failed");
        }
        if (low.contains("unknown host")) {
            setStatus("Unknown host");
            String saved = ChatServerPrefs.host(activity);
            addLine(new ChatMessage("system", null,
                    "Unknown host: the proxy tried a short name (e.g. simpcraft) that is not public DNS. "
                            + "Menu, then Connect again to "
                            + (saved.isEmpty() ? "the lobby address you typed" : saved)
                            + ". Do not put the queue name in the address box."));
        }
        if (low.contains("resource pack") || low.contains("resourcepack")) {
            setStatus("Resource pack");
        }
        if (low.contains("joined the game") || low.contains("logged in")
                || (low.contains("multiplayer") && low.contains("joined"))) {
            markConnected(line);
        }
        maybeProgress(line);
        handleDeathLine(line);
    }

    private void markConnected(String text) {
        String t = text == null ? "" : text.toLowerCase();
        if (respawn.isDead()) {
            setStatus("Dead · tap Respawn");
            return;
        }
        if (t.contains("queue")) setStatus("In queue");
        else setStatus("Connected");
    }

    private void maybeProgress(String line) {
        if (line == null) return;
        String low = line.toLowerCase();
        if (!looksProgress(low)) return;
        String clip = line.length() > 240 ? line.substring(0, 240) : line;
        if (clip.equals(lastProgress)) return;
        lastProgress = clip;
        addLine(new ChatMessage("system", null, clip));
    }

    private static boolean looksProgress(String low) {
        return low.contains("connecting") || low.contains("connected to")
                || low.contains("logged in")
                || low.contains("resource pack") || low.contains("resourcepack")
                || low.contains("downloading") || low.contains("disconnect")
                || low.contains("timed out") || low.contains("kicked")
                || low.contains("queue") || low.contains("authenticat")
                || low.contains("failed to connect") || low.contains("connection refused")
                || low.contains("unknown host") || low.contains("unable to connect")
                || low.contains("lost connection") || low.contains("connection lost")
                || low.contains("server brand") || low.contains("transferring");
    }

    private void sendQueueCmd() {
        String cmd = ChatServerPrefs.queueCmd(activity);
        if (!sender.send(cmd)) return;
        addLine(new ChatMessage("you", "You", cmd));
        setStatus("Sent " + cmd);
        input.postDelayed(this::showKeyboard, 400);
    }

    private void editQueueCmd() {
        final EditText box = new EditText(activity);
        box.setText(ChatServerPrefs.queueCmd(activity));
        box.setHint("/queue simpcraft");
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
        respawn.onDeath();
    }

    private void showDeathBanner(String message) {
        boolean wasHidden = deathBar == null || deathBar.getVisibility() != View.VISIBLE;
        setStatus("Dead · tap Respawn");
        if (deathText != null) deathText.setText(message);
        if (deathBar != null) deathBar.setVisibility(View.VISIBLE);
        if (wasHidden) {
            addLine(new ChatMessage("system", null, message));
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        } else if (deathText != null) {
            addLine(new ChatMessage("system", null, message));
        }
        if (stickBottom && scroller != null) {
            scroller.post(() -> scroller.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void hideDeathBanner() {
        if (deathBar != null) deathBar.setVisibility(View.GONE);
    }

    private void clickRespawn() {
        respawn.respawnNow();
        setStatus("Respawning…");
        addLine(new ChatMessage("system", null, "Respawn sent."));
        respawn.onAlive();
        hideDeathBanner();
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
        input.postDelayed(this::showKeyboard, 400);
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

    private void bindKeyboardShare() {
        try {
            activity.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        } catch (Throwable ignored) {}
        View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : root;
        ViewCompat.setOnApplyWindowInsetsListener(decor, (v, insets) -> {
            applyImeInsets(insets);
            return insets;
        });
        if (input != null) {
            input.setOnFocusChangeListener((v, has) -> {
                if (has) showKeyboard();
            });
            input.setOnClickListener(v -> showKeyboard());
        }
        root.post(() -> {
            ViewCompat.requestApplyInsets(decor);
            showKeyboard();
        });
    }

    private void applyImeInsets(WindowInsetsCompat insets) {
        if (root == null) return;
        Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
        Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
        int top = Math.max(sys.top, dp(12));
        int bottom = Math.max(ime.bottom, sys.bottom);
        root.setPadding(sys.left, top, sys.right, bottom);
        if (stickBottom && scroller != null) {
            scroller.post(() -> scroller.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void showKeyboard() {
        if (input == null) return;
        input.requestFocus();
        try {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        } catch (Throwable ignored) {}
    }

    private int dp(int dps) {
        float d = activity.getResources().getDisplayMetrics().density;
        return Math.round(dps * d);
    }

    private void setCover(boolean on) {
        cover = on;
        if (on) root.setBackgroundResource(R.drawable.mcmessenger_app_bg);
        else root.setBackgroundColor(Color.TRANSPARENT);
        root.setClickable(on);
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

    /** Phone Back from GameActivity. True if the overlay handled it. */
    public static boolean onSystemBack() {
        ChatOverlayController c = sActive != null ? sActive.get() : null;
        if (c == null) return false;
        c.leaveToMenu();
        return true;
    }

    private void leaveToMenu() {
        if (activity.isFinishing()) return;
        activity.runOnUiThread(() -> {
            if (leaveDialogOpen || activity.isFinishing()) return;
            leaveDialogOpen = true;
            new AlertDialog.Builder(activity)
                    .setTitle("Leave server")
                    .setMessage("Disconnect and return to the McMessenger menu?")
                    .setNegativeButton(android.R.string.cancel, (d, w) -> leaveDialogOpen = false)
                    .setPositiveButton("Menu", (d, w) -> exitToLauncher())
                    .setOnCancelListener(d -> leaveDialogOpen = false)
                    .setOnDismissListener(d -> {
                        if (!activity.isFinishing()) leaveDialogOpen = false;
                    })
                    .show();
        });
    }

    private void exitToLauncher() {
        try {
            if (tailer != null) tailer.stop();
            Tools.restartLauncherActivity(activity);
            Tools.fullyExit();
        } catch (Throwable t) {
            try {
                activity.finish();
            } catch (Throwable ignored) {}
        }
    }
}
