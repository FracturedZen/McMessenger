package net.kdt.pojavlaunch.chatoverlay;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Same DNS SRV lookup the PC multiplayer screen does ({@code _minecraft._tcp.host}).
 * Command-line {@code --server}/{@code --port} skip this, which is why a hostname
 * that works on PC can hang here if we always pass 25565.
 */
public final class McSrvLookup {
    private static final String TAG = "McMessenger";

    public static final class Result {
        public final String host;
        public final int port;
        public Result(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    private McSrvLookup() {}

    public static Result resolve(String host) {
        if (host == null || host.isEmpty()) return null;
        final Result[] box = new Result[1];
        Thread t = new Thread(() -> {
            try {
                box[0] = query(host);
            } catch (Exception e) {
                Log.w(TAG, "SRV lookup failed for " + host, e);
            }
        }, "mcmessenger-srv");
        t.start();
        try {
            t.join(4000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return box[0];
    }

    private static Result query(String host) throws Exception {
        String q = "_minecraft._tcp." + host;
        String url = "https://cloudflare-dns.com/dns-query?name="
                + URLEncoder.encode(q, "UTF-8") + "&type=SRV";
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(2500);
        c.setReadTimeout(2500);
        c.setRequestProperty("Accept", "application/dns-json");
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in == null) return null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[2048];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        c.disconnect();
        JSONObject json = new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
        JSONArray ans = json.optJSONArray("Answer");
        if (ans == null) return null;
        int bestPri = Integer.MAX_VALUE;
        Result best = null;
        for (int i = 0; i < ans.length(); i++) {
            JSONObject rec = ans.getJSONObject(i);
            if (rec.optInt("type") != 33) continue;
            String data = rec.optString("data", "").trim();
            String[] p = data.split("\\s+");
            if (p.length < 4) continue;
            int pri = Integer.parseInt(p[0]);
            int port = Integer.parseInt(p[2]);
            String target = p[3];
            if (target.endsWith(".")) target = target.substring(0, target.length() - 1);
            if (target.isEmpty()) continue;
            if (pri < bestPri) {
                bestPri = pri;
                best = new Result(target, port);
            }
        }
        if (best != null) Log.i(TAG, "SRV " + host + " → " + best.host + ":" + best.port);
        return best;
    }
}
