package com.example.mod.module;

import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.ColorSetting;
import com.example.mod.setting.IntegerSetting;
import com.example.mod.setting.NumberSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

/** Client-side entity boxes and tracers. */
public final class EspModule extends Module {
    private final BooleanSetting renderBoxes = addSetting(new BooleanSetting("Boxes", true));
    private final BooleanSetting renderTracers = addSetting(new BooleanSetting("Tracers", true));
    private final BooleanSetting playersOnly = addSetting(new BooleanSetting("Players only", false));
    private final BooleanSetting players = addSetting(new BooleanSetting("Players", true));
    private final BooleanSetting hostileMobs = addSetting(new BooleanSetting("Hostile mobs", true));
    private final BooleanSetting passiveMobs = addSetting(new BooleanSetting("Passive mobs", true));
    private final BooleanSetting includeInvisible = addSetting(new BooleanSetting("Include invisible", true));
    private final ColorSetting tracerColor = addSetting(new ColorSetting("ESP color", 0xFF26D9FF));
    private final ColorSetting hostileColor = addSetting(new ColorSetting("Hostile color", 0xFFFF6565));
    private final ColorSetting passiveColor = addSetting(new ColorSetting("Passive color", 0xFF75DB9B));
    private final NumberSetting range = addSetting(new NumberSetting("Range", 64.0, 8.0, 256.0, 8.0));
    private final IntegerSetting maxTracers = addSetting(new IntegerSetting("Max tracers", 8, 1, 32));

    public EspModule() {
        super("Entity ESP", false);
        renderBoxes.describe("Draw a box around matching living entities, visible through walls.");
        renderTracers.describe("Draw thin lines from your crosshair to matching entities visible on screen.");
        playersOnly.describe("Only highlight other players. Turn off to include mobs and animals.");
        players.describe("Highlight other players.");
        hostileMobs.describe("Highlight hostile mobs separately from passive mobs.");
        passiveMobs.describe("Highlight passive and neutral living entities.");
        includeInvisible.describe("Highlight invisible entities only when the server sends them to your client.");
        tracerColor.describe("Color for player boxes and tracers.");
        hostileColor.describe("Color for hostile mob boxes and tracers.");
        passiveColor.describe("Color for passive and neutral mob boxes and tracers.");
        range.describe("Maximum distance from your player, in blocks, for entity highlights.");
        maxTracers.describe("Draw only this many nearby targets, so a crowded area does not fill the screen with lines.");
    }

    public BooleanSetting playersOnly() {
        return playersOnly;
    }
    public BooleanSetting renderBoxes() { return renderBoxes; }
    public BooleanSetting renderTracers() { return renderTracers; }
    public ColorSetting tracerColor() { return tracerColor; }
    public NumberSetting range() { return range; }

    private int colorFor(Entity entity) {
        if (!includeInvisible.get() && entity.isInvisible()) return 0;
        if (entity instanceof PlayerEntity) return players.get() ? tracerColor.get() : 0;
        if (playersOnly.get()) return 0;
        if (entity instanceof HostileEntity) return hostileMobs.get() ? hostileColor.get() : 0;
        return passiveMobs.get() ? passiveColor.get() : 0;
    }

    /** Called from Fabric's client world drawing phase. */
    public void renderWorld(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || !renderBoxes.get() || client.world == null || client.player == null) {
            return;
        }

        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }

        Vec3d camera = context.worldState().cameraRenderState.pos;
        float tickProgress = client.getRenderTickCounter().getTickProgress(false);
        Vec3d playerPos = client.player.getLerpedPos(tickProgress);
        VertexConsumer lines = consumers.getBuffer(EspRenderLayers.LINES_THROUGH_WALLS);

        matrices.push();
        try {
            // Fabric's world consumer expects positions relative to the camera.
            matrices.translate(-camera.x, -camera.y, -camera.z);

            for (Entity entity : client.world.getEntities()) {
                if (!(entity instanceof LivingEntity living)
                        || entity == client.player
                        || !entity.isAlive()) {
                    continue;
                }
                int color = colorFor(entity);
                if (color == 0) continue;

                // The world and camera are drawn between ticks. Interpolate the
                // entity to that same render time to prevent boxes/tracers from
                // lagging or jumping when either the player or target moves.
                Vec3d interpolatedPos = living.getLerpedPos(tickProgress);
                if (interpolatedPos.squaredDistanceTo(playerPos) > range.get() * range.get()) {
                    continue;
                }
                Vec3d entityOffset = interpolatedPos.subtract(
                        new Vec3d(living.getX(), living.getY(), living.getZ())
                );
                Box box = living.getBoundingBox().offset(entityOffset).expand(0.03);
                VertexRendering.drawOutline(
                        matrices, lines, VoxelShapes.cuboid(box),
                        0.0, 0.0, 0.0, color, 2.0f
                );
            }
        } finally {
            matrices.pop();
        }
    }

    /** Collect tracer targets for the next world render pass. */
    public void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || !renderTracers.get() || client.world == null || client.player == null
                || client.currentScreen != null) {
            return;
        }

        float tickProgress = tickCounter.getTickProgress(false);
        Vec3d playerPos = EspTracers.origin(client, tickProgress);
        double maxDistanceSquared = range.get() * range.get();

        List<EspTracers.Target> targets = new ArrayList<>();
        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || entity == client.player || !entity.isAlive()) {
                continue;
            }
            int color = colorFor(entity);
            if (color == 0) continue;
            Vec3d interpolatedPos = living.getLerpedPos(tickProgress);
            if (interpolatedPos.squaredDistanceTo(playerPos) > maxDistanceSquared) continue;

            Vec3d offset = interpolatedPos.subtract(
                    new Vec3d(living.getX(), living.getY(), living.getZ()));
            Vec3d target = living.getBoundingBox().getCenter().add(offset);
            targets.add(new EspTracers.Target(target, color, interpolatedPos.squaredDistanceTo(playerPos)));
        }
        EspTracers.draw(context, client, targets, maxTracers.get());
    }
}



