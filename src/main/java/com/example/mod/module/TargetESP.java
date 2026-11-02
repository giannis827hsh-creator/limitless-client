package com.example.mod.module;

import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.ColorSetting;
import com.example.mod.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

/** Separate crosshair target marker, independent of broad Entity ESP. */
public final class TargetESP extends Module {
    private final BooleanSetting playersOnly = addSetting(new BooleanSetting("Players only", false));
    private final NumberSetting range = addSetting(new NumberSetting("Range", 32, 8, 128, 8));
    private final NumberSetting linger = addSetting(new NumberSetting("Hold seconds", 1.0, 0, 5, 0.5));
    private final ColorSetting color = addSetting(new ColorSetting("Target color", 0xFFA855F7));
    private final BooleanSetting tracers = addSetting(new BooleanSetting("Tracers", true));
    private LivingEntity target;
    private ClientWorld observedWorld;
    private long lastSeen;

    public TargetESP() {
        super("Target ESP", false, Category.VISUALS);
        playersOnly.describe("Only mark players under your crosshair. Turn off to include mobs and animals.");
        range.describe("Maximum distance at which a target can be marked.");
        linger.describe("Keep the marker briefly after your crosshair leaves the target.");
        color.describe("Color of the target marker.");
        tracers.describe("Draw one line from the crosshair to your marked target.");
    }

    @Override protected void onEnable() { onWorldLeave(); }
    @Override protected void onDisable() { onWorldLeave(); }
    @Override public void onWorldLeave() { target = null; observedWorld = null; lastSeen = 0; }

    public void tick(MinecraftClient client) {
        if (!isEnabled() || client.world == null || client.player == null) {
            if (observedWorld != null) onWorldLeave();
            return;
        }
        if (observedWorld != client.world) { onWorldLeave(); observedWorld = client.world; }
        if (client.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity living
                && living != client.player && living.isAlive() && !living.isRemoved()
                && (!playersOnly.get() || living instanceof PlayerEntity)
                && living.squaredDistanceTo(client.player) <= range.get() * range.get()) {
            target = living;
            lastSeen = System.nanoTime();
        } else if (System.nanoTime() - lastSeen > (long) (linger.get() * 1_000_000_000L)) target = null;
    }

    public void renderWorld(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || client.world == null || client.player == null || client.world != observedWorld
                || target == null || target.isRemoved() || !target.isAlive()
                || target.squaredDistanceTo(client.player) > range.get() * range.get()) return;
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) return;
        Vec3d camera = context.worldState().cameraRenderState.pos;
        float progress = client.getRenderTickCounter().getTickProgress(false);
        Vec3d offset = target.getLerpedPos(progress).subtract(target.getEntityPos());
        Box box = target.getBoundingBox().offset(offset).expand(0.13);
        VertexConsumer lines = consumers.getBuffer(EspRenderLayers.LINES_THROUGH_WALLS);
        matrices.push();
        try {
            matrices.translate(-camera.x, -camera.y, -camera.z);
            VertexRendering.drawOutline(matrices, lines, VoxelShapes.cuboid(box),
                    0, 0, 0, color.get(), 2.0f);
        } finally { matrices.pop(); }
    }

    public void renderHud(DrawContext context, RenderTickCounter ticks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || !tracers.get() || target == null || target.isRemoved()
                || client.player == null || client.world != observedWorld
                || target.squaredDistanceTo(client.player) > range.get() * range.get()) return;
        Vec3d point = target.getLerpedPos(ticks.getTickProgress(false)).add(0, target.getHeight() / 2, 0);
        EspTracers.draw(context, client, java.util.List.of(new EspTracers.Target(
                point, color.get(), point.squaredDistanceTo(client.player.getEntityPos()))), 1);
    }
}
