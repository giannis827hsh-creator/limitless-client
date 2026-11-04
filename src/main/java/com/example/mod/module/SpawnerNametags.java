package com.example.mod.module;

import com.example.mod.Theme;
import com.example.mod.gui.GuiDraw;
import com.example.mod.mixin.SpawnerLogicAccessor;
import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.NumberSetting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.spawner.MobSpawnerEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

/** Labels spawners received in loaded chunks and shows their activation radius. */
public final class SpawnerNametags extends Module {
    private static final int MAX_LABELS = 48;
    private static final int MAX_CACHED = 4096;
    private static final int CHUNKS_PER_TICK = 4;
    private static final int RING_SEGMENTS = 64;
    private final NumberSetting range = addSetting(new NumberSetting("Range", 64, 8, 256, 1));
    private final BooleanSetting activationRadius = addSetting(new BooleanSetting("Activation Radius", true));
    private final Set<Long> spawners = new HashSet<>();
    private final ArrayDeque<ChunkCoordinate> scanQueue = new ArrayDeque<>();
    private Object observedWorld;
    private int centerChunkX = Integer.MIN_VALUE;
    private int centerChunkZ = Integer.MIN_VALUE;
    private int scannedRange = -1;
    private long nextRescan;

    public SpawnerNametags() {
        super("Spawner Nametags", false, Category.VISUALS);
        range.describe("Show spawners in loaded chunks within this many blocks.");
        activationRadius.describe("Show the required player range in the label and draw a ring around each spawner.");
    }

    @Override protected void onEnable() { onWorldLeave(); }
    @Override protected void onDisable() { onWorldLeave(); }
    @Override public void onWorldLeave() {
        spawners.clear();
        scanQueue.clear();
        observedWorld = null;
        centerChunkX = Integer.MIN_VALUE;
        centerChunkZ = Integer.MIN_VALUE;
        scannedRange = -1;
        nextRescan = 0;
    }

    public void tick(MinecraftClient client) {
        if (!isEnabled() || client.world == null || client.player == null || client.isPaused()) return;
        if (observedWorld != client.world) {
            onWorldLeave();
            observedWorld = client.world;
        }
        int cx = client.player.getBlockX() >> 4;
        int cz = client.player.getBlockZ() >> 4;
        int wantedRange = (int) range.get();
        if (cx != centerChunkX || cz != centerChunkZ || wantedRange != scannedRange
                || scanQueue.isEmpty() && client.world.getTime() >= nextRescan) {
            queueLoadedChunks(client, cx, cz, wantedRange);
            centerChunkX = cx;
            centerChunkZ = cz;
            scannedRange = wantedRange;
            nextRescan = client.world.getTime() + 100;
        }
        for (int n = 0; n < CHUNKS_PER_TICK && !scanQueue.isEmpty(); n++) {
            scanChunk(client, scanQueue.removeFirst());
        }
    }

