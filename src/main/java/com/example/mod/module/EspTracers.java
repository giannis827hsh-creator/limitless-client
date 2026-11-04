package com.example.mod.module;

import com.example.mod.LimitlessClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/** World-space, through-wall tracers shared by the ESP modules. */
public final class EspTracers {
    public record Target(Vec3d position, int color, double distanceSq) { }

    private static final List<Target> pending = new ArrayList<>();
    private static Object pendingWorld;
    private static final int MAX_FRAME_TARGETS = 192;
    private static final int EDGE_SECTORS = 24;
    private static final double NEAR = 0.2;
    private static final float LINE_WIDTH_PIXELS = 1.0f;
    private static int occupiedEdges;

    private EspTracers() { }

    public static Vec3d origin(MinecraftClient client, float tickProgress) {
        if (LimitlessClient.FREECAM != null && LimitlessClient.FREECAM.isActive())
            return client.gameRenderer.getCamera().getCameraPos();
        return client.player.getLerpedPos(tickProgress);
    }

    public static void beginHudFrame(MinecraftClient client) {
        pending.clear();
        pendingWorld = client.world;
        occupiedEdges = 0;
    }

    public static void draw(DrawContext context, MinecraftClient client, List<Target> candidates, int limit) {
        if (client.currentScreen != null || client.player == null || client.world == null
                || candidates.isEmpty() || limit <= 0) return;
        if (pendingWorld != client.world) {
            pending.clear();
            pendingWorld = client.world;
        }
        Vec3d eye = client.gameRenderer.getCamera().getCameraPos();
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
        for (Target target : sorted) {
            if (pending.size() >= MAX_FRAME_TARGETS) break;
            pending.add(target);
            drawOffscreenDirection(context, client, target);
        }
    }

    private static void drawOffscreenDirection(DrawContext context, MinecraftClient client, Target target) {
        var camera = client.gameRenderer.getCamera();
        Vec3d relative = target.position().subtract(camera.getCameraPos());
        Vec3d forward = new Vec3d(camera.getHorizontalPlane());
        double depth = relative.dotProduct(forward);
        int width = context.getScaledWindowWidth(), height = context.getScaledWindowHeight();
        double x, y;
        if (depth > NEAR) {
            Vec3d projected = client.gameRenderer.project(target.position());
            if (!Double.isFinite(projected.x) || !Double.isFinite(projected.y)) return;
            if (Math.abs(projected.x) <= 0.96 && Math.abs(projected.y) <= 0.96) return;
            x = projected.x * width / 2.0;
            y = -projected.y * height / 2.0;
        } else {
            // A 3D line behind the camera has no visible segment. Project its
            // direction into a HUD ray so it still has a usable tracer.
            x = relative.dotProduct(new Vec3d(camera.getDiagonalPlane()));
            y = -relative.dotProduct(new Vec3d(camera.getVerticalPlane()));
            if (Math.abs(x) + Math.abs(y) < 1.0e-5) y = 1;
        }
        if (!Double.isFinite(x) || !Double.isFinite(y)) return;
        double edgeX = Math.max(1, width / 2.0 - 14);
        double edgeY = Math.max(1, height / 2.0 - 14);
        double ratio = Math.max(Math.abs(x) / edgeX, Math.abs(y) / edgeY);
        if (ratio < 1.0e-6) return;
        x /= ratio;
        y /= ratio;
        double angle = Math.atan2(y, x);
        int sector = Math.floorMod((int) Math.floor((angle + Math.PI) * EDGE_SECTORS
                / (Math.PI * 2.0)), EDGE_SECTORS);
        int bit = 1 << sector;
        if ((occupiedEdges & bit) != 0) return;
        occupiedEdges |= bit;

        int color = target.color();
        color = (Math.min(color >>> 24, 190) << 24) | (color & 0x00ffffff);
        context.getMatrices().pushMatrix();
        try {
            context.getMatrices().translate((float) (width / 2.0 + x), (float) (height / 2.0 + y));
            context.getMatrices().rotate((float) angle);
            context.getMatrices().scale(1.0f, 0.35f);
            int length = (int) Math.ceil(Math.hypot(x, y));
            context.fill(4 - length, 0, -2, 1, color);
        } finally {
            context.getMatrices().popMatrix();
        }
    }

    public static void renderWorld(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        List<Target> targets = List.copyOf(pending);
        if (pendingWorld != client.world || client.world == null || client.player == null
                || client.currentScreen != null || targets.isEmpty()) return;
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) return;

        Vec3d eye = context.worldState().cameraRenderState.pos;
        var camera = client.gameRenderer.getCamera();
        Vec3d forward = new Vec3d(camera.getHorizontalPlane()).normalize();
        Vec3d origin = forward.multiply(NEAR);
        double fov = client.options.getFov().getValue();
        double pixelPerDepth = 2.0 * Math.tan(Math.toRadians(fov) * 0.5)
                / Math.max(1, client.getWindow().getFramebufferHeight());
        VertexConsumer quads = consumers.getBuffer(EspRenderLayers.FILLED_THROUGH_WALLS);

        matrices.push();
        try {
            MatrixStack.Entry matrix = matrices.peek();
            for (Target target : targets) {
                Vec3d delta = target.position().subtract(eye);
                double depth = delta.dotProduct(forward);
                // The near plane clips targets behind the camera. Drawing them
                // as HUD lines made a giant fan across third-person view.
                if (depth <= NEAR || !Double.isFinite(depth)) continue;
                Vec3d side = forward.crossProduct(delta);
                double sideLength = side.length();
                if (sideLength < 1.0e-8) {
                    side = new Vec3d(camera.getDiagonalPlane());
                    sideLength = side.length();
                }
                if (sideLength < 1.0e-8) continue;
                side = side.multiply(1.0 / sideLength);
                Vec3d nearOffset = side.multiply(NEAR * pixelPerDepth * LINE_WIDTH_PIXELS * 0.5);
                Vec3d farOffset = side.multiply(depth * pixelPerDepth * LINE_WIDTH_PIXELS * 0.5);
                int color = target.color();
                color = (Math.min(color >>> 24, 160) << 24) | (color & 0x00ffffff);
                vertex(quads, matrix, origin.add(nearOffset), color);
                vertex(quads, matrix, delta.add(farOffset), color);
                vertex(quads, matrix, delta.subtract(farOffset), color);
                vertex(quads, matrix, origin.subtract(nearOffset), color);
            }
        } finally {
            matrices.pop();
        }
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry matrix, Vec3d pos, int color) {
        consumer.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z).color(color);
    }
}
