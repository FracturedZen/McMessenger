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
        try {
            int p = Integer.parseInt(prefs(c).getString("server_port", "25565").trim());
            if (p < 1 || p > 65535) return 25565;
            return p;
        } catch (Exception e) {
            return 25565;
        }
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
        if (port.isEmpty()) port = "25565";
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
            p.setText(String.valueOf(port(c)));
        }
    }
}
