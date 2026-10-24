package com.lucidclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.NetworkingBackend;

import java.net.UnknownHostException;

/**
 * Client-side ping measurement for Minecraft 1.21.11+.
 *
 * <p>Mojang removed latency from the player-list protocol in this generation
 * (the UPDATE_LATENCY player-info action no longer exists, and
 * {@code PlayerListEntry.getLatency()} is therefore always 0 in
 * multiplayer). The server measures our keepalive round-trip but never
 * reports it back.
 *
 * <p>So we measure it ourselves: every PING_INTERVAL_MS we run one vanilla
 * {@link MultiplayerServerListPinger} cycle against the current server — the
 * exact same status handshake + ping the multiplayer server list performs —
 * and cache the reported round-trip time. It is a genuine RTT to the server
 * (or its proxy) and works on any server, since it's ordinary client
 * traffic.
 *
 * <p>All methods must be called from the client thread (the pinger's
 * {@code tick()} requirement); the result values are written from the
 * netty thread via the pinger's callbacks, hence volatile.
 */
public final class PingTracker {

    private static final int INTERVAL_MS = 15_000;
    private static final int TIMEOUT_MS = 8_000;

    private static MultiplayerServerListPinger pinger;
    private static ServerInfo target;
    private static String targetAddress;
    private static boolean active;
    private static long startedAt;
    private static long nextAttemptAt;

    private static volatile int lastPing = -1;
    private static volatile long lastPingAt;

    private PingTracker() {}

    /**
     * Drive the measurement cycle. Call every frame/tick from the client
     * thread.
     *
     * @return the latest measured ping in ms, or -1 when no measurement is
     *         available (e.g. singleplayer — where 0ms is the truthful
     *         value the caller may display).
     */
    public static synchronized int tick(MinecraftClient client) {
        if (client.player == null || client.getCurrentServerEntry() == null) {
            // Not on a multiplayer server — nothing to measure.
            reset();
            return -1;
        }

        String address = client.getCurrentServerEntry().address;
        long now = System.currentTimeMillis();

        // Server changed → drop the cached value and re-measure.
        if (!address.equals(targetAddress)) {
            cleanup();
            lastPing = -1;
            targetAddress = address;
        }

        if (active) {
            pinger.tick();
            if (target.ping >= 0) {
                // Completed: the vanilla pinger wrote the RTT into ping.
                record((int) Math.min(target.ping, 9999), now);
            } else if (now - startedAt > TIMEOUT_MS) {
                cleanup();
                nextAttemptAt = now + INTERVAL_MS;
            }
        } else if (now >= nextAttemptAt) {
            startPing(address, now);
        }

        return lastPing;
    }

    /** Latest measured ping in ms, or -1 if none known. */
    public static synchronized int latest() {
        return lastPing;
    }

    private static void startPing(String address, long now) {
        target = new ServerInfo("Lucid Ping", address, ServerInfo.ServerType.OTHER);
        target.ping = -1;
        pinger = new MultiplayerServerListPinger();
        try {
            pinger.add(target,
                () -> { /* success handled in tick() via target.ping */ },
                () -> { /* failure handled via the timeout in tick() */ },
                NetworkingBackend.remote(false));
            active = true;
            startedAt = now;
        } catch (UnknownHostException e) {
            cleanup();
            nextAttemptAt = now + INTERVAL_MS;
        }
    }

    private static void record(int ping, long now) {
        lastPing = ping;
        lastPingAt = now;
        cleanup();
        nextAttemptAt = now + INTERVAL_MS;
    }

    private static void cleanup() {
        if (pinger != null) {
            try {
                pinger.cancel();
            } catch (Throwable ignored) {
            }
        }
        pinger = null;
        target = null;
        active = false;
    }

    private static void reset() {
        cleanup();
        targetAddress = null;
        lastPing = -1;
    }
}
