package com.fracturedzen.mcmessenger.agent;

import java.lang.instrument.Instrumentation;

/**
 * Runs inside the Minecraft JVM launched by Mojo, not inside Android ART.
 *
 * Drops typical chunk/light frames after a login/switch grace. A quiet gap
 * then a large burst (Velocity /queue) starts a new grace so Join Game is
 * kept. Does not transform JDK classes.
 */
public final class AgentMain {
    public static void premain(String args, Instrumentation inst) {
        try {
            PlayDropper.start();
            inst.addTransformer(new FireChannelReadTransformer(), false);
            System.out.println("[mcmessenger] javaagent installed");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
