package net.kdt.pojavlaunch.chatoverlay;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
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
    private String lastTraffic = "";
    private long lastTrafficAt;
    private int lastImeBottom;
    private final Handler imeKeep = new Handler(Looper.getMainLooper());
    private final Runnable keepIme = new Runnable() {
        @Override public void run() {
            if (root == null || !root.isAttachedToWindow()) return;
            showKeyboard();
            layoutAboveKeyboard();
            imeKeep.postDelayed(this, 1500);
        }
    };

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
            @Override public void onViewAttachedToWindow(View v) {
                imeKeep.removeCallbacks(keepIme);
                imeKeep.post(keepIme);
            }
            @Override public void onViewDetachedFromWindow(View v) {
                imeKeep.removeCallbacks(keepIme);
                if (tailer != null) tailer.stop();
            }
        });
        imeKeep.post(keepIme);
    }

    private void onLogLine(String line) {
        ChatMessage msg = parser.parseLine(line);
        if (msg != null) {
            if ("join".equals(msg.kind) || "leave".equals(msg.kind)) {
                addTraffic(msg);
                if ("join".equals(msg.kind) && isSelf(msg.username)) markConnected(msg.text);
                return;
            }
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
        ChatMessage traffic = ChatJoinLeave.parse(line);
        if (traffic != null) {
            addTraffic(traffic);
            if ("join".equals(traffic.kind) && isSelf(traffic.username)) markConnected(line);
            return;
        }
        if (low.contains("logged in")
                || (low.contains("multiplayer") && low.contains("joined"))) {
            markConnected(line);
        }
        maybeProgress(line);
        handleDeathLine(line);
    }

    private boolean isSelf(String name) {
        return name != null && !selfName.isEmpty() && selfName.equalsIgnoreCase(name);
    }

    private void addTraffic(ChatMessage msg) {
        String key = msg.kind + ":" + msg.username;
        long now = System.currentTimeMillis();
        if (key.equals(lastTraffic) && now - lastTrafficAt < 2000) return;
        lastTraffic = key;
        lastTrafficAt = now;
        addLine(msg);
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
        else if ("join".equals(msg.kind)) prefix = "→ ";
        else if ("leave".equals(msg.kind)) prefix = "← ";
        else if ("player".equals(msg.kind) && msg.username != null) prefix = msg.username + ": ";
        else prefix = "System: ";
        tv.setText(prefix + msg.text);
        if ("system".equals(msg.kind)) tv.setTextColor(Color.parseColor("#C8E090"));
        if ("you".equals(msg.kind)) tv.setTextColor(Color.parseColor("#FF8A96"));
        if ("join".equals(msg.kind)) tv.setTextColor(Color.parseColor("#9CFF7A"));
        if ("leave".equals(msg.kind)) tv.setTextColor(Color.parseColor("#A8C4A0"));
        transcript.addView(tv);
        if (stickBottom) {
            scroller.post(() -> scroller.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void bindKeyboardShare() {
        try {
            activity.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        } catch (Throwable ignored) {}
        View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : root;
        ViewCompat.setOnApplyWindowInsetsListener(decor, (v, insets) -> {
            lastImeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (root != null) {
                root.setPadding(sys.left, Math.max(sys.top, dp(8)), sys.right, 0);
            }
            layoutAboveKeyboard();
            return insets;
        });
        View content = activity.findViewById(android.R.id.content);
        if (content != null) {
            content.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> layoutAboveKeyboard());
        }
        if (input != null) {
            input.setOnFocusChangeListener((v, has) -> showKeyboard());
            input.setOnClickListener(v -> showKeyboard());
        }
        root.post(() -> {
            layoutAboveKeyboard();
            showKeyboard();
        });
    }

    /** Chat panel is the top half; keyboard is forced into the bottom half. */
    private void layoutAboveKeyboard() {
        if (root == null) return;
        View content = activity.findViewById(android.R.id.content);
        int screenH = 0;
        if (content != null) screenH = content.getHeight();
        if (screenH < dp(200)) {
            screenH = activity.getResources().getDisplayMetrics().heightPixels;
        }
        int reserve = lastImeBottom > dp(80) ? lastImeBottom : Math.round(screenH * 0.45f);
        reserve = Math.max(reserve, dp(260));
        reserve = Math.min(reserve, screenH / 2);
        int chatH = Math.max(dp(220), screenH - reserve);
        ViewGroup.LayoutParams lp = root.getLayoutParams();
        if (!(lp instanceof FrameLayout.LayoutParams)) {
            lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, chatH);
        }
        FrameLayout.LayoutParams fl = (FrameLayout.LayoutParams) lp;
        fl.gravity = Gravity.TOP;
        fl.width = ViewGroup.LayoutParams.MATCH_PARENT;
        fl.height = chatH;
        root.setLayoutParams(fl);
        if (stickBottom && scroller != null) {
            scroller.post(() -> scroller.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void showKeyboard() {
        if (input == null) return;
        input.requestFocus();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.view.WindowInsetsController c = activity.getWindow().getInsetsController();
                if (c != null) c.show(WindowInsets.Type.ime());
            }
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_FORCED);
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
