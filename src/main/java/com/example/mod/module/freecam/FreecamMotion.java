package com.example.mod.module.freecam;

/** Render-time camera math with no reference to a player entity or network connection. */
public final class FreecamMotion {
    private double x, y, z;
    private float yaw, pitch;

    public void reset(double x, double y, double z, float yaw, float pitch) {
        this.x = x; this.y = y; this.z = z;
        this.yaw = wrap(yaw); this.pitch = clampPitch(pitch);
    }
    /** Mouse deltas have already passed through Minecraft's sensitivity and inversion options. */
    public void look(double dx, double dy) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy)) return;
        yaw = wrap(yaw + (float) (dx * 0.15));
        pitch = clampPitch(pitch + (float) (dy * 0.15));
    }
    /** Positive right is D; positive vertical is Space. Diagonals have the same total speed. */
    public void move(double forward, double right, double vertical, double blocksPerSecond,
                     double verticalMultiplier, double seconds) {
        if (!Double.isFinite(seconds) || !Double.isFinite(blocksPerSecond) || blocksPerSecond <= 0) return;
        double length = Math.sqrt(forward * forward + right * right + vertical * vertical);
        if (!Double.isFinite(length) || length == 0) return;
        double travel = blocksPerSecond * Math.max(0, Math.min(0.1, seconds)) / Math.max(1, length);
        double angle = Math.toRadians(yaw), sin = Math.sin(angle), cos = Math.cos(angle);
        x += (-sin * forward - cos * right) * travel;
        y += vertical * travel * verticalMultiplier;
        z += (cos * forward - sin * right) * travel;
    }
    private static float wrap(float angle) {
        float wrapped = angle % 360;
        return wrapped >= 180 ? wrapped - 360 : wrapped < -180 ? wrapped + 360 : wrapped;
    }
    private static float clampPitch(float value) { return Math.max(-90, Math.min(90, value)); }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
}
