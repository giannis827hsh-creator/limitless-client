package com.example.mod.module.hud;

/** Matches timed ping echoes to the exact connection that sent them. Replies may arrive on Netty. */
public final class ServerLatency {
    private static final long INTERVAL = 3_000_000_000L;
    private static final long TIMEOUT = 5_000_000_000L;
    private static final long FRESHNESS = 15_000_000_000L;
    private Object connection;
    private boolean waiting, sampled;
    private long pendingId, pendingSince, nextProbeAt, sampledAt;
    private double milliseconds;

    public synchronized void reset() {
        connection = null; waiting = false; sampled = false;
        pendingId = 0; pendingSince = 0; nextProbeAt = 0; sampledAt = 0; milliseconds = 0;
    }

    public synchronized boolean begin(Object connection, long id, long now) {
        if (this.connection != connection) { reset(); this.connection = connection; }
        if (waiting && now - pendingSince < TIMEOUT) return false;
        if ((waiting || sampled) && now < nextProbeAt) return false;
        pendingId = id; pendingSince = now; nextProbeAt = now + INTERVAL; waiting = true;
        return true;
    }

    public synchronized void accept(Object connection, long id, long arrivalNanos) {
        if (this.connection != connection || !waiting || id != pendingId) return;
        long elapsed = arrivalNanos - pendingSince;
        if (elapsed < 0 || elapsed > TIMEOUT) return;
        double sample = Math.max(1, elapsed / 1_000_000.0);
        milliseconds = sampled ? milliseconds * 0.65 + sample * 0.35 : sample;
        sampled = true; waiting = false; sampledAt = arrivalNanos;
    }

    public synchronized int milliseconds(Object connection, long now) {
        if (this.connection != connection || !sampled || now - sampledAt > FRESHNESS) return -1;
        return Math.max(1, (int) Math.round(milliseconds));
    }
}
