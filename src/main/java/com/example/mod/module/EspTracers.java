package com.example.mod.module;

import com.example.mod.LimitlessClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;

public final class EspTracers {
    public record Target(Vec3d position, int color, double distanceSq) { }

    private static final double NEAR = 0.2;

    private EspTracers() { }

    public static Vec3d origin(MinecraftClient client, float tickProgress) {
        if (LimitlessClient.FREECAM != null && LimitlessClient.FREECAM.isActive())
            return client.gameRenderer.getCamera().getCameraPos();
        return client.player.getLerpedPos(tickProgress);
    }

    public static void draw(DrawContext context, MinecraftClient client, List<Target> candidates, int limit) {
        if (client.currentScreen != null || client.player == null || client.world == null
                || candidates.isEmpty() || limit <= 0) return;

        var camera = client.gameRenderer.getCamera();
        Vec3d eye = camera.getCameraPos();
        Comparator<Target> byCameraDistance = Comparator.comparingDouble(
                target -> target.position().squaredDistanceTo(eye));
        PriorityQueue<Target> nearest = new PriorityQueue<>(byCameraDistance.reversed());
        for (Target target : candidates) {
            if (nearest.size() < limit) nearest.add(target);
            else if (byCameraDistance.compare(target, nearest.peek()) < 0) {
                nearest.remove();
                nearest.add(target);
            }
        }
        List<Target> sorted = new ArrayList<>(nearest);
        sorted.sort(byCameraDistance);
        for (Target target : sorted) drawLine(context, client, target);
    }

    private static void drawLine(DrawContext context, MinecraftClient client, Target target) {
        var camera = client.gameRenderer.getCamera();
        Vec3d relative = target.position().subtract(camera.getCameraPos());
        double depth = relative.dotProduct(new Vec3d(camera.getHorizontalPlane()));
        int width = context.getScaledWindowWidth(), height = context.getScaledWindowHeight();
        double x, y;
        if (depth > NEAR) {
            Vec3d projected = client.gameRenderer.project(target.position());
            if (!Double.isFinite(projected.x) || !Double.isFinite(projected.y)) return;
            x = projected.x * width / 2.0;
            y = -projected.y * height / 2.0;
        } else {
            x = relative.dotProduct(new Vec3d(camera.getDiagonalPlane()));
            y = -relative.dotProduct(new Vec3d(camera.getVerticalPlane()));
            if (Math.abs(x) + Math.abs(y) < 1.0e-5) y = 1;
        }
        if (!Double.isFinite(x) || !Double.isFinite(y)) return;
        double edgeX = Math.max(1, width / 2.0);
        double edgeY = Math.max(1, height / 2.0);
        double ratio = Math.max(Math.abs(x) / edgeX, Math.abs(y) / edgeY);
        if (ratio > 1) { x /= ratio; y /= ratio; }
        int length = (int) Math.round(Math.hypot(x, y));
        if (length < 5) return;
        int color = target.color();
        color = (Math.min(color >>> 24, 140) << 24) | (color & 0x00ffffff);
        float lineScale = height / (float) Math.max(1, client.getWindow().getFramebufferHeight()) * 0.5f;

        context.getMatrices().pushMatrix();
        try {
            context.getMatrices().translate(width / 2.0f, height / 2.0f);
            context.getMatrices().rotate((float) Math.atan2(y, x));
            context.getMatrices().scale(1.0f, lineScale);
            context.fill(4, -1, length, 1, color);
        } finally {
            context.getMatrices().popMatrix();
        }
    }
}
