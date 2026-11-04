package com.example.mod.module;

import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.ColorSetting;
import com.example.mod.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Client-only outlines for storage and farm blocks in already-loaded chunks. */
public final class StorageESP extends Module {
    private static final Logger LOGGER = LoggerFactory.getLogger("limitless/StorageESP");
    private static final int ENDER_CHEST_COLOR = 0xFFB66DFF;
    private static final int SHULKER_COLOR = 0xFFFFD84D;
    private static final int CHEST_COLOR = 0xFFFFA640;
    private static final int BARREL_COLOR = 0xFF91C46C;
    private static final int HOPPER_COLOR = 0xFF70C7B8;
    private static final int FURNACE_COLOR = 0xFFFFA36A;
    private static final int BREWING_COLOR = 0xFFB994FF;
    private static final int DISPENSER_COLOR = 0xFFE3C978;
    private static final int CRAFTER_COLOR = 0xFF68C6E8;
    private static final int BEACON_COLOR = 0xFF8BFAF1;
    private static final int PISTON_COLOR = 0xFFFFCD78;
    private static final int OBSERVER_COLOR = 0xFF9BBCFF;
    private static final int SPAWNER_COLOR = 0xFF9D73FF;
    private static final int ENCHANTING_COLOR = 0xFFFF78B4;
    private static final int FARM_CELLS_PER_TICK = 4096;
    private static final int MAX_FARM_PER_CHUNK = 128;
    private static final int MAX_FARM_VISIBLE = 256;
    private static final int MAX_FARM_CACHED = 4096;
    private static final long FARM_SCAN_BUDGET_NS = 1_500_000L;
    private static final long FARM_RESCAN_TICKS = 120;
    private static final int MAX_OUTLINES = 48;

    private final BooleanSetting renderChests = addSetting(new BooleanSetting("Chests", true));
    private final BooleanSetting renderEnderChests = addSetting(new BooleanSetting("Ender chests", true));
    private final BooleanSetting renderShulkers = addSetting(new BooleanSetting("Shulker boxes", true));
    private final BooleanSetting renderBarrels = addSetting(new BooleanSetting("Barrels", true));
    private final BooleanSetting renderHoppers = addSetting(new BooleanSetting("Hoppers", true));
    private final BooleanSetting renderFurnaces = addSetting(new BooleanSetting("Furnaces", true));
    private final BooleanSetting renderBrewing = addSetting(new BooleanSetting("Brewing stands", true));
    private final BooleanSetting renderDispensers = addSetting(new BooleanSetting("Dispensers / droppers", true));
    private final BooleanSetting renderCrafters = addSetting(new BooleanSetting("Crafters", true));
    private final BooleanSetting renderBeacons = addSetting(new BooleanSetting("Beacons", true));
    private final BooleanSetting renderPistons = addSetting(new BooleanSetting("Pistons", true));
    private final BooleanSetting renderObservers = addSetting(new BooleanSetting("Observers", true));
    private final BooleanSetting renderSpawners = addSetting(new BooleanSetting("Spawners", true));
    private final BooleanSetting renderEnchanting = addSetting(new BooleanSetting("Enchanting tables", true));
    private final BooleanSetting tracers = addSetting(new BooleanSetting("Tracers", true));
    private final BooleanSetting useCustomColor = addSetting(new BooleanSetting("Use custom color", false));
    private final ColorSetting boxOutlineColor = addSetting(new ColorSetting("Outline color", 0xFFFFA640));
    private final NumberSetting range = addSetting(new NumberSetting("Range", 128.0, 16.0, 256.0, 16.0));
    private Object cachedWorld;
    private Object loggedWorld;
    private long scannedWorldTime = Long.MIN_VALUE;
    private double scannedRange = -1.0;
    private int scannedChunkX = Integer.MIN_VALUE, scannedChunkZ = Integer.MIN_VALUE;
    private final List<BlockEntity> loadedBlockEntities = new ArrayList<>();
    private final Map<Long, List<FarmTarget>> farmByChunk = new HashMap<>();
    private final ArrayDeque<ChunkCoordinate> farmQueue = new ArrayDeque<>();
    private List<FarmTarget> visibleFarm = List.of();
    private FarmScan activeFarmScan;
    private Object farmWorld;
    private int farmCenterX = Integer.MIN_VALUE, farmCenterZ = Integer.MIN_VALUE, farmSettingsHash;
    private int farmOriginX, farmOriginY, farmOriginZ;
    private long nextFarmRescan;

