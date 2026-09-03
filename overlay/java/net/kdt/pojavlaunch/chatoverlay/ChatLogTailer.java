package net.kdt.pojavlaunch.chatoverlay;

import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Follows Mojo's {@code latestlog.txt} without stealing LoggerView's listener.
 */
public final class ChatLogTailer {
    public interface Listener {
        void onLine(String line);
    }

    private final File file;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private Thread thread;

    public ChatLogTailer(File file, Listener listener) {
        this.file = file;
        this.listener = listener;
    }

    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "mc-chat-log-tail");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    private void loop() {
        long pos = 0;
        while (running) {
            try {
                if (!file.exists()) {
                    Thread.sleep(400);
                    continue;
                }
                try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                    if (raf.length() < pos) pos = 0;
                    raf.seek(pos);
                    String line;
                    while (running && (line = readLine(raf)) != null) {
                        final String emit = line;
                        main.post(() -> listener.onLine(emit));
                    }
                    pos = raf.getFilePointer();
                }
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ignored) {
                try { Thread.sleep(500); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static String readLine(RandomAccessFile raf) throws Exception {
        StringBuilder sb = new StringBuilder();
        int b;
        boolean any = false;
        while ((b = raf.read()) != -1) {
            any = true;
            if (b == '\n') break;
            if (b != '\r') sb.append((char) b);
        }
        if (!any) return null;
        // latestlog is mostly ASCII / UTF-8 BMP; re-decode the buffer as UTF-8
        return new String(sb.toString().getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }
}
