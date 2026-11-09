package com.example.mod.module;

import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.ColorSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.ArrayList;
import java.util.List;

public final class PearlTrajectory extends Module {
    private final BooleanSetting throughWalls = addSetting(new BooleanSetting("Through walls", false));
    private final ColorSetting color = addSetting(new ColorSetting("Path color", 0xFFAA79FF));

    public PearlTrajectory() {
        super("Pearl Trajectory", false, Category.VISUALS);
        throughWalls.describe("Keep the predicted pearl path visible behind blocks.");
        color.describe("Color of the predicted path and landing marker.");
    }

    public void renderWorld(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || client.player == null || client.world == null
                || (!client.player.getMainHandStack().isOf(Items.ENDER_PEARL)
                && !client.player.getOffHandStack().isOf(Items.ENDER_PEARL))) return;
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) return;

        List<Vec3d> points = predict(client);
        if (points.size() < 2) return;
        Vec3d camera = context.worldState().cameraRenderState.pos;
        VertexConsumer quads = consumers.getBuffer(throughWalls.get()
                ? EspRenderLayers.FILLED_THROUGH_WALLS : EspRenderLayers.TERRAIN_HIGHLIGHT);
        matrices.push();
        try {
            matrices.translate(-camera.x, -camera.y, -camera.z);
            MatrixStack.Entry matrix = matrices.peek();
            // The spawn-to-first-tick segment crosses the camera near plane in first person.
            for (int i = 2; i < points.size(); i++)
                segment(quads, matrix, camera, points.get(i - 1), points.get(i), color.get(), 0.006);
            Vec3d end = points.getLast();
            segment(quads, matrix, camera, end.add(-0.22, 0.03, 0), end.add(0.22, 0.03, 0), color.get(), 0.025);
            segment(quads, matrix, camera, end.add(0, 0.03, -0.22), end.add(0, 0.03, 0.22), color.get(), 0.025);
        } finally { matrices.pop(); }
    }

    private List<Vec3d> predict(MinecraftClient client) {
        Vec3d direction = client.player.getRotationVec(1.0f).normalize();
        Vec3d position = client.player.getEyePos().subtract(0, 0.1, 0);
        Vec3d velocity = direction.multiply(1.5).add(client.player.getVelocity());
        List<Vec3d> points = new ArrayList<>();
        points.add(position);
        for (int tick = 0; tick < 120; tick++) {
            Vec3d next = position.add(velocity);
            HitResult hit = client.world.raycast(new RaycastContext(position, next,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, client.player));
            if (hit.getType() != HitResult.Type.MISS) {
                points.add(hit.getPos());
                break;
            }
            points.add(next);
            position = next;
            velocity = velocity.multiply(0.99).add(0, -0.03, 0);
            if (position.y < client.world.getBottomY()) break;
        }
        return points;
    }

    private static void segment(VertexConsumer quads, MatrixStack.Entry matrix, Vec3d camera,
                                Vec3d start, Vec3d end, int color, double width) {
        Vec3d tangent = end.subtract(start);
        if (tangent.lengthSquared() < 1.0e-8) return;
        Vec3d side = tangent.crossProduct(camera.subtract(start)).normalize().multiply(width);
        if (side.lengthSquared() < 1.0e-8) side = new Vec3d(width, 0, 0);
        vertex(quads, matrix, start.add(side), color);
        vertex(quads, matrix, end.add(side), color);
        vertex(quads, matrix, end.subtract(side), color);
        vertex(quads, matrix, start.subtract(side), color);
    }

    private static void vertex(VertexConsumer quads, MatrixStack.Entry matrix, Vec3d point, int color) {
        quads.vertex(matrix, (float) point.x, (float) point.y, (float) point.z).color(color);
    }
}
