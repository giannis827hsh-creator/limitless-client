package com.example.mod.module;

import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.ColorSetting;
import com.example.mod.setting.NumberSetting;
import com.example.mod.setting.StringSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashSet;

/** Client-side block search for selected ores and spawners in already-loaded chunks. */
public final class BlockESP extends Module {
    private static final long FULL_RESCAN_INTERVAL = 200L;
    private static final int CHUNKS_PER_TICK = 1;
    private static final int CELLS_PER_TICK = 4096;
    private static final long SCAN_BUDGET_NANOS = 1_500_000L;
    private static final int MAX_TARGETS_PER_CHUNK = 512;
    private static final int MAX_VISIBLE_TARGETS = 2048;
    private static final int MAX_CACHED_TARGETS = 8192;
    private Set<Block> selectedBlocks = Set.of();
    private Map<Block, Integer> customColors = Map.of();
    private ScanWork activeScan;

    private final NumberSetting scanRadius = addSetting(new NumberSetting("Scan radius", 100.0, 16.0, 256.0, 16.0));
    private final BooleanSetting tracers = addSetting(new BooleanSetting("Tracers", true));
    private final ColorSetting blockColor = addSetting(new ColorSetting("Block color", 0xFFAA55FF));
    private final StringSetting selectedBlockIds = addSetting(new StringSetting("Selected blocks", "", 65535));
    private final StringSetting savedColors = addSetting(new StringSetting("Block colors", "", 65535));

    // All scanning and rendering occurs on the client thread. The published immutable
    // snapshot also makes iteration safe if a renderer asks for it during a scan update.
    private volatile List<TargetBlock> visibleTargets = List.of();
    private final Map<Long, List<TargetBlock>> targetsByChunk = new HashMap<>();
    private final ArrayDeque<ChunkCoordinate> scanQueue = new ArrayDeque<>();
    private Object scannedWorld;
    private long lastFullScanTime = Long.MIN_VALUE;
    private int scanCenterChunkX = Integer.MIN_VALUE;
    private int scanCenterChunkZ = Integer.MIN_VALUE;
    private int scannedSettingsHash;
    private int scanPlayerX;
    private int scanPlayerY;
    private int scanPlayerZ;

    public BlockESP() {
        super("Block ESP", false, Category.VISUALS);
        scanRadius.describe("Search distance in blocks around your player. Larger ranges take longer to scan.");
        tracers.describe("Draw thin lines from the crosshair toward selected blocks.");
        blockColor.describe("Default outline and tracer color for selected blocks.");
        selectedBlockIds.visibleWhen(() -> false);
        savedColors.visibleWhen(() -> false);
    }

    public NumberSetting scanRadius() { return scanRadius; }
    public BooleanSetting tracers() { return tracers; }
    public ColorSetting blockColor() { return blockColor; }
    public String selectedIds() { return selectedBlockIds.get(); }
    public boolean isSelected(Block block) {
        return new HashSet<>(java.util.Arrays.asList(selectedBlockIds.get().split(",")))
                .contains(Registries.BLOCK.getId(block).toString());
    }
    public void setSelected(Set<Block> blocks) {
        String value = blocks.stream().map(block -> Registries.BLOCK.getId(block).toString())
                .sorted().collect(java.util.stream.Collectors.joining(","));
        selectedBlockIds.set(value);
    }
    public int selectedCount() { return selectedBlockIds.get().isEmpty() ? 0 : selectedBlockIds.get().split(",").length; }
    public int displayColor(Block block) {
        String id = Registries.BLOCK.getId(block).toString();
        for (String entry : savedColors.get().split(";")) {
            int split = entry.lastIndexOf('=');
            if (split > 0 && entry.substring(0, split).equals(id)) {
                try { return (int) Long.parseLong(entry.substring(split + 1), 16); }
                catch (NumberFormatException ignored) { break; }
            }
        }
        return blockColor.get();
    }
    public void setDisplayColor(Block block, int color) {
        String id = Registries.BLOCK.getId(block).toString();
        Map<String, String> colors = new java.util.TreeMap<>();
        for (String entry : savedColors.get().split(";")) {
            int split = entry.lastIndexOf('=');
            if (split > 0) colors.put(entry.substring(0, split), entry.substring(split + 1));
        }
        colors.put(id, Integer.toHexString(color));
        savedColors.set(colors.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(java.util.stream.Collectors.joining(";")));
    }

