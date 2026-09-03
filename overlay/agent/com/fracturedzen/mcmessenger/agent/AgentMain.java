package com.fracturedzen.mcmessenger.agent;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

/**
 * Runs inside the Minecraft JVM launched by Mojo, not inside Android ART.
 *
 * Large inbound frames are no longer dropped: Velocity lobby→server sends a
 * new Join Game / registry that is huge, and dropping it kicks you. The
 * overlay still shrinks the GL surface. Respawn and DNS remap stay.
 */
public final class AgentMain {
    public static void premain(String args, Instrumentation inst) {
        boolean boot = false;
        try {
            File jar = new File(AgentMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            inst.appendToBootstrapClassLoaderSearch(new JarFile(jar));
            boot = true;
        } catch (Throwable t) {
            System.out.println("[mcmessenger] bootstrap append failed: " + t);
        }
        PlayDropper.start();
        inst.addTransformer(new FireChannelReadTransformer(), true);
        if (boot) {
            inst.addTransformer(new HostMapTransformer(), true);
            try {
                inst.retransformClasses(Class.forName("java.net.InetAddress"));
                System.out.println("[mcmessenger] InetAddress host remap installed");
            } catch (Throwable t) {
                System.out.println("[mcmessenger] InetAddress retransform skipped: " + t);
            }
        }
        System.out.println("[mcmessenger] javaagent installed — lobby/server switch packets kept");
    }
}
