package net.kdt.pojavlaunch.chatoverlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.EditText;

import git.artdeell.mojo.R;

/** Last Java server the user typed. Survives app restarts. */
public final class ChatServerPrefs {
    public static final String PREFS = "mcmessenger";

    private ChatServerPrefs() {}

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, 0);
    }

    public static String host(Context c) {
        return prefs(c).getString("server_host", "");
    }

    public static int port(Context c) {
        Integer p = explicitPort(c);
        return p == null ? 25565 : p;
    }

    /** Null when the user left port blank — same as PC (SRV / default). */
    public static Integer explicitPort(Context c) {
        String raw = prefs(c).getString("server_port", "");
        if (raw == null) return null;
        raw = raw.trim();
        if (raw.isEmpty()) return null;
        try {
            int p = Integer.parseInt(raw);
            if (p < 1 || p > 65535) return null;
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    public static String queueCmd(Context c) {
        String q = prefs(c).getString("queue_cmd", "/queue simpcraft");
        if (q == null || q.trim().isEmpty()) return "/queue simpcraft";
        return q.trim();
    }

    public static void saveQueueCmd(Context c, String cmd) {
        if (cmd == null || cmd.trim().isEmpty()) return;
        prefs(c).edit().putString("queue_cmd", cmd.trim()).apply();
    }

    public static void save(Context c, String hostRaw, String portRaw) {
        String host = hostRaw == null ? "" : hostRaw.trim();
        String port = portRaw == null ? "" : portRaw.trim();
        if (host.contains(":") && !host.startsWith("[")) {
            int colon = host.lastIndexOf(':');
            String maybePort = host.substring(colon + 1);
            if (maybePort.matches("\\d+")) {
                port = maybePort;
                host = host.substring(0, colon);
            }
        }
        prefs(c).edit().putString("server_host", host).putString("server_port", port).apply();
    }

    public static boolean saveFrom(View root) {
        EditText h = root.findViewById(R.id.mc_server_host);
        EditText p = root.findViewById(R.id.mc_server_port);
        if (h == null) return !host(root.getContext()).isEmpty();
        save(root.getContext(), h.getText().toString(), p != null ? p.getText().toString() : "25565");
        return !host(root.getContext()).isEmpty();
    }

    public static void bind(View root) {
        EditText h = root.findViewById(R.id.mc_server_host);
        EditText p = root.findViewById(R.id.mc_server_port);
        Context c = root.getContext();
        if (h != null && (h.getText() == null || h.getText().length() == 0)) {
            h.setText(host(c));
        }
        if (p != null && (p.getText() == null || p.getText().length() == 0)) {
            Integer ep = explicitPort(c);
            p.setText(ep == null ? "" : String.valueOf(ep));
        }
    }
}
