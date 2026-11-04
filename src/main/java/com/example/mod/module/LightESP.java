package com.example.mod.module;

import com.example.mod.setting.ColorSetting;
import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.IntegerSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

/** Budgeted scan of actual light-source blocks in chunks already loaded by the server. */
public final class LightESP extends Module {
    private static final Set<Block> SOURCES = Set.of(
            Blocks.TORCH, Blocks.WALL_TORCH, Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH,
            Blocks.LANTERN, Blocks.SOUL_LANTERN, Blocks.GLOWSTONE, Blocks.SEA_LANTERN,
            Blocks.SHROOMLIGHT, Blocks.JACK_O_LANTERN, Blocks.END_ROD, Blocks.REDSTONE_LAMP);
    private static final int CELLS_PER_TICK = 4096;
    private static final long SCAN_BUDGET_NS = 1_500_000L;
    private final IntegerSetting radius = addSetting(new IntegerSetting("Scan radius", 20, 8, 32));
    private final IntegerSetting minimumLight = addSetting(new IntegerSetting("Minimum light", 5, 1, 15));
    private final IntegerSetting maxHighlights = addSetting(new IntegerSetting("Max highlights", 128, 16, 512));
    private final ColorSetting color = addSetting(new ColorSetting("Light color", 0xFFFFDD66));
    private final BooleanSetting tracers = addSetting(new BooleanSetting("Tracers", true));
    private ClientWorld world;
    private BlockPos center;
    private int side, index, lastRadius, lastMinimumLight;
    private long nextScanTick;
    private WorldChunk columnChunk;
    private int columnX = Integer.MIN_VALUE, columnZ = Integer.MIN_VALUE;
    private final List<BlockPos> working = new ArrayList<>();
    private List<BlockPos> visible = List.of();

    public LightESP() {
        super("Light ESP", false, Category.VISUALS);
        radius.describe("Scan nearby loaded blocks for placed light sources. This never reveals blocks the server has not sent.");
        minimumLight.describe("Only highlight sources emitting at least this much block light.");
        maxHighlights.describe("Maximum number of nearby light sources to draw. Lower values reduce clutter.");
        color.describe("Outline color for light sources.");
        tracers.describe("Draw lines from the crosshair to nearby placed light sources.");
    }

    @Override protected void onEnable() { onWorldLeave(); }
    @Override protected void onDisable() { onWorldLeave(); }
    @Override public void onWorldLeave() {
        world = null; center = null; index = 0; nextScanTick = 0;
        columnChunk = null; columnX = Integer.MIN_VALUE; columnZ = Integer.MIN_VALUE;
        working.clear(); visible = List.of();
    }

    public void tick(MinecraftClient client) {
        if (!isEnabled() || client.world == null || client.player == null || client.isPaused()) return;
        BlockPos player = BlockPos.ofFloored(EspTracers.origin(client, 0));
        int r = radius.get();
        boolean moved = center != null && (Math.abs(player.getX() - center.getX()) > 8
                || Math.abs(player.getY() - center.getY()) > 8 || Math.abs(player.getZ() - center.getZ()) > 8);
        if (world != client.world || center == null || r != lastRadius
                || minimumLight.get() != lastMinimumLight || moved) {
            onWorldLeave(); world = client.world; center = player;
            lastRadius = r; lastMinimumLight = minimumLight.get();
            side = r * 2 + 1;
        }
        if (index >= side * side * side) {
            if (client.world.getTime() < nextScanTick) return;
            index = 0; working.clear(); center = player;
        }
        long deadline = System.nanoTime() + SCAN_BUDGET_NS;
        int examined = 0;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        while (index < side * side * side && examined++ < CELLS_PER_TICK && System.nanoTime() < deadline) {
            int offsetY = index % side;
            int offsetZ = index / side % side;
            int offsetX = index / (side * side);
            index++;
            int x = center.getX() + offsetX - r;
            int y = center.getY() + offsetY - r;
            int z = center.getZ() + offsetZ - r;
            if (y < world.getBottomY() || y >= world.getTopYInclusive() + 1) continue;
            if (columnX != x >> 4 || columnZ != z >> 4) {
                columnX = x >> 4; columnZ = z >> 4;
                columnChunk = world.getChunkManager().getChunk(columnX, columnZ, ChunkStatus.FULL, false);
            }
            if (columnChunk == null) continue;
            cursor.set(x, y, z);
            if (SOURCES.contains(columnChunk.getBlockState(cursor).getBlock())
                    && columnChunk.getBlockState(cursor).getLuminance() >= minimumLight.get())
                working.add(cursor.toImmutable());
        }
        if (index >= side * side * side) {
            working.sort(Comparator.comparingDouble(pos -> pos.getSquaredDistance(player)));
            visible = List.copyOf(working.subList(0, Math.min(maxHighlights.get(), working.size())));
            working.clear(); nextScanTick = world.getTime() + 100;
        }
    }

    public void renderWorld(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || client.world != world || client.player == null || visible.isEmpty()) return;
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) return;
        Vec3d camera = context.worldState().cameraRenderState.pos;
        VertexConsumer lines = consumers.getBuffer(EspRenderLayers.LINES_THROUGH_WALLS);
        double maxDistanceSq = (double) radius.get() * radius.get() * 3;
        matrices.push();
        try {
            matrices.translate(-camera.x, -camera.y, -camera.z);
            for (BlockPos pos : visible) {
                if (pos.getSquaredDistance(client.player.getBlockPos()) > maxDistanceSq) continue;
                VertexRendering.drawOutline(matrices, lines, VoxelShapes.cuboid(new Box(pos)),
                        0, 0, 0, color.get(), 2.0f);
            }
        } finally { matrices.pop(); }
    }

    public void renderHud(DrawContext context, RenderTickCounter ticks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || !tracers.get() || client.player == null || client.world != world) return;
        List<EspTracers.Target> candidates = new ArrayList<>();
        Vec3d origin = EspTracers.origin(client, ticks.getTickProgress(false));
        double maxDistanceSq = (double) radius.get() * radius.get() * 3;
        for (BlockPos pos : visible) {
            Vec3d point = Vec3d.ofCenter(pos);
            double distanceSq = point.squaredDistanceTo(origin);
            if (distanceSq <= maxDistanceSq)
                candidates.add(new EspTracers.Target(point, color.get(), distanceSq));
        }
        EspTracers.draw(context, client, candidates, 24);
    }
}