    public StorageESP() {
        super("Storage ESP", false, Category.VISUALS);
        renderChests.describe("Highlight regular chests and trapped chests through walls.");
        renderEnderChests.describe("Highlight ender chests through walls.");
        renderShulkers.describe("Highlight placed shulker boxes of every color.");
        renderBarrels.describe("Highlight barrels through walls.");
        renderHoppers.describe("Highlight hoppers used for item sorting and farms.");
        renderFurnaces.describe("Highlight furnaces, blast furnaces and smokers.");
        renderBrewing.describe("Highlight brewing stands.");
        renderDispensers.describe("Highlight dispensers and droppers.");
        renderCrafters.describe("Highlight automatic crafters.");
        renderBeacons.describe("Highlight beacons.");
        renderPistons.describe("Search loaded chunk palettes for normal and sticky pistons. Large areas take longer to scan.");
        renderObservers.describe("Search loaded chunk palettes for observers. Large areas take longer to scan.");
        renderSpawners.describe("Highlight mob spawners in chunks already loaded by the server.");
        renderEnchanting.describe("Highlight enchanting tables in loaded chunks.");
        tracers.describe("Draw thin lines toward storage and farm targets, including directions to off-screen targets.");
        useCustomColor.describe("Use one outline and tracer color for every highlighted block.");
        boxOutlineColor.describe("Choose the shared highlight color when Use custom color is on.");
        range.describe("Maximum distance in blocks. Only chunks already loaded around your player are scanned.");
    }

    public BooleanSetting renderChests() { return renderChests; }
    public BooleanSetting renderEnderChests() { return renderEnderChests; }
    public BooleanSetting renderShulkers() { return renderShulkers; }
    public BooleanSetting renderBarrels() { return renderBarrels; }
    public BooleanSetting useCustomColor() { return useCustomColor; }
    public ColorSetting boxOutlineColor() { return boxOutlineColor; }
    public NumberSetting range() { return range; }

