package com.example.mod.module;

import com.example.mod.setting.ColorSetting;
import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.ModeSetting;
import com.example.mod.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.BlockView;

import java.util.List;

/** Client-only nearby 1x1 hole detection and visualization. */
public final class HoleESP extends Module {
    private static final int MAX_HOLE_DEPTH = 8;
    private static final int VERTICAL_SCAN_RANGE = 10;
    private static final int SCAN_INTERVAL_TICKS = 5;
    private final NumberSetting range = addSetting(new NumberSetting("Range", 100.0, 10.0, 100.0, 10.0));
    private final ModeSetting renderMode = addSetting(new ModeSetting("Mode", "Both", "Outline", "Fill", "Both"));
    private final ColorSetting safeColor = addSetting(new ColorSetting("Safe color", 0xFF35E06F));
    private final ColorSetting unsafeColor = addSetting(new ColorSetting("Unsafe color", 0xFFFFC43D));
    private final BooleanSetting tracers = addSetting(new BooleanSetting("Tracers", true));

    private static final long SCAN_BUDGET_NANOS = 1_000_000L;
    private static final int MAX_COLUMNS_PER_TICK = 128;
    private static final int MAX_HIGHLIGHTS = 2048;
    private final java.util.Map<Long, Hole> holesByColumn = new java.util.HashMap<>();
    private Object scannedWorld;
    private double scannedRange = -1;
    private BlockPos scanOrigin;
    private int ring, ringStep, scanRadius, minY, maxY;
    private long nextScanTick;
    private List<Hole> holes = List.of();
    public HoleESP() {
        super("Hole ESP", false, Category.VISUALS);
        range.describe("Search radius around your player in blocks. Nearby holes appear first; distant scans take longer.");
        renderMode.describe("Outline draws hole edges, Fill colors the floor, and Both shows the two together.");
        safeColor.describe("Color for holes whose floor and surrounding walls are entirely obsidian or bedrock.");
        unsafeColor.describe("Color for holes with breakable blocks in their floor or surrounding walls.");
        tracers.describe("Draw safe or unsafe colored lines from the crosshair to nearby holes.");
    }

    public NumberSetting range() { return range; }
    public ModeSetting renderMode() { return renderMode; }
    public ColorSetting safeColor() { return safeColor; }
    public ColorSetting unsafeColor() { return unsafeColor; }