    @Override protected void onEnable() { onWorldLeave(); }
    @Override protected void onDisable() { onWorldLeave(); }
    @Override public void onWorldLeave() {
        scannedWorld = null; scanQueue.clear(); activeScan = null;
        targetsByChunk.clear(); visibleTargets = List.of(); selectedBlocks = Set.of(); customColors = Map.of();
    }

    /** Incremental, palette-filtered scan with both a cell limit and a time target. */
    public void tick(MinecraftClient client) {
        if (!isEnabled() || client.world == null || client.player == null || client.isPaused()) return;
        Vec3d origin = EspTracers.origin(client, 0);
        int centerChunkX = Math.floorDiv((int) Math.floor(origin.x), 16);
        int centerChunkZ = Math.floorDiv((int) Math.floor(origin.z), 16);
        int settingsHash = settingsHash();
        long worldTime = client.world.getTime();
        boolean selectionChanged = scannedWorld != client.world || settingsHash != scannedSettingsHash;
        boolean reset = selectionChanged || centerChunkX != scanCenterChunkX || centerChunkZ != scanCenterChunkZ;
        if (selectionChanged) {
            Set<Block> selected = new HashSet<>();
            for (String id : selectedBlockIds.get().split(",")) {
                net.minecraft.util.Identifier parsed = net.minecraft.util.Identifier.tryParse(id.trim());
                if (parsed != null && Registries.BLOCK.containsId(parsed)) selected.add(Registries.BLOCK.get(parsed));
            }
            selectedBlocks = Set.copyOf(selected);
            Map<Block, Integer> colors = new HashMap<>();
            for (Block block : selected) colors.put(block, displayColor(block));
            customColors = Map.copyOf(colors);
        }
        if (reset) {
            targetsByChunk.clear(); visibleTargets = List.of(); activeScan = null;
            beginScan(client, centerChunkX, centerChunkZ, settingsHash, worldTime);
        } else if (worldTime - lastFullScanTime >= FULL_RESCAN_INTERVAL && scanQueue.isEmpty() && activeScan == null) {
            beginScan(client, centerChunkX, centerChunkZ, settingsHash, worldTime);
        }
        if (selectedBlocks.isEmpty()) { scanQueue.clear(); activeScan = null; return; }
        long deadline = System.nanoTime() + SCAN_BUDGET_NANOS;
        int remaining = CELLS_PER_TICK;
        boolean changed = false;
        for (int completed = 0; completed < CHUNKS_PER_TICK && remaining > 0 && System.nanoTime() < deadline;) {
            if (activeScan == null) {
                if (scanQueue.isEmpty()) break;
                ChunkCoordinate coordinate = scanQueue.removeFirst();
                WorldChunk chunk = client.world.getChunkManager().getChunk(coordinate.x(), coordinate.z(), ChunkStatus.FULL, false);
                if (chunk == null) { changed |= targetsByChunk.remove(chunkKey(coordinate.x(), coordinate.z())) != null; completed++; continue; }
                activeScan = new ScanWork(chunk, client.world.getBottomY());
            }
            WorldChunk current = client.world.getChunkManager().getChunk(activeScan.chunk.getPos().x,
                    activeScan.chunk.getPos().z, ChunkStatus.FULL, false);
            if (current != activeScan.chunk) {
                targetsByChunk.remove(chunkKey(activeScan.chunk.getPos().x, activeScan.chunk.getPos().z));
                changed = true; activeScan = null; completed++; continue;
            }
            remaining -= activeScan.advance(remaining, deadline);
            if (!activeScan.complete()) break;
            long key = chunkKey(activeScan.chunk.getPos().x, activeScan.chunk.getPos().z);
            targetsByChunk.remove(key);
            int cachedCount = targetsByChunk.values().stream().mapToInt(List::size).sum();
            int keep = Math.min(activeScan.found.size(), Math.max(0, MAX_CACHED_TARGETS - cachedCount));
            targetsByChunk.put(key, List.copyOf(activeScan.found.subList(0, keep)));
            activeScan = null; completed++; changed = true;
        }
        if (changed) publishTargets();
    }
    /** Draws thin target-colored outlines with the mod's no-depth-test 1.21.11 layer. */
    public void renderWorld(WorldRenderContext context) {
        if (!isEnabled() || visibleTargets.isEmpty()) return;
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) return;