    public void renderHud(DrawContext context, RenderTickCounter ticks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || !tracers.get() || client.player == null || client.world == null) return;
        Vec3d origin = EspTracers.origin(client, ticks.getTickProgress(false));
        double maxDistanceSq = range.get() * range.get();
        PriorityQueue<Highlight> nearest = new PriorityQueue<>(MAX_OUTLINES + 1,
                Comparator.comparingDouble(Highlight::distanceSq).reversed());
        for (BlockEntity entity : loadedBlockEntities) {
            BlockPos pos = entity.getPos();
            int color = containerColor(client.world.getBlockState(pos).getBlock());
            addHighlight(nearest, pos, color, origin, maxDistanceSq);
        }
        for (FarmTarget target : visibleFarm) {
            BlockPos pos = target.pos();
            int color = farmColor(client.world.getBlockState(pos).getBlock());
            addHighlight(nearest, pos, color, origin, maxDistanceSq);
        }
        List<EspTracers.Target> candidates = new ArrayList<>(nearest.size());
        for (Highlight target : nearest)
            candidates.add(new EspTracers.Target(Vec3d.ofCenter(target.pos()),
                    target.color(), target.distanceSq()));
        EspTracers.draw(context, client, candidates, MAX_OUTLINES);
    }

    @Override protected void onEnable() { onWorldLeave(); }
    @Override protected void onDisable() { onWorldLeave(); }
    @Override public void onWorldLeave() {
        loadedBlockEntities.clear(); cachedWorld = null; loggedWorld = null;
        scannedWorldTime = Long.MIN_VALUE; scannedRange = -1;
        farmByChunk.clear(); farmQueue.clear(); visibleFarm = List.of(); activeFarmScan = null;
        farmWorld = null; farmCenterX = Integer.MIN_VALUE; farmCenterZ = Integer.MIN_VALUE;
        nextFarmRescan = 0;
    }
    public void tick(MinecraftClient client) {
        if (isEnabled() && client.world != null && client.player != null && !client.isPaused()) {
            refreshLoadedBlockEntities(client);
            scanFarmBlocks(client);
        }
    }

    /** Called on the client render thread after entities; only currently loaded block entities are scanned. */
    public void renderWorld(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || client.world == null || client.player == null) return;

        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) return;

        Vec3d camera = context.worldState().cameraRenderState.pos;
        double maxDistanceSquared = range.get() * range.get();
        VertexConsumer lines = consumers.getBuffer(EspRenderLayers.LINES_THROUGH_WALLS);

        matrices.push();
        try {
            // WorldRenderContext geometry uses world coordinates; subtract the interpolated camera origin.
            matrices.translate(-camera.x, -camera.y, -camera.z);

            // In 1.21.11 depth behavior is configured on the render pipeline.
            // The shared ESP line pipeline uses NO_DEPTH_TEST and does not write depth.
            PriorityQueue<Highlight> nearest = new PriorityQueue<>(MAX_OUTLINES + 1,
                    Comparator.comparingDouble(Highlight::distanceSq).reversed());
            for (BlockEntity entity : loadedBlockEntities) {
                BlockPos pos = entity.getPos();
                BlockState state = client.world.getBlockState(pos);
                addHighlight(nearest, pos, containerColor(state.getBlock()), camera, maxDistanceSquared);
            }
            for (FarmTarget target : visibleFarm) {
                BlockPos pos = target.pos();
                addHighlight(nearest, pos, farmColor(client.world.getBlockState(pos).getBlock()),
                        camera, maxDistanceSquared);
            }
            for (Highlight target : nearest) {
                BlockPos pos = target.pos();
                VertexRendering.drawOutline(matrices, lines,
                        VoxelShapes.cuboid(new net.minecraft.util.math.Box(
                                pos.getX() + 0.025, pos.getY() + 0.025, pos.getZ() + 0.025,
                                pos.getX() + 0.975, pos.getY() + 0.975, pos.getZ() + 0.975)),
                        0.0, 0.0, 0.0, target.color(), 2.0f);
            }
        } finally {
            matrices.pop();
        }

    }

    private void addHighlight(PriorityQueue<Highlight> nearest, BlockPos pos, int color,
                              Vec3d camera, double maxDistanceSq) {
        if (color == 0) return;
        double distanceSq = Vec3d.ofCenter(pos).squaredDistanceTo(camera);
        if (distanceSq > maxDistanceSq) return;
        Highlight target = new Highlight(pos,
                useCustomColor.get() ? boxOutlineColor.get() : color, distanceSq);
        if (nearest.size() < MAX_OUTLINES) nearest.add(target);
        else if (distanceSq < nearest.peek().distanceSq()) {
            nearest.remove();
            nearest.add(target);
        }
    }

    /** Returns zero for block entities outside the selected container categories. */
    private int containerColor(Block block) {
        if (block == Blocks.ENDER_CHEST) return renderEnderChests.get() ? ENDER_CHEST_COLOR : 0;
        if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) return renderChests.get() ? CHEST_COLOR : 0;
        if (block instanceof ShulkerBoxBlock) return renderShulkers.get() ? SHULKER_COLOR : 0;
        if (block == Blocks.BARREL) return renderBarrels.get() ? BARREL_COLOR : 0;
        if (block == Blocks.HOPPER) return renderHoppers.get() ? HOPPER_COLOR : 0;
        if (block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER)
            return renderFurnaces.get() ? FURNACE_COLOR : 0;
        if (block == Blocks.BREWING_STAND) return renderBrewing.get() ? BREWING_COLOR : 0;
        if (block == Blocks.DISPENSER || block == Blocks.DROPPER)
            return renderDispensers.get() ? DISPENSER_COLOR : 0;
        if (block == Blocks.CRAFTER) return renderCrafters.get() ? CRAFTER_COLOR : 0;
        if (block == Blocks.BEACON) return renderBeacons.get() ? BEACON_COLOR : 0;
        if (block == Blocks.SPAWNER) return renderSpawners.get() ? SPAWNER_COLOR : 0;
        if (block == Blocks.ENCHANTING_TABLE) return renderEnchanting.get() ? ENCHANTING_COLOR : 0;
        return 0;
    }

    private int farmColor(Block block) {
        if (block == Blocks.PISTON || block == Blocks.STICKY_PISTON)
            return renderPistons.get() ? PISTON_COLOR : 0;
        if (block == Blocks.OBSERVER) return renderObservers.get() ? OBSERVER_COLOR : 0;
        return 0;
    }

    /**
     * ClientWorld.getBlockEntities() can be empty while visible client chunks still
     * contain block entities (observed with this optimized Fabric profile). Read
     * each already-loaded FULL chunk's own block-entity map instead. The scan is
     * cached for five ticks and refreshed from the tick callback, never during drawing.
     */
    private void refreshLoadedBlockEntities(MinecraftClient client) {
        long worldTime = client.world.getTime();
        double requestedRange = range.get();
        Vec3d origin = EspTracers.origin(client, 0);
        int centerChunkX = Math.floorDiv((int) Math.floor(origin.x), 16);
        int centerChunkZ = Math.floorDiv((int) Math.floor(origin.z), 16);
        if (cachedWorld == client.world && worldTime >= scannedWorldTime
                && worldTime - scannedWorldTime < 5 && scannedRange == requestedRange
                && scannedChunkX == centerChunkX && scannedChunkZ == centerChunkZ) {
            return;
        }

        loadedBlockEntities.clear();
        int chunkRadius = Math.min((int) Math.ceil(requestedRange / 16.0), client.options.getClampedViewDistance() + 1);
        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                WorldChunk chunk = client.world.getChunkManager().getChunk(
                        chunkX, chunkZ, ChunkStatus.FULL, false
                );
                if (chunk != null) {
                    loadedBlockEntities.addAll(chunk.getBlockEntities().values());
                }
            }
        }

        scannedWorldTime = worldTime;
        scannedRange = requestedRange;
        cachedWorld = client.world;
        scannedChunkX = centerChunkX; scannedChunkZ = centerChunkZ;
        if (loggedWorld != client.world && !loadedBlockEntities.isEmpty()) {
            int matchingContainers = 0;
            for (BlockEntity blockEntity : loadedBlockEntities)
                if (containerColor(client.world.getBlockState(blockEntity.getPos()).getBlock()) != 0) matchingContainers++;
            loggedWorld = client.world;
            LOGGER.info("Storage ESP chunk scan: {} loaded block entities, {} recognized containers, range {} blocks",
                    loadedBlockEntities.size(), matchingContainers, requestedRange);
        }
    }

    /** Incrementally searches loaded chunk sections for pistons and observers. */
    private void scanFarmBlocks(MinecraftClient client) {
        if (!renderPistons.get() && !renderObservers.get()) {
            farmByChunk.clear(); farmQueue.clear(); visibleFarm = List.of(); activeFarmScan = null;
            farmWorld = null;
            return;
        }
        Vec3d origin = EspTracers.origin(client, 0);
        int centerX = Math.floorDiv((int) Math.floor(origin.x), 16);
        int centerZ = Math.floorDiv((int) Math.floor(origin.z), 16);
        int settingsHash = (renderPistons.get() ? 1 : 0) | (renderObservers.get() ? 2 : 0);
        settingsHash = 31 * settingsHash + Double.hashCode(range.get());
        long worldTime = client.world.getTime();
        boolean reset = farmWorld != client.world || farmCenterX != centerX
                || farmCenterZ != centerZ || farmSettingsHash != settingsHash;
        if (reset) {
            farmByChunk.clear(); visibleFarm = List.of(); activeFarmScan = null;
            beginFarmScan(client, centerX, centerZ, settingsHash, worldTime);
        } else if (worldTime >= nextFarmRescan && farmQueue.isEmpty() && activeFarmScan == null) {
            beginFarmScan(client, centerX, centerZ, settingsHash, worldTime);
        }

        int remaining = FARM_CELLS_PER_TICK;
        long deadline = System.nanoTime() + FARM_SCAN_BUDGET_NS;
        boolean changed = false;
        while (remaining > 0 && System.nanoTime() < deadline) {
            if (activeFarmScan == null) {
                if (farmQueue.isEmpty()) break;
                ChunkCoordinate coordinate = farmQueue.removeFirst();
                WorldChunk chunk = client.world.getChunkManager().getChunk(
                        coordinate.x(), coordinate.z(), ChunkStatus.FULL, false);
                if (chunk == null) {
                    changed |= farmByChunk.remove(chunkKey(coordinate.x(), coordinate.z())) != null;
                    continue;
                }
                activeFarmScan = new FarmScan(chunk, client.world.getBottomY());
            }
            WorldChunk current = client.world.getChunkManager().getChunk(
                    activeFarmScan.chunk.getPos().x, activeFarmScan.chunk.getPos().z, ChunkStatus.FULL, false);
            if (current != activeFarmScan.chunk) {
                changed |= farmByChunk.remove(chunkKey(activeFarmScan.chunk.getPos().x,
                        activeFarmScan.chunk.getPos().z)) != null;
                activeFarmScan = null;
                continue;
            }
            remaining -= activeFarmScan.advance(remaining, deadline);
            if (!activeFarmScan.complete()) break;
            long key = chunkKey(activeFarmScan.chunk.getPos().x, activeFarmScan.chunk.getPos().z);
            farmByChunk.remove(key);
            int cached = farmByChunk.values().stream().mapToInt(List::size).sum();
            int keep = Math.min(activeFarmScan.found.size(), Math.max(0, MAX_FARM_CACHED - cached));
            if (keep > 0) farmByChunk.put(key, List.copyOf(activeFarmScan.found.subList(0, keep)));
            activeFarmScan = null;
            changed = true;
        }
        if (changed) publishFarmTargets();
    }

    private void beginFarmScan(MinecraftClient client, int centerX, int centerZ,
                               int settingsHash, long worldTime) {
        farmQueue.clear();
        Vec3d origin = EspTracers.origin(client, 0);
        double requestedRange = range.get();
        int chunkRadius = Math.min((int) Math.ceil(requestedRange / 16.0),
                client.options.getClampedViewDistance() + 1);
        double rangeSquared = requestedRange * requestedRange;
        for (int x = centerX - chunkRadius; x <= centerX + chunkRadius; x++) {
            for (int z = centerZ - chunkRadius; z <= centerZ + chunkRadius; z++) {
                if (client.world.getChunkManager().getChunk(x, z, ChunkStatus.FULL, false) == null) continue;
                double dx = Math.max(0, Math.max(x * 16 - origin.x,
                        origin.x - (x * 16 + 15)));
                double dz = Math.max(0, Math.max(z * 16 - origin.z,
                        origin.z - (z * 16 + 15)));
                if (dx * dx + dz * dz <= rangeSquared) farmQueue.addLast(new ChunkCoordinate(x, z));
            }
        }
        List<ChunkCoordinate> nearest = new ArrayList<>(farmQueue);
        nearest.sort(Comparator.comparingLong(c -> {
            long dx = c.x() - centerX, dz = c.z() - centerZ;
            return dx * dx + dz * dz;
        }));
        farmQueue.clear(); farmQueue.addAll(nearest);
        farmWorld = client.world; farmCenterX = centerX; farmCenterZ = centerZ;
        farmSettingsHash = settingsHash;
        farmOriginX = (int) Math.floor(origin.x);
        farmOriginY = (int) Math.floor(origin.y);
        farmOriginZ = (int) Math.floor(origin.z);
        nextFarmRescan = worldTime + FARM_RESCAN_TICKS;
    }

    private final class FarmScan {
        final WorldChunk chunk;
        final int bottomY;
        final List<FarmTarget> found = new ArrayList<>();
        int sectionIndex, cell;
        boolean checkedSection;

        FarmScan(WorldChunk chunk, int bottomY) { this.chunk = chunk; this.bottomY = bottomY; }

        boolean complete() {
            return sectionIndex >= chunk.getSectionArray().length || found.size() >= MAX_FARM_PER_CHUNK;
        }

        int advance(int limit, long deadline) {
            int examined = 0;
            var sections = chunk.getSectionArray();
            while (examined < limit && !complete()) {
                if ((examined & 63) == 0 && System.nanoTime() >= deadline) break;
                var section = sections[sectionIndex];
                if (!checkedSection) {
                    if (section == null || section.isEmpty()
                            || !section.hasAny(state -> farmColor(state.getBlock()) != 0)) {
                        sectionIndex++; examined++; continue;
                    }
                    checkedSection = true;
                }
                int x = cell & 15, z = (cell >> 4) & 15, y = (cell >> 8) & 15;
                Block block = section.getBlockState(x, y, z).getBlock();
                if (farmColor(block) != 0) {
                    int wx = chunk.getPos().getStartX() + x;
                    int wy = bottomY + sectionIndex * 16 + y;
                    int wz = chunk.getPos().getStartZ() + z;
                    double dx = wx - farmOriginX, dy = wy - farmOriginY, dz = wz - farmOriginZ;
                    if (dx * dx + dy * dy + dz * dz <= range.get() * range.get())
                        found.add(new FarmTarget(new BlockPos(wx, wy, wz), block));
                }
                examined++;
                if (++cell == 4096) { cell = 0; sectionIndex++; checkedSection = false; }
            }
            return examined;
        }
    }

    private void publishFarmTargets() {
        List<FarmTarget> combined = new ArrayList<>();
        for (List<FarmTarget> targets : farmByChunk.values()) combined.addAll(targets);
        BlockPos origin = new BlockPos(farmOriginX, farmOriginY, farmOriginZ);
        combined.sort(Comparator.comparingDouble(target -> target.pos().getSquaredDistance(origin)));
        visibleFarm = List.copyOf(combined.subList(0, Math.min(MAX_FARM_VISIBLE, combined.size())));
    }

    private static long chunkKey(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }
    private record ChunkCoordinate(int x, int z) { }
    private record FarmTarget(BlockPos pos, Block block) { }
    private record Highlight(BlockPos pos, int color, double distanceSq) { }
}

