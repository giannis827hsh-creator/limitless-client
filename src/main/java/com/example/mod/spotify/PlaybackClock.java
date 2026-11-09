
package com.example.mod.spotify;

import com.example.mod.spotify.NowPlaying;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class PlaybackClock {
    static final long SNAP_MS = 1200L;
    static final long CATCHUP_MS = 1500L;
    private NowPlaying sample;
    private boolean running;
    private long durationMs;
    private double rate = 1.0;
    private double base;
    private long baseAt;
    private double correction;

    public void update(NowPlaying now, long nowMs) {
        if (now == this.sample) {
            return;
        }
        NowPlaying previous = this.sample;
        this.sample = now;
        if (now == null || !now.hasTimeline()) {
            this.running = false;
            this.durationMs = 0L;
            this.base = 0.0;
            this.correction = 0.0;
            this.baseAt = nowMs;
            return;
        }
        double shown = this.position(nowMs);
        double target = now.positionAt(nowMs);
        boolean continuous = previous != null && previous.hasTimeline() && previous.sameTrack(now) && this.running && now.playing() && Math.abs(target - shown) < 1200.0 * Math.min(1.0, now.rate());
        this.base = continuous ? shown : target;
        this.correction = continuous ? target - shown : 0.0;
        this.baseAt = nowMs;
        this.running = now.playing();
        this.durationMs = now.durationMs();
        this.rate = now.rate();
    }

    public long positionAt(long nowMs) {
        if (this.durationMs <= 0L) {
            return 0L;
        }
        return Math.max(0L, Math.min(this.durationMs, Math.round(this.position(nowMs))));
    }

    private double position(long nowMs) {
        if (this.durationMs <= 0L) {
            return 0.0;
        }
        if (!this.running) {
            return this.base;
        }
        double elapsed = Math.max(0L, nowMs - this.baseAt);
        return this.base + elapsed * this.rate + this.correction * Math.min(1.0, elapsed / 1500.0);
    }
}