    private void queueLoadedChunks(MinecraftClient client, int cx, int cz, int wantedRange) {
        scanQueue.clear();
        int radiusChunks = Math.min((wantedRange + 15) / 16, client.options.getClampedViewDistance() + 1);
        spawners.removeIf(key -> {
            BlockPos pos = BlockPos.fromLong(key);
            return Math.abs((pos.getX() >> 4) - cx) > radiusChunks
                    || Math.abs((pos.getZ() >> 4) - cz) > radiusChunks
                    || client.world.getChunkManager().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                            ChunkStatus.FULL, false) == null;
        });
        List<ChunkCoordinate> loaded = new ArrayList<>();
        for (int x = cx - radiusChunks; x <= cx + radiusChunks; x++) {
            for (int z = cz - radiusChunks; z <= cz + radiusChunks; z++) {
                if (client.world.getChunkManager().getChunk(x, z, ChunkStatus.FULL, false) != null) {
                    loaded.add(new ChunkCoordinate(x, z));
                }
            }
        }
        loaded.sort(Comparator.comparingLong(c -> {
            long dx = c.x() - cx, dz = c.z() - cz;
            return dx * dx + dz * dz;
        }));
        scanQueue.addAll(loaded);
    }

    private void scanChunk(MinecraftClient client, ChunkCoordinate coordinate) {
        WorldChunk chunk = client.world.getChunkManager().getChunk(
                coordinate.x(), coordinate.z(), ChunkStatus.FULL, false);
        spawners.removeIf(key -> {
            BlockPos pos = BlockPos.fromLong(key);
            return pos.getX() >> 4 == coordinate.x() && pos.getZ() >> 4 == coordinate.z();
        });
        if (chunk == null) return;
        for (BlockPos pos : chunk.getBlockEntityPositions()) {
            if (spawners.size() >= MAX_CACHED) break;
            if (chunk.getBlockState(pos).isOf(Blocks.SPAWNER)) spawners.add(pos.asLong());
        }
    }

    private List<Shown> inRange(MinecraftClient client) {
        if (!isEnabled() || client.world == null || client.player == null || observedWorld != client.world) {
            return List.of();
        }
        List<Shown> found = new ArrayList<>();
        double maxSq = range.get() * range.get();
        for (long key : spawners) {
            BlockPos pos = BlockPos.fromLong(key);
            double distanceSq = Vec3d.ofCenter(pos).squaredDistanceTo(
                    client.player.getX(), client.player.getY(), client.player.getZ());
            if (distanceSq > maxSq) continue;
            if (client.world.getChunkManager().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                    ChunkStatus.FULL, false) == null) continue;
            BlockEntity entity = client.world.getBlockEntity(pos);
            if (!(entity instanceof MobSpawnerBlockEntity spawner)) continue;
            int activation = ((SpawnerLogicAccessor) spawner.getLogic()).limitless$getRequiredPlayerRange();
            found.add(new Shown(pos, Math.sqrt(distanceSq), activation,
                    activation < 0 || distanceSq < (double) activation * activation));
        }
        found.sort(Comparator.comparingDouble(Shown::distance));
        return found;
    }

    private static String mobName(MinecraftClient client, MobSpawnerBlockEntity spawner, BlockPos pos) {
        MobSpawnerEntry entry = ((SpawnerLogicAccessor) spawner.getLogic()).limitless$getSpawnEntry();
        if (entry != null) {
            String id = entry.getNbt().getString("id", "");
            if (!id.isEmpty()) {
                String name = EntityType.get(id).map(type -> type.getName().getString()).orElse(null);
                if (name != null) return name;
            }
        }
        try {
            Entity rendered = spawner.getLogic().getRenderedEntity(client.world, pos);
            if (rendered != null) return rendered.getType().getName().getString();
        } catch (RuntimeException ignored) {
            // Keep the label even if the server did not send a mob type.
        }
        return null;
    }

    private float fade(double distance) {
        double max = range.get();
        return (float) Math.max(0, Math.min(1, (max - distance) / (max * 0.125)));
    }

    public void renderHud(DrawContext context, RenderTickCounter ticks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || client.world == null || client.player == null
                || client.currentScreen != null || client.options.hudHidden) return;
        Camera camera = client.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getCameraPos();
        Vec3d forward = new Vec3d(camera.getHorizontalPlane());
        List<ProjectedSpawner> visible = new ArrayList<>();
        for (Shown item : inRange(client)) {
            BlockPos pos = item.pos();
            Vec3d anchor = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5);
            if (anchor.subtract(cameraPos).dotProduct(forward) <= 0.05) continue;
            Vec3d projected = client.gameRenderer.project(anchor);
            if (!Double.isFinite(projected.x) || !Double.isFinite(projected.y)
                    || Math.abs(projected.x) > 1.1 || Math.abs(projected.y) > 1.1) continue;
            float x = (float) ((projected.x + 1) * context.getScaledWindowWidth() / 2);
            float y = (float) ((1 - projected.y) * context.getScaledWindowHeight() / 2);
            float alpha = fade(item.distance());
            if (alpha < 0.02f) continue;
            visible.add(new ProjectedSpawner(item, x, y, alpha));
        }
        int count = Math.min(MAX_LABELS, visible.size());
        for (int i = count - 1; i >= 0; i--) {
            ProjectedSpawner target = visible.get(i);
            Shown item = target.item();
            BlockPos pos = item.pos();
            BlockEntity entity = client.world.getBlockEntity(pos);
            String mob = entity instanceof MobSpawnerBlockEntity spawner
                    ? mobName(client, spawner, pos) : null;
            String title = mob == null ? "Spawner" : mob + " Spawner";
            String detail = Math.round(item.distance()) + "m";
            if (activationRadius.get() && item.range() > 0) {
                detail += "  ·  " + item.range() + "m range";
            }
            int width = Math.max(client.textRenderer.getWidth(title), client.textRenderer.getWidth(detail)) + 16;
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(target.x(), target.y());
            GuiDraw.rounded(context, -width / 2, -29, width, 27, 5,
                    GuiDraw.fade(0xE8120C1F, target.alpha()));
            GuiDraw.outline(context, -width / 2, -29, width, 27, 5,
                    GuiDraw.fade(Theme.PRIMARY, target.alpha()));
            context.drawCenteredTextWithShadow(client.textRenderer, title, 0, -25,
                    GuiDraw.fade(Theme.TEXT, target.alpha()));
            context.drawCenteredTextWithShadow(client.textRenderer, detail, 0, -14,
                    GuiDraw.fade(item.inside() ? Theme.PRIMARY : Theme.MUTED_TEXT, target.alpha()));
            context.getMatrices().popMatrix();
        }
    }

    public void renderWorld(WorldRenderContext context) {
        if (!isEnabled() || !activationRadius.get()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        List<Shown> shown = inRange(client);
        if (shown.isEmpty() || context.matrices() == null || context.consumers() == null) return;
        Vec3d camera = context.worldState().cameraRenderState.pos;
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        VertexConsumer quads = consumers.getBuffer(EspRenderLayers.FILLED_THROUGH_WALLS);
        matrices.push();
        try {
            matrices.translate(-camera.x, -camera.y, -camera.z);
            MatrixStack.Entry matrix = matrices.peek();
            for (int index = 0; index < Math.min(MAX_LABELS, shown.size()); index++) {
                Shown item = shown.get(index);
                if (item.range() <= 0) continue;
                int alpha = Math.round(255 * fade(item.distance()) * (item.inside() ? 0.8f : 0.4f));
                if (alpha < 4) continue;
                int color = (alpha << 24) | (Theme.PRIMARY & 0x00FFFFFF);
                double centerX = item.pos().getX() + 0.5;
                double centerY = item.pos().getY() + 0.5;
                double centerZ = item.pos().getZ() + 0.5;
                double outer = item.range() + 0.04, inner = item.range() - 0.04;
                for (int i = 0; i < RING_SEGMENTS; i++) {
                    double a = Math.PI * 2 * i / RING_SEGMENTS;
                    double b = Math.PI * 2 * (i + 1) / RING_SEGMENTS;
                    vertex(quads, matrix, centerX + Math.cos(a) * outer, centerY,
                            centerZ + Math.sin(a) * outer, color);
                    vertex(quads, matrix, centerX + Math.cos(b) * outer, centerY,
                            centerZ + Math.sin(b) * outer, color);
                    vertex(quads, matrix, centerX + Math.cos(b) * inner, centerY,
                            centerZ + Math.sin(b) * inner, color);
                    vertex(quads, matrix, centerX + Math.cos(a) * inner, centerY,
                            centerZ + Math.sin(a) * inner, color);
                }
            }
        } finally {
            matrices.pop();
        }
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry matrix,
                               double x, double y, double z, int color) {
        consumer.vertex(matrix, (float) x, (float) y, (float) z).color(color);
    }

    private record ChunkCoordinate(int x, int z) { }
    private record Shown(BlockPos pos, double distance, int range, boolean inside) { }
    private record ProjectedSpawner(Shown item, float x, float y, float alpha) { }
}
