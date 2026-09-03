package com.fracturedzen.mcmessenger.agent;

import java.lang.instrument.Instrumentation;

/**
 * Runs inside the Minecraft JVM launched by Mojo, not inside Android ART.
 *
 * Drops large inbound Netty frames after a login grace period so chunk/light
 * payloads are not decoded. Small frames (chat, keepalive, teleport) pass.
 * Does not hide the player, spoof movement, or skip keepalives.
 */
public final class AgentMain {
    public static void premain(String args, Instrumentation inst) {
        PlayDropper.start();
        inst.addTransformer(new FireChannelReadTransformer(), true);
        System.out.println("[mcmessenger] javaagent installed — will drop large inbound frames after login grace");
    }
}