        Vec3d camera = context.worldState().cameraRenderState.pos;
        VertexConsumer lines = consumers.getBuffer(EspRenderLayers.LINES_THROUGH_WALLS);
        matrices.push();
        try {
            matrices.translate(-camera.x, -camera.y, -camera.z);
            for (TargetBlock target : visibleTargets) {
                int color = colorFor(target.block());
                if (color == 0) continue;
                VertexRendering.drawOutline(
                        matrices,
                        lines,
                        target.outline(),
                        0.0, 0.0, 0.0, color, 2.0f);
            }
        } finally {
            matrices.pop();
        }
    }

    public void renderHud(DrawContext context, RenderTickCounter ticks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || !tracers.get() || client.player == null || visibleTargets.isEmpty()) return;
        List<EspTracers.Target> candidates = new ArrayList<>();
        Vec3d origin = EspTracers.origin(client, ticks.getTickProgress(false));
        for (TargetBlock target : visibleTargets) {
            Vec3d point = Vec3d.ofCenter(target.pos());
            candidates.add(new EspTracers.Target(point, colorFor(target.block()), point.squaredDistanceTo(origin)));
        }
        EspTracers.draw(context, client, candidates, 32);
    }

    private void beginScan(MinecraftClient client, int centerChunkX, int centerChunkZ,
                           int settingsHash, long worldTime) {
        scanQueue.clear();
        Vec3d origin = EspTracers.origin(client, 0);
        int configuredRadius = (int) scanRadius.get();
        // Scanning outside the current loaded view cannot find blocks; cap the work to
        // the view distance plus one chunk while keeping the user setting at its chosen value.
        int radius = Math.min(configuredRadius, client.options.getClampedViewDistance() * 16 + 16);
        int chunkRadius = (int) Math.ceil(radius / 16.0);
        double radiusSquared = (double) configuredRadius * configuredRadius;
        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                WorldChunk loaded = client.world.getChunkManager().getChunk(
                        chunkX, chunkZ, ChunkStatus.FULL, false);
                if (loaded == null) continue;
                if (!chunkIntersectsRadius(chunkX, chunkZ, origin.x, origin.z, radiusSquared)) continue;
                scanQueue.addLast(new ChunkCoordinate(chunkX, chunkZ));
            }
        }
        List<ChunkCoordinate> nearest = new ArrayList<>(scanQueue);
        nearest.sort(java.util.Comparator.comparingLong(c -> {
            long dx = (long) c.x() - centerChunkX, dz = (long) c.z() - centerChunkZ;
            return dx * dx + dz * dz;
        }));
        scanQueue.clear(); scanQueue.addAll(nearest);
        scannedWorld = client.world;
        scanCenterChunkX = centerChunkX;
        scanCenterChunkZ = centerChunkZ;
        scannedSettingsHash = settingsHash;
        lastFullScanTime = worldTime;
        scanPlayerX = (int) Math.floor(origin.x);
        scanPlayerY = (int) Math.floor(origin.y);
        scanPlayerZ = (int) Math.floor(origin.z);
        targetsByChunk.keySet().removeIf(key -> !isChunkInsideScan(key, centerChunkX, centerChunkZ, chunkRadius));
    }

    private final class ScanWork {
        final WorldChunk chunk;
        final int bottomY;
        final List<TargetBlock> found = new ArrayList<>();
        int sectionIndex, cell;
        boolean checkedSection;
        ScanWork(WorldChunk chunk, int bottomY) { this.chunk = chunk; this.bottomY = bottomY; }
        boolean complete() { return sectionIndex >= chunk.getSectionArray().length || found.size() >= MAX_TARGETS_PER_CHUNK; }
        int advance(int limit, long deadline) {
            int examined = 0;
            var sections = chunk.getSectionArray();
            while (examined < limit && !complete()) {
                if ((examined & 63) == 0 && System.nanoTime() >= deadline) break;
                var section = sections[sectionIndex];
                if (!checkedSection) {
                    // Check a section's small palette before reading any of its 4096 cells.
                    if (section == null || section.isEmpty() || !section.hasAny(state -> selectedBlocks.contains(state.getBlock()))) {
                        sectionIndex++; examined++; continue;
                    }
                    checkedSection = true;
                }
                int x = cell & 15, z = (cell >> 4) & 15, y = (cell >> 8) & 15;
                Block block = section.getBlockState(x, y, z).getBlock();
                if (selectedBlocks.contains(block)) {
                    int wx = chunk.getPos().getStartX() + x, wy = bottomY + sectionIndex * 16 + y;
                    int wz = chunk.getPos().getStartZ() + z;
                    double dx = wx - scanPlayerX, dy = wy - scanPlayerY, dz = wz - scanPlayerZ;
                    if (dx * dx + dy * dy + dz * dz <= scanRadius.get() * scanRadius.get())
                        found.add(new TargetBlock(new BlockPos(wx, wy, wz), block));
                }
                examined++;
                if (++cell == 4096) { cell = 0; sectionIndex++; checkedSection = false; }
            }
            return examined;
        }
    }
    private void publishTargets() {
        List<TargetBlock> combined = new ArrayList<>();
        // Render the closest results first and bound vertex/allocation costs for broad searches.
        List<List<TargetBlock>> chunks = new ArrayList<>(targetsByChunk.values());
        chunks.removeIf(List::isEmpty);
        BlockPos origin = new BlockPos(scanPlayerX, scanPlayerY, scanPlayerZ);
        chunks.sort(java.util.Comparator.comparingDouble(list -> list.getFirst().pos().getSquaredDistance(origin)));
        for (List<TargetBlock> chunkTargets : chunks) {
            int count = Math.min(chunkTargets.size(), MAX_VISIBLE_TARGETS - combined.size());
            combined.addAll(chunkTargets.subList(0, count));
            if (combined.size() >= MAX_VISIBLE_TARGETS) break;
        }
        visibleTargets = List.copyOf(combined);
    }

    private int settingsHash() {
        int result = Double.hashCode(scanRadius.get());
        result = 31 * result + selectedBlockIds.get().hashCode();
        result = 31 * result + savedColors.get().hashCode();
        result = 31 * result + blockColor.get();
        return result;
    }

    private int colorFor(Block block) {
        return selectedBlocks.contains(block) ? customColors.getOrDefault(block, blockColor.get()) : 0;
    }

    private static boolean chunkIntersectsRadius(int chunkX, int chunkZ, double playerX, double playerZ,
                                                  double radiusSquared) {
        double minX = chunkX * 16.0;
        double minZ = chunkZ * 16.0;
        double nearestX = Math.max(minX, Math.min(playerX, minX + 16.0));
        double nearestZ = Math.max(minZ, Math.min(playerZ, minZ + 16.0));
        double dx = playerX - nearestX;
        double dz = playerZ - nearestZ;
        return dx * dx + dz * dz <= radiusSquared;
    }

    private static boolean isChunkInsideScan(long key, int centerX, int centerZ, int radius) {
        int x = (int) (key >> 32);
        int z = (int) key;
        return Math.abs(x - centerX) <= radius && Math.abs(z - centerZ) <= radius;
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private record ChunkCoordinate(int x, int z) { }
    private record TargetBlock(BlockPos pos, Block block, net.minecraft.util.shape.VoxelShape outline) {
        TargetBlock(BlockPos pos, Block block) {
            this(pos, block, VoxelShapes.cuboid(new net.minecraft.util.math.Box(
                    pos.getX() + 0.04, pos.getY() + 0.04, pos.getZ() + 0.04,
                    pos.getX() + 0.96, pos.getY() + 0.96, pos.getZ() + 0.96)));
        }
    }
}
