package com.fracturedzen.mcmessenger.agent;

/**
 * Velocity {@code Transfer} packets often use the registered server name
 * ({@code simpcraft}) instead of the public hostname ({@code simpcraft.com}).
 * Unqualified names are remapped to {@code -Dmcmessenger.joinHost}.
 */
public final class HostMap {
    private HostMap() {}

    public static String map(String host) {
        if (host == null || host.isEmpty()) return host;
        if (host.indexOf('.') >= 0 || host.indexOf(':') >= 0) return host;
        if (isIpv4(host)) return host;
        if ("localhost".equalsIgnoreCase(host)) return host;
        String fb = System.getProperty("mcmessenger.joinHost", "");
        if (fb == null) return host;
        fb = fb.trim();
        if (fb.isEmpty() || fb.equalsIgnoreCase(host)) return host;
        System.out.println("[mcmessenger] DNS remap " + host + " -> " + fb);
        return fb;
    }

    private static boolean isIpv4(String host) {
        int dots = 0;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c == '.') dots++;
            else if (c < '0' || c > '9') return false;
        }
        return dots == 3;
    }
}