    public void renderHud(DrawContext context, RenderTickCounter ticks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || !tracers.get() || client.player == null || holes.isEmpty()) return;
        Vec3d origin = EspTracers.origin(client, ticks.getTickProgress(false));
        List<EspTracers.Target> candidates = new java.util.ArrayList<>();
        for (Hole hole : holes) {
            if (!insideRange(hole.pos(), BlockPos.ofFloored(origin), range.get())) continue;
            Vec3d point = Vec3d.ofCenter(hole.pos()).add(0, -Math.min(1, hole.depth()) * 0.5, 0);
            candidates.add(new EspTracers.Target(point, hole.safe() ? safeColor.get() : unsafeColor.get(),
                    point.squaredDistanceTo(origin)));
        }
        EspTracers.draw(context, client, candidates, 24);
    }

    /** Draw cached results; scanning runs in the tick callback. */
    public void renderWorld(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || client.world == null || client.player == null) return;

        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) return;

        if (holes.isEmpty()) return;

        Vec3d camera = context.worldState().cameraRenderState.pos;
        VertexConsumer lines = consumers.getBuffer(EspRenderLayers.LINES_THROUGH_WALLS);
        VertexConsumer fills = renderMode.get().equals("Outline")
                ? null : consumers.getBuffer(EspRenderLayers.FILLED_THROUGH_WALLS);

        matrices.push();
        try {
            matrices.translate(-camera.x, -camera.y, -camera.z);
            for (Hole hole : holes) {
                BlockPos pos = hole.pos();
                if (!insideRange(pos, client.player.getBlockPos(), range.get())) continue;
                int color = hole.safe() ? safeColor.get() : unsafeColor.get();
                if (!renderMode.get().equals("Fill")) {
                    Box outlineBox = new Box(
                            pos.getX() + 0.035, pos.getY() - hole.depth() + 0.035, pos.getZ() + 0.035,
                            pos.getX() + 0.965, pos.getY() + 0.965, pos.getZ() + 0.965
                    );
                    VertexRendering.drawOutline(matrices, lines, VoxelShapes.cuboid(outlineBox),
                            0.0, 0.0, 0.0, color, 2.0f);
                }
                if (fills != null) {
                    drawFloorHighlight(matrices.peek(), fills, pos, hole.depth(), withAlpha(color, 90));
                }
            }
        } finally {
            matrices.pop();
        }
    }

    @Override protected void onEnable() { onWorldLeave(); }
    @Override protected void onDisable() { onWorldLeave(); }
    @Override public void onWorldLeave() {
        holesByColumn.clear(); holes = List.of(); scannedWorld = null;
        scanOrigin = null; scannedRange = -1; nextScanTick = 0;
    }

    /** Incremental nearest-first scan. Rendering only consumes the cached highlights. */
    public void tick(MinecraftClient client) {
        if (!isEnabled() || client.world == null || client.player == null || client.isPaused()) return;
        BlockPos player = BlockPos.ofFloored(EspTracers.origin(client, 0));
        if (scannedWorld != client.world || scannedRange != range.get()) onWorldLeave();
        long tick = client.world.getTime();
        boolean moved = scanOrigin != null && !player.isWithinDistance(scanOrigin, 4);
        boolean changed = moved;
        if (scanOrigin == null || moved || (ring > scanRadius && tick >= nextScanTick)) {
            scannedWorld = client.world; scannedRange = range.get(); scanOrigin = player.toImmutable();
            scanRadius = Math.min((int) range.get(), client.options.getClampedViewDistance() * 16 + 16);
            minY = Math.max(client.world.getBottomY(), player.getY() - VERTICAL_SCAN_RANGE);
            maxY = Math.min(client.world.getTopYInclusive() - 1, player.getY() + VERTICAL_SCAN_RANGE);
            ring = 0; ringStep = 0;
            changed |= holesByColumn.values().removeIf(hole -> !insideRange(hole.pos(), player, range.get())
                    || hole.pos().getY() < minY || hole.pos().getY() > maxY);
        }
        if (ring > scanRadius) return;
        long deadline = System.nanoTime() + SCAN_BUDGET_NANOS;
        BlockPos.Mutable candidate = new BlockPos.Mutable();
        for (int count = 0; count < MAX_COLUMNS_PER_TICK && ring <= scanRadius; count++) {
            if (count > 0 && System.nanoTime() >= deadline) break;
            int dx = 0, dz = 0;
            if (ring > 0) {
                int side = ringStep / (2 * ring), offset = ringStep % (2 * ring);
                switch (side) {
                    case 0 -> { dx = -ring + offset; dz = -ring; }
                    case 1 -> { dx = ring; dz = -ring + offset; }
                    case 2 -> { dx = ring - offset; dz = ring; }
                    default -> { dx = -ring; dz = ring - offset; }
                }
                if (++ringStep >= 8 * ring) { ring++; ringStep = 0; }
            } else { ring = 1; }
            if (dx * dx + dz * dz > scannedRange * scannedRange) continue;
            int x = scanOrigin.getX() + dx, z = scanOrigin.getZ() + dz;
            long key = chunkKey(x, z);
            Hole found = null;
            if (client.world.getChunkManager().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false) != null) {
                // Highest opening wins. Stop after finding it instead of checking all lower cells.
                for (int y = maxY; y >= minY; y--) {
                    candidate.set(x, y, z);
                    found = classifyHole(client.world, candidate);
                    if (found != null) break;
                }
            }
            Hole old = holesByColumn.get(key);
            if (!java.util.Objects.equals(old, found)) {
                if (found == null) holesByColumn.remove(key);
                else if (old != null || holesByColumn.size() < MAX_HIGHLIGHTS) holesByColumn.put(key, found);
                changed = true;
            }
        }
        if (changed) holes = List.copyOf(holesByColumn.values());
        if (ring > scanRadius) nextScanTick = tick + SCAN_INTERVAL_TICKS;
    }

    private static boolean insideRange(BlockPos hole, BlockPos player, double radius) {
        double dx = (double) hole.getX() - player.getX(), dz = (double) hole.getZ() - player.getZ();
        return dx * dx + dz * dz <= radius * radius;
    }
    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /** Finds a two-block-headroom shaft with solid walls down to a floor, to eight blocks deep. */
    private static Hole classifyHole(BlockView world, BlockPos pos) {
        if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir()) return null;
        boolean safe = true;
        BlockPos.Mutable wallPos = new BlockPos.Mutable();

        for (int depth = 0; depth <= MAX_HOLE_DEPTH; depth++) {
            BlockPos airLayer = pos.down(depth);
            for (int[] direction : DIRECTIONS) {
                wallPos.set(airLayer.getX() + direction[0], airLayer.getY(), airLayer.getZ() + direction[1]);
                if (!hasCollision(world, wallPos)) return null;
                if (!isSafeMaterial(world.getBlockState(wallPos).getBlock())) safe = false;
            }

            BlockPos below = airLayer.down();
            BlockState belowState = world.getBlockState(below);
            if (hasCollision(world, below)) {
                if (!isSafeMaterial(belowState.getBlock())) safe = false;
                return new Hole(pos.toImmutable(), depth, safe);
            }
            if (!belowState.isAir() || depth == MAX_HOLE_DEPTH) return null;
        }
        return null;
    }

    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static boolean hasCollision(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(world, pos).isEmpty();
    }

    private static boolean isSafeMaterial(Block block) {
        return block == Blocks.OBSIDIAN || block == Blocks.BEDROCK;
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }

    private static void drawFloorHighlight(MatrixStack.Entry matrix, VertexConsumer consumer,
                                           BlockPos pos, int depth, int color) {
        float x0 = pos.getX() + 0.08f;
        float x1 = pos.getX() + 0.92f;
        float y = pos.getY() - depth + 0.04f;
        float z0 = pos.getZ() + 0.08f;
        float z1 = pos.getZ() + 0.92f;
        consumer.vertex(matrix, x0, y, z0).color(color);
        consumer.vertex(matrix, x0, y, z1).color(color);
        consumer.vertex(matrix, x1, y, z1).color(color);
        consumer.vertex(matrix, x1, y, z0).color(color);
    }

    private record Hole(BlockPos pos, int depth, boolean safe) { }
}
