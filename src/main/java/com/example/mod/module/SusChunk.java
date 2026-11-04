package com.example.mod.module;

import com.example.mod.license.LicenseManager;
import com.example.mod.module.suschunk.SusChunkEvidence;
import com.example.mod.module.suschunk.SusChunkEvidence.Counts;
import com.example.mod.module.suschunk.SusChunkEvidence.Options;
import com.example.mod.module.suschunk.SusChunkEvidence.Result;
import com.example.mod.module.suschunk.SusChunkEvidence.Signal;
import com.example.mod.module.suschunk.SusChunkGeodes;
import com.example.mod.module.suschunk.SusChunkGeodes.Cell;
import com.example.mod.module.suschunk.SusChunkGeodes.Material;
import com.example.mod.module.suschunk.SusChunkGeodes.Placement;
import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.ColorSetting;
import com.example.mod.setting.IntegerSetting;
import com.example.mod.setting.NumberSetting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

/** Scans loaded chunks for signs of player construction within a tick budget. */
public final class SusChunk extends Module {
    private static final int MAX_CHUNKS = 1024;
    private static final int MAX_TRACKED_BLOCKS = 4096;
    private static final int MAX_GEODE_CELLS = 4096;
    private static final int MAX_COMBINED_GEODE_CELLS = 8192;
    private static final int MAX_PLACEMENTS = 512;
    private static final int DISCOVERY_INTERVAL = 20;
    private static final int RESCAN_INTERVAL = 600;
    private static final long CHANGE_LIFETIME = 12_000;
    private static final Map<Block, Kind> KINDS = new HashMap<>();

    private final IntegerSetting scanRadius = addSetting(new IntegerSetting("Simulation distance", 4, 1, 16));
    private final IntegerSetting sensitivity = addSetting(new IntegerSetting("Sensitivity", 3, 1, 10));
    private final BooleanSetting smartAdjustment = addSetting(new BooleanSetting("Smart adjustment", true));
    private final BooleanSetting separateDetections = addSetting(new BooleanSetting("Separate detections", false));
    private final BooleanSetting kelp = addSetting(new BooleanSetting("Kelp", false));
    private final BooleanSetting caveVines = addSetting(new BooleanSetting("Cave vines", false));
    private final BooleanSetting vines = addSetting(new BooleanSetting("Vines", false));
    private final BooleanSetting modifiedGeodes = addSetting(new BooleanSetting("Amethyst", true));
    private final BooleanSetting bamboo = addSetting(new BooleanSetting("Bamboo", false));
    private final BooleanSetting cocoa = addSetting(new BooleanSetting("Cocoa", false));
    private final BooleanSetting beeNest = addSetting(new BooleanSetting("Bee nest", false));
    private final IntegerSetting opacity = addSetting(new IntegerSetting("Alpha", 40, 5, 85));
    private final ColorSetting highlightColor = addSetting(new ColorSetting("Chunk color", 0xFFFF3030));
    private final BooleanSetting holeFilter = addSetting(new BooleanSetting("Hole filter", false));
    private final BooleanSetting advanced = addSetting(new BooleanSetting("More settings", false));

    private final BooleanSetting reasonAlerts = addSetting(new BooleanSetting("Reason alerts", true));
    private final BooleanSetting border = addSetting(new BooleanSetting("Chunk border", true));
    private final IntegerSetting minimumBlocks = addSetting(new IntegerSetting("Minimum indicator blocks", 2, 2, 16));
    private final IntegerSetting minimumGroups = addSetting(new IntegerSetting("Independent signal groups", 2, 2, 5));
    private final BooleanSetting storage = addSetting(new BooleanSetting("Storage and shulkers", true));
    private final BooleanSetting utilities = addSetting(new BooleanSetting("Utilities and beds", true));
    private final BooleanSetting redstone = addSetting(new BooleanSetting("Redstone components", true));
    private final BooleanSetting construction = addSetting(new BooleanSetting("Manufactured blocks", true));
    private final BooleanSetting lighting = addSetting(new BooleanSetting("Supporting placed lights", true));
    private final BooleanSetting ignoreStructures = addSetting(new BooleanSetting("Filter natural structures", true));
    private final BooleanSetting strongAnchor = addSetting(new BooleanSetting("Require stronger construction", true));
    private final NumberSetting scanBudget = addSetting(new NumberSetting("Scan budget ms", 2.0, 0.5, 8.0, 0.5));
    private final IntegerSetting blocksPerTick = addSetting(new IntegerSetting("Max scan cells per tick", 8192, 512, 32768));
    private final IntegerSetting maxAlerts = addSetting(new IntegerSetting("Alerts per minute", 4, 1, 10));
    private final BooleanSetting followTerrain = addSetting(new BooleanSetting("Follow terrain surface", true));
    private final IntegerSetting flatHeight = addSetting(new IntegerSetting("Flat overlay Y", 64, -64, 320));
    private final IntegerSetting renderLimit = addSetting(new IntegerSetting("Maximum rendered chunks", 64, 1, 128));

    // World, packet, and render callbacks all execute on the client thread. No worker reads live chunks.
    private final Map<Long, Snapshot> cache = new HashMap<>();
    private final Map<Long, Integer> revisions = new HashMap<>();
    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private final Set<Long> dirty = new HashSet<>();
    private final Set<Long> fullInvalidations = new HashSet<>();
    private final Map<Long, Set<Integer>> changedSections = new HashMap<>();
    private final LinkedHashMap<Cell, Long> removedBudding = new LinkedHashMap<>();
    private final ArrayDeque<Long> alertTimes = new ArrayDeque<>();
    private final Map<Long, Long> notifiedAt = new HashMap<>();
    private List<RenderFlag> renderedFlags = List.of();
    private List<ChunkFlag> publishedFlags = List.of();
    private World observedWorld;
    private ChunkWork work;
    private long ticks;
    private int lastRadius, lastOptionsHash;
    private boolean publishNeeded;

    public record ChunkFlag(int chunkX, int chunkZ, int score, int indicatorBlocks, String reason) {}

    public SusChunk() {
        super("SusChunk", false, Category.BASEFINDER);
        scanRadius.describe("Scan radius", "Scan already loaded chunks within this many chunks of you. This does not change the server's simulation distance. Default: 4.");
        sensitivity.describe("Sensitivity", "Higher values require stronger evidence and show fewer possible finds. Default: 3.");
        smartAdjustment.describe("Smart adjustment", "Keep the strongest nearby highlight when several adjacent chunks look suspicious. Separate detections overrides this.");
        separateDetections.describe("Separate detections", "Draw each clue in its own colored stripe and show neighboring chunks separately. This turns off nearby highlight merging.");
        kelp.describe("Kelp", "Use clusters of mature kelp as a supporting clue; natural kelp alone is not proof.");
        caveVines.describe("Cave vines", "Use clusters of berry-bearing cave vines as a supporting clue.");
        vines.describe("Vines", "Use dense vine growth as a supporting clue.");
        bamboo.describe("Bamboo", "Use dense bamboo growth as a supporting clue.");
        cocoa.describe("Cocoa", "Use clusters of ripe cocoa pods as a supporting clue.");
        beeNest.describe("Bee nest", "Use full natural bee nests as a supporting clue.");
        reasonAlerts.describe("Chat alerts", "Send a chat message explaining why a chunk was flagged.");
        highlightColor.describe("Chunk color", "Click to cycle the color of combined highlights. Separate detections uses clue colors instead.");
        opacity.describe("Alpha", "Higher values make the chunk overlay less transparent.");
        border.describe("Chunk border", "Draw a clear edge around each highlighted chunk.");
        holeFilter.describe("Hole filter", "Hide suspicious chunk highlights near a narrow surface hole. Can also hide real finds.");
        advanced.describe("More settings", "Show extra detection and performance controls. The defaults are recommended.");

        minimumBlocks.describe("Minimum blocks", "Require this many supported building blocks before flagging a chunk.");
        minimumGroups.describe("Clue types", "Require several clue categories, such as storage plus redstone. Default: 2.");
        storage.describe("Containers", "Count chests, barrels, hoppers, ender chests and shulker boxes as clues.");
        utilities.describe("Beds / workstations", "Count beds, furnaces, anvils and other crafted utility blocks as clues.");
        redstone.describe("Redstone", "Use redstone components as clues that a player built here.");
        construction.describe("Concrete builds", "Count groups of concrete or concrete powder. Ordinary terrain is ignored.");
        lighting.describe("Placed lights", "Torches and other placed lights add support. Lights alone never flag a chunk.");
        modifiedGeodes.describe("Amethyst", "Look for construction or observed changes inside amethyst geodes.");
        ignoreStructures.describe("Ignore structures", "Reduce flags from villages, mineshafts and other generated structures.");
        strongAnchor.describe("Strong clues only", "Require stronger construction evidence. Keep on to reduce false alarms.");
        scanBudget.describe("Scan time (ms)", "Maximum target time spent scanning each tick. Lower values spread work out. Default: 2.");
        blocksPerTick.describe("Cells per tick", "Maximum cells examined each tick. Lower values scan more slowly. Default: 8192.");
        maxAlerts.describe("Alerts / minute", "Limit how many new chunk alerts appear each minute.");
        followTerrain.describe("Follow terrain", "Place highlights on the terrain. Turn off to use a fixed height.");
        flatHeight.describe("Fixed height", "World Y level for highlights when following the ground is off.");
        renderLimit.describe("Max highlights", "Limit how many flagged chunks are drawn at once. Default: 64.");

        // Keep original names as persistence keys, even when the visible labels are simpler.
        for (var setting : List.of(reasonAlerts, border, minimumBlocks, minimumGroups, storage,
                utilities, redstone, construction, lighting, ignoreStructures, strongAnchor,
                scanBudget, blocksPerTick, followTerrain, renderLimit)) {
            setting.visibleWhen(advanced::get);
        }
        maxAlerts.visibleWhen(() -> advanced.get() && reasonAlerts.get());
        flatHeight.visibleWhen(() -> advanced.get() && !followTerrain.get());
    }
    public List<ChunkFlag> flaggedChunks() { return publishedFlags; }
    public int scannedChunks() { return cache.size(); }

    @Override protected void onEnable() { clear(); }
    @Override protected void onDisable() { clear(); }
    @Override public void onWorldLeave() { clear(); }

    private void clear() {
        cache.clear(); revisions.clear(); queue.clear(); queued.clear(); dirty.clear();
        fullInvalidations.clear(); changedSections.clear();
        removedBudding.clear(); alertTimes.clear(); notifiedAt.clear();
        renderedFlags = List.of(); publishedFlags = List.of();
        observedWorld = null; work = null; ticks = 0; lastRadius = 0; lastOptionsHash = 0;
        publishNeeded = false;
    }

    public void tick(MinecraftClient client) {
        if (!ready(client) || client.isPaused()) return;
        ticks++;
        if (ticks == 1 || ticks % DISCOVERY_INTERVAL == 0 || lastRadius != scanRadius.get()) {
            discover(client);
            cleanup(client);
            lastRadius = scanRadius.get();
            publishNeeded = true;
        }
        Options options = options();
        int optionsHash = 31 * options.hashCode() + (smartAdjustment.get() ? 2 : 0)
                + (separateDetections.get() ? 4 : 0) + (holeFilter.get() ? 8 : 0);
        if (optionsHash != lastOptionsHash) { lastOptionsHash = optionsHash; publishNeeded = true; }

        long deadline = System.nanoTime() + (long) (scanBudget.get() * 1_000_000.0);
        int remaining = blocksPerTick.get();
        while (remaining > 0 && System.nanoTime() < deadline) {
            if (work == null && !startNext(client)) break;
            if (loaded(client, work.chunkX, work.chunkZ) != work.source
                    || revisions.getOrDefault(work.key, 0) != work.revision
                    || !inRange(client, work.chunkX, work.chunkZ, scanRadiusBlocks())) {
                long invalidKey = work.key;
                if (work.fullScan) fullInvalidations.add(invalidKey);
                else changedSections.computeIfAbsent(invalidKey, k -> new HashSet<>()).addAll(work.requestedSections);
                work = null;
                if (loaded(client, chunkX(invalidKey), chunkZ(invalidKey)) != null) enqueue(client, invalidKey, false);
                continue;
            }
            if (!work.scanComplete) {
                remaining -= Math.max(1, work.advance(Math.min(256, remaining)));
                if (!work.scanComplete) continue;
                prepareGeodeAnalysis(work);
            }
            if (work.geodeJob != null && !work.geodeJob.complete()) {
                remaining -= Math.max(1, work.geodeJob.advance(Math.min(256, remaining)));
                continue;
            }
            commit(client, work);
            work = null;
        }
        if (publishNeeded) publish(client, options);
    }

    /** Full chunk packets invalidate cached observations; analysis happens within the tick budget. */
    public void onChunkData(ChunkDataS2CPacket packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (ready(client)) enqueue(client, key(packet.getChunkX(), packet.getChunkZ()), true);
    }

    /** Coalesce updates; property changes such as repeater power never restart a construction scan. */
    public void onBlockUpdate(BlockPos pos, BlockState received) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ready(client)) return;
        long chunkKey = key(pos.getX() >> 4, pos.getZ() >> 4);
        Snapshot previous = cache.get(chunkKey);
        if (previous != null && pos.getY() >= previous.surface[index(pos.getX(), pos.getZ())] - 1) {
            previous.surface[index(pos.getX(), pos.getZ())] = client.world.getTopY(
                    Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        }
        Block old = previous == null ? null : previous.tracked.get(pos);
        if (old == null && work != null && work.key == chunkKey) old = work.tracked.get(pos);
        Cell cell = cell(pos);
        boolean restored = received.isOf(Blocks.BUDDING_AMETHYST) && removedBudding.remove(cell) != null;
        if (old == received.getBlock() && !restored && !isGrowthSignal(kind(received.getBlock()).signal)) return;
        if (old == Blocks.BUDDING_AMETHYST && !received.isOf(Blocks.BUDDING_AMETHYST)) rememberRemoval(cell);
        if (old != null || kind(received.getBlock()).relevant()
                || (received.isAir() && previous != null && !previous.tracked.isEmpty())) {
            invalidateSection(client, chunkKey, (pos.getY() - client.world.getBottomY()) >> 4);
        }
    }

    private boolean ready(MinecraftClient client) {
        if (!isEnabled() || !LicenseManager.isLicensed()) return false;
        if (client.world == null || client.player == null) { if (observedWorld != null) clear(); return false; }
        if (observedWorld != client.world) { clear(); observedWorld = client.world; }
        return true;
    }

    private void discover(MinecraftClient client) {
        int cx = client.player.getBlockX() >> 4, cz = client.player.getBlockZ() >> 4;
        int radius = Math.min(scanRadius.get() + 1, client.options.getClampedViewDistance() + 1);
        List<Long> candidates = new ArrayList<>();
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                if (!inRange(client, x, z, scanRadiusBlocks())) continue;
                WorldChunk chunk = loaded(client, x, z);
                if (chunk == null) continue;
                long key = key(x, z);
                Snapshot snapshot = cache.get(key);
                // A packet can invalidate a cached chunk while the bounded queue is full.
                // Keep retrying that dirty chunk on discovery ticks instead of leaving it hidden.
                if (snapshot == null || snapshot.source != chunk || dirty.contains(key)
                        || ticks - snapshot.lastFullScanAt >= RESCAN_INTERVAL) {
                    fullInvalidations.add(key);
                    candidates.add(key);
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(k -> distanceSquared(client, chunkX(k), chunkZ(k))));
        for (long key : candidates) enqueue(client, key, false);
    }

    private void enqueue(MinecraftClient client, long key, boolean invalidate) {
        if (!inRange(client, chunkX(key), chunkZ(key), scanRadiusBlocks())) return;
        if (invalidate) {
            revisions.merge(key, 1, Integer::sum);
            dirty.add(key);
            fullInvalidations.add(key);
            publishNeeded = true;
        } else if (work != null && work.key == key) return;
        if (queue.size() < MAX_CHUNKS && queued.add(key)) {
            if (invalidate) queue.addFirst(key); else queue.addLast(key);
        }
    }

    private void invalidateSection(MinecraftClient client, long key, int section) {
        if (!inRange(client, chunkX(key), chunkZ(key), scanRadiusBlocks())) return;
        revisions.merge(key, 1, Integer::sum);
        dirty.add(key);
        changedSections.computeIfAbsent(key, k -> new HashSet<>()).add(section);
        publishNeeded = true;
        if (queue.size() < MAX_CHUNKS && queued.add(key)) queue.addFirst(key);
    }

    private boolean startNext(MinecraftClient client) {
        while (!queue.isEmpty()) {
            long key = queue.removeFirst(); queued.remove(key);
            WorldChunk chunk = loaded(client, chunkX(key), chunkZ(key));
            if (chunk == null || !inRange(client, chunkX(key), chunkZ(key), scanRadiusBlocks())) continue;
            Set<Integer> sections = changedSections.remove(key);
            boolean full = fullInvalidations.remove(key);
            work = new ChunkWork(chunk, revisions.getOrDefault(key, 0), client.world.getRegistryKey() == World.OVERWORLD,
                    cache.get(key), full ? null : sections);
            return true;
        }
        return false;
    }

    private void prepareGeodeAnalysis(ChunkWork current) {
        for (var entry : current.geodes.entrySet()) {
            if (entry.getValue() == Material.BUDDING) removedBudding.remove(entry.getKey());
        }
        Snapshot old = cache.get(current.key);
        if (old != null && !current.geodesOverflow) {
            for (var entry : old.geodes.entrySet()) {
                Cell previous = entry.getKey();
                if (entry.getValue() == Material.BUDDING && current.geodes.get(previous) != Material.BUDDING
                        && !current.source.getBlockState(new BlockPos(previous.x(), previous.y(), previous.z())).isOf(Blocks.BUDDING_AMETHYST)) {
                    rememberRemoval(entry.getKey());
                }
            }
        }
        current.geometrySignature = geometrySignature(current.chunkX, current.chunkZ, current.geodes, current.placements);
        if (!current.overworld || current.geodesOverflow) return;
        Map<Cell, Material> cells = new HashMap<>(current.geodes);
        List<Placement> placements = new ArrayList<>(current.placements);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                Snapshot neighbour = cache.get(key(current.chunkX + dx, current.chunkZ + dz));
                if (neighbour == null || neighbour.geodesOverflow || dirty.contains(neighbour.key)) continue;
                if (cells.size() + neighbour.geodes.size() > MAX_COMBINED_GEODE_CELLS) return;
                cells.putAll(neighbour.geodes);
                for (Placement placement : neighbour.placements) {
                    if (placements.size() == MAX_PLACEMENTS * 3) break;
                    placements.add(placement);
                }
            }
        }
        for (Cell removed : removedBudding.keySet()) {
            if (Math.abs(Math.floorDiv(removed.x(), 16) - current.chunkX) <= 1
                    && Math.abs(Math.floorDiv(removed.z(), 16) - current.chunkZ) <= 1) {
                cells.put(removed, Material.REMOVED_BUDDING);
            }
        }
        if (cells.size() > MAX_COMBINED_GEODE_CELLS || cells.isEmpty() || placements.isEmpty()) return;
        current.geodeJob = new SusChunkGeodes.Job(cells, placements, current.chunkX, current.chunkZ);
    }

    private void commit(MinecraftClient client, ChunkWork current) {
        if (current.geodeJob != null) current.counts.set(Signal.GEODE_MODIFICATION, current.geodeJob.modifiedComponents());
        int[] surface = new int[256];
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            surface[index(x, z)] = client.world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    current.chunkX * 16 + x, current.chunkZ * 16 + z);
        }
        if (cache.size() >= MAX_CHUNKS && !cache.containsKey(current.key)) {
            long oldest = cache.values().stream().min(Comparator.comparingLong(s -> s.scannedAt)).orElseThrow().key;
            cache.remove(oldest);
        }
        cache.put(current.key, new Snapshot(current, surface, ticks));
        if (current.fullScan && !queued.contains(current.key)) fullInvalidations.remove(current.key);
        dirty.remove(current.key);
        publishNeeded = true;
        // Re-evaluate adjacent cached shells when their shared geometry first arrives or changes.
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            long key = key(current.chunkX + dx, current.chunkZ + dz);
            Snapshot neighbour = cache.get(key);
            if (neighbour == null || neighbour.geodes.isEmpty() || dirty.contains(key)) continue;
            if (neighbour.geometrySignature != geometrySignature(chunkX(key), chunkZ(key), neighbour.geodes, neighbour.placements)) {
                changedSections.computeIfAbsent(key, k -> new HashSet<>());
                enqueue(client, key, false);
            }
        }
    }

    private int geometrySignature(int cx, int cz, Map<Cell, Material> own, List<Placement> placements) {
        int signature = 31 * own.hashCode() + placements.hashCode();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            long key = key(cx + dx, cz + dz);
            Snapshot neighbour = cache.get(key);
            signature = 31 * signature + (neighbour == null || dirty.contains(key) || neighbour.geodesOverflow ? 0
                    : 31 * neighbour.geodes.hashCode() + neighbour.placements.hashCode());
        }
        for (Cell removed : removedBudding.keySet()) {
            if (Math.abs(Math.floorDiv(removed.x(), 16) - cx) <= 1 && Math.abs(Math.floorDiv(removed.z(), 16) - cz) <= 1) {
                signature += removed.hashCode();
            }
        }
        return signature;
    }

    private void rememberRemoval(Cell cell) {
        removedBudding.put(cell, ticks);
        while (removedBudding.size() > 1024) removedBudding.remove(removedBudding.keySet().iterator().next());
    }

    private void cleanup(MinecraftClient client) {
        cache.entrySet().removeIf(entry -> {
            Snapshot snapshot = entry.getValue();
            return !inRange(client, chunkX(snapshot.key), chunkZ(snapshot.key), scanRadiusBlocks() + 16)
                    || loaded(client, chunkX(snapshot.key), chunkZ(snapshot.key)) != snapshot.source;
        });
        while (cache.size() > MAX_CHUNKS) {
            long oldest = cache.values().stream().min(Comparator.comparingLong(s -> s.scannedAt)).orElseThrow().key;
            cache.remove(oldest);
        }
        queue.removeIf(key -> !inRange(client, chunkX(key), chunkZ(key), scanRadiusBlocks())
                || loaded(client, chunkX(key), chunkZ(key)) == null);
        queued.retainAll(new HashSet<>(queue));
        Set<Long> live = new HashSet<>(cache.keySet()); live.addAll(queued);
        if (work != null) live.add(work.key);
        revisions.keySet().retainAll(live); dirty.retainAll(live); notifiedAt.keySet().retainAll(live);
        fullInvalidations.retainAll(live); changedSections.keySet().retainAll(live);
        removedBudding.entrySet().removeIf(entry -> ticks - entry.getValue() > CHANGE_LIFETIME
                || !inRange(client, Math.floorDiv(entry.getKey().x(), 16), Math.floorDiv(entry.getKey().z(), 16), scanRadiusBlocks() + 16)
                || loaded(client, Math.floorDiv(entry.getKey().x(), 16), Math.floorDiv(entry.getKey().z(), 16)) == null);
    }

    private Options options() {
        EnumSet<Signal> enabled = EnumSet.noneOf(Signal.class);
        if (kelp.get()) enabled.add(Signal.MATURE_KELP);
        if (caveVines.get()) enabled.add(Signal.BERRY_VINES);
        if (vines.get()) enabled.add(Signal.DENSE_VINES);
        if (bamboo.get()) enabled.add(Signal.BAMBOO_GROWTH);
        if (cocoa.get()) enabled.add(Signal.RIPE_COCOA);
        if (beeNest.get()) enabled.add(Signal.FULL_BEE_NEST);
        if (storage.get()) { enabled.add(Signal.STORAGE); enabled.add(Signal.PORTABLE_STORAGE); }
        if (utilities.get()) { enabled.add(Signal.WORKSTATION); enabled.add(Signal.SPECIAL_UTILITY); enabled.add(Signal.BED); }
        if (redstone.get()) enabled.add(Signal.REDSTONE);
        if (construction.get()) enabled.add(Signal.MANUFACTURED);
        if (lighting.get()) enabled.add(Signal.LIGHT);
        if (modifiedGeodes.get()) enabled.add(Signal.GEODE_MODIFICATION);
        return new Options(minimumBlocks.get(), Math.max(4, 8 + (sensitivity.get() - 3) * 2),
                minimumGroups.get(), ignoreStructures.get(), strongAnchor.get(), enabled);
    }

    private void publish(MinecraftClient client, Options options) {
        List<RenderFlag> flags = new ArrayList<>();
        for (Snapshot snapshot : cache.values()) {
            if (dirty.contains(snapshot.key) || !inRange(client, chunkX(snapshot.key), chunkZ(snapshot.key), scanRadiusBlocks())) continue;
            Result result = SusChunkEvidence.evaluate(snapshot.counts, nearbyStructureContext(client, snapshot), options);
            if (result.flagged()) flags.add(new RenderFlag(snapshot, result));
        }
        if (holeFilter.get()) flags.removeIf(flag -> nearNarrowHole(flag.snapshot));
        if (smartAdjustment.get() && !separateDetections.get()) {
            flags.sort(Comparator.comparingInt((RenderFlag flag) -> flag.result.score()).reversed());
            List<RenderFlag> strongest = new ArrayList<>();
            Set<Long> covered = new HashSet<>();
            for (RenderFlag candidate : flags) {
                if (covered.contains(candidate.snapshot.key)) continue;
                strongest.add(candidate);
                int cx = chunkX(candidate.snapshot.key), cz = chunkZ(candidate.snapshot.key);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                    covered.add(key(cx + dx, cz + dz));
            }
            flags = strongest;
        }
        if (separateDetections.get()) {
            List<RenderFlag> expanded = new ArrayList<>();
            for (RenderFlag flag : flags) {
                List<Signal> signals = SusChunkEvidence.detectedSignals(flag.snapshot.counts, options);
                if (signals.isEmpty()) { expanded.add(flag); continue; }
                for (int index = 0; index < signals.size(); index++) {
                    expanded.add(new RenderFlag(flag.snapshot, flag.result, signals.get(index), index, signals.size()));
                }
            }
            flags = expanded;
        }
        flags.sort(Comparator.comparingDouble(flag -> distanceSquared(client, chunkX(flag.snapshot.key), chunkZ(flag.snapshot.key))));
        renderedFlags = List.copyOf(flags);
        publishedFlags = flags.stream().map(flag -> new ChunkFlag(chunkX(flag.snapshot.key), chunkZ(flag.snapshot.key),
                flag.result.score(), flag.result.indicatorBlocks(),
                flag.signal == null ? flag.result.reasonText() : flag.signal.label)).toList();
        publishNeeded = false;
        if (reasonAlerts.get()) for (RenderFlag flag : flags) notifyReason(client, flag);
    }

    private boolean nearNarrowHole(Snapshot snapshot) {
        int cx = chunkX(snapshot.key), cz = chunkZ(snapshot.key);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            Snapshot nearby = cache.get(key(cx + dx, cz + dz));
            if (nearby != null && hasNarrowSurfaceHole(nearby.surface)) return true;
        }
        return false;
    }

    private static boolean hasNarrowSurfaceHole(int[] surface) {
        for (int x = 2; x < 14; x++) for (int z = 2; z < 14; z++) {
            int floor = surface[index(x, z)];
            if (surface[index(x - 2, z)] - floor >= 4
                    && surface[index(x + 2, z)] - floor >= 4
                    && surface[index(x, z - 2)] - floor >= 4
                    && surface[index(x, z + 2)] - floor >= 4) return true;
        }
        return false;
    }

    private boolean nearbyStructureContext(MinecraftClient client, Snapshot own) {
        if (own.structureContext) return true;
        int cx = chunkX(own.key), cz = chunkZ(own.key);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            long key = key(cx + dx, cz + dz);
            Snapshot neighbour = cache.get(key);
            if (neighbour != null && neighbour.structureContext) return true;
            // Defer weaker flags while neighbouring loaded structure context is still unknown.
            if (neighbour == null && inRange(client, cx + dx, cz + dz, scanRadiusBlocks())
                    && loaded(client, cx + dx, cz + dz) != null) return true;
        }
        return false;
    }

    private void notifyReason(MinecraftClient client, RenderFlag flag) {
        long now = System.nanoTime();
        long minute = 60_000_000_000L;
        while (!alertTimes.isEmpty() && now - alertTimes.peekFirst() >= minute) alertTimes.removeFirst();
        Long previousAlert = notifiedAt.get(flag.snapshot.key);
        if (alertTimes.size() >= maxAlerts.get() || (previousAlert != null && now - previousAlert < minute)) return;
        notifiedAt.put(flag.snapshot.key, now); alertTimes.addLast(now);
        client.player.sendMessage(Text.literal("[limitless SusChunk] Chunk " + chunkX(flag.snapshot.key) + ", "
                + chunkZ(flag.snapshot.key) + " | evidence score " + flag.result.score() + " | "
                + (flag.signal == null ? flag.result.reasonText() : flag.signal.label))
                .formatted(Formatting.RED), false);
    }

    /** Cached height samples avoid world scans during rendering; only top faces are emitted. */
    public void renderWorld(WorldRenderContext context) {
        if (!isEnabled() || renderedFlags.isEmpty()) return;
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) return;
        Vec3d camera = context.worldState().cameraRenderState.pos;
        VertexConsumer fills = consumers.getBuffer(EspRenderLayers.TERRAIN_HIGHLIGHT);
        matrices.push();
        try {
            matrices.translate(-camera.x, -camera.y, -camera.z);
            int drawn = 0;
            for (RenderFlag flag : renderedFlags) {
                if (drawn++ >= renderLimit.get()) break;
                Snapshot snapshot = flag.snapshot;
                int sx = chunkX(snapshot.key) * 16, sz = chunkZ(snapshot.key) * 16;
                int rgb = flag.signal == null ? highlightColor.get() & 0x00ffffff : signalColor(flag.signal);
                int fillColor = rgb | (opacity.get() * 255 / 100 << 24);
                int lineColor = rgb | 0xdd000000;
                if (!followTerrain.get()) {
                    float left = sx + 16f * flag.slot / flag.stripes;
                    float right = sx + 16f * (flag.slot + 1) / flag.stripes;
                    face(matrices.peek(), fills, left, flatHeight.get() + 0.04f, sz, right, sz + 16, fillColor);
                    if (border.get() && flag.stripes == 1) perimeter(matrices.peek(), fills, sx, flatHeight.get() + 0.05f, sz, lineColor);
                    continue;
                }
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                    if (x % flag.stripes != flag.slot) continue;
                    float y = snapshot.surface[index(x, z)] + 0.035f;
                    face(matrices.peek(), fills, sx + x, y, sz + z, sx + x + 1, sz + z + 1, fillColor);
                    if (!border.get()) continue;
                    if (x == 0) face(matrices.peek(), fills, sx, y + 0.01f, sz + z, sx + 0.05f, sz + z + 1, lineColor);
                    if (x == 15) face(matrices.peek(), fills, sx + 15.95f, y + 0.01f, sz + z, sx + 16, sz + z + 1, lineColor);
                    if (z == 0) face(matrices.peek(), fills, sx + x, y + 0.01f, sz, sx + x + 1, sz + 0.05f, lineColor);
                    if (z == 15) face(matrices.peek(), fills, sx + x, y + 0.01f, sz + 15.95f, sx + x + 1, sz + 16, lineColor);
                }
            }
        } finally { matrices.pop(); }
    }

    private static int signalColor(Signal signal) {
        return switch (signal) {
            case MATURE_KELP, BERRY_VINES, DENSE_VINES -> 0x32C76A;
            case BAMBOO_GROWTH, RIPE_COCOA -> 0xE2B34B;
            case FULL_BEE_NEST -> 0xF9A03F;
            case GEODE_MODIFICATION -> 0xB77BFF;
            case PORTABLE_STORAGE, STORAGE -> 0x43BFEF;
            case REDSTONE -> 0xFF5757;
            case LIGHT -> 0xFFE36B;
            default -> 0xC7A0F7;
        };
    }

    private static void perimeter(MatrixStack.Entry matrix, VertexConsumer consumer, float x, float y, float z, int color) {
        face(matrix, consumer, x, y, z, x + 16, z + 0.05f, color);
        face(matrix, consumer, x, y, z + 15.95f, x + 16, z + 16, color);
        face(matrix, consumer, x, y, z, x + 0.05f, z + 16, color);
        face(matrix, consumer, x + 15.95f, y, z, x + 16, z + 16, color);
    }

    private static void face(MatrixStack.Entry matrix, VertexConsumer consumer, float x0, float y, float z0,
                             float x1, float z1, int color) {
        consumer.vertex(matrix, x0, y, z0).color(color);
        consumer.vertex(matrix, x0, y, z1).color(color);
        consumer.vertex(matrix, x1, y, z1).color(color);
        consumer.vertex(matrix, x1, y, z0).color(color);
    }

    private enum Marker { MINESHAFT, SPAWNER, MOSSY, STRONGHOLD, TRIAL, VILLAGE, ANCIENT, END_CITY }
    private record Kind(Signal signal, Marker marker, Material material) {
        boolean relevant() { return signal != null || marker != null || material != null; }
    }

    private static Kind kind(Block block) { return KINDS.computeIfAbsent(block, SusChunk::classify); }
    private static Kind classify(Block block) {
        Signal signal = null; Marker marker = null; Material material = null;
        if (block == Blocks.KELP) signal = Signal.MATURE_KELP;
        else if (block == Blocks.CAVE_VINES || block == Blocks.CAVE_VINES_PLANT) signal = Signal.BERRY_VINES;
        else if (block == Blocks.VINE) signal = Signal.DENSE_VINES;
        else if (block == Blocks.BAMBOO) signal = Signal.BAMBOO_GROWTH;
        else if (block == Blocks.COCOA) signal = Signal.RIPE_COCOA;
        else if (block == Blocks.BEE_NEST) signal = Signal.FULL_BEE_NEST;
        else if (block instanceof ShulkerBoxBlock || block == Blocks.ENDER_CHEST) signal = Signal.PORTABLE_STORAGE;
        else if (block == Blocks.CRAFTER || block == Blocks.ENCHANTING_TABLE || block == Blocks.BEACON || block == Blocks.RESPAWN_ANCHOR) signal = Signal.SPECIAL_UTILITY;
        else if (block instanceof ChestBlock || block == Blocks.BARREL || block == Blocks.HOPPER) signal = Signal.STORAGE;
        else if (block instanceof AbstractFurnaceBlock || block == Blocks.CRAFTING_TABLE || block == Blocks.SMITHING_TABLE
                || block == Blocks.CARTOGRAPHY_TABLE || block == Blocks.FLETCHING_TABLE || block == Blocks.STONECUTTER
                || block == Blocks.GRINDSTONE || block == Blocks.LOOM || block == Blocks.BREWING_STAND
                || block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL || block == Blocks.DAMAGED_ANVIL) signal = Signal.WORKSTATION;
        else if (block instanceof BedBlock) signal = Signal.BED;
        else if (block == Blocks.REDSTONE_WIRE || block == Blocks.REPEATER || block == Blocks.COMPARATOR
                || block == Blocks.OBSERVER || block == Blocks.PISTON || block == Blocks.STICKY_PISTON
                || block == Blocks.DISPENSER || block == Blocks.DROPPER || block == Blocks.REDSTONE_BLOCK
                || block == Blocks.REDSTONE_TORCH || block == Blocks.REDSTONE_WALL_TORCH || block == Blocks.TARGET) signal = Signal.REDSTONE;
        else if (block == Blocks.TORCH || block == Blocks.WALL_TORCH || block == Blocks.SOUL_TORCH || block == Blocks.SOUL_WALL_TORCH
                || block == Blocks.LANTERN || block == Blocks.SOUL_LANTERN || block == Blocks.END_ROD) signal = Signal.LIGHT;
        String path = Registries.BLOCK.getId(block).getPath();
        if (signal == null && (path.endsWith("_concrete") || path.endsWith("_concrete_powder"))) signal = Signal.MANUFACTURED;
        if (block == Blocks.COBWEB || block == Blocks.RAIL || block == Blocks.POWERED_RAIL || block == Blocks.DETECTOR_RAIL || block == Blocks.ACTIVATOR_RAIL) marker = Marker.MINESHAFT;
        else if (block == Blocks.SPAWNER) marker = Marker.SPAWNER;
        else if (block == Blocks.MOSSY_COBBLESTONE) marker = Marker.MOSSY;
        else if (block == Blocks.STONE_BRICKS || block == Blocks.MOSSY_STONE_BRICKS || block == Blocks.CRACKED_STONE_BRICKS
                || block == Blocks.CHISELED_STONE_BRICKS || block == Blocks.END_PORTAL_FRAME) marker = Marker.STRONGHOLD;
        else if (block == Blocks.TUFF_BRICKS || block == Blocks.WAXED_COPPER_BLOCK || block == Blocks.WAXED_COPPER_GRATE) marker = Marker.TRIAL;
        else if (block == Blocks.BELL || block == Blocks.HAY_BLOCK) marker = Marker.VILLAGE;
        else if (block == Blocks.REINFORCED_DEEPSLATE) marker = Marker.ANCIENT;
        else if (block == Blocks.PURPUR_BLOCK || block == Blocks.PURPUR_PILLAR) marker = Marker.END_CITY;
        if (block == Blocks.AMETHYST_BLOCK) material = Material.AMETHYST;
        else if (block == Blocks.BUDDING_AMETHYST) material = Material.BUDDING;
        else if (block == Blocks.CALCITE) material = Material.CALCITE;
        else if (block == Blocks.SMOOTH_BASALT) material = Material.BASALT;
        return new Kind(signal, marker, material);
    }

    private static boolean isGrowthSignal(Signal signal) {
        return signal == Signal.MATURE_KELP || signal == Signal.BERRY_VINES
                || signal == Signal.DENSE_VINES || signal == Signal.BAMBOO_GROWTH
                || signal == Signal.RIPE_COCOA || signal == Signal.FULL_BEE_NEST;
    }

    private static boolean qualifies(BlockState state, Signal signal) {
        if (signal == null) return false;
        return switch (signal) {
            case MATURE_KELP -> propertyNumber(state, "age") >= 20;
            case BERRY_VINES -> propertyBoolean(state, "berries");
            case RIPE_COCOA -> propertyNumber(state, "age") >= 2;
            case FULL_BEE_NEST -> propertyNumber(state, "honey_level") >= 3;
            default -> true;
        };
    }

    private static int propertyNumber(BlockState state, String name) {
        for (var entry : state.getEntries().entrySet()) {
            if (entry.getKey().getName().equals(name) && entry.getValue() instanceof Number number)
                return number.intValue();
        }
        return -1;
    }

    private static boolean propertyBoolean(BlockState state, String name) {
        for (var entry : state.getEntries().entrySet()) {
            if (entry.getKey().getName().equals(name) && Boolean.TRUE.equals(entry.getValue())) return true;
        }
        return false;
    }

    private static final class ChunkWork {
        final WorldChunk source;
        final long key;
        final int chunkX, chunkZ, revision;
        final boolean overworld;
        final boolean fullScan;
        final Set<Integer> requestedSections;
        final SectionData[] sectionData;
        final long lastFullScanAt;
        final Counts counts = new Counts();
        final int[] markers = new int[Marker.values().length];
        final Map<BlockPos, Block> tracked = new HashMap<>();
        final Map<Cell, Material> geodes = new HashMap<>();
        final List<Placement> placements = new ArrayList<>();
        int sectionIndex, cellIndex;
        int geometrySignature;
        boolean sectionChecked, scanComplete, geodesOverflow;
        SusChunkGeodes.Job geodeJob;

        ChunkWork(WorldChunk source, int revision, boolean overworld, Snapshot old, Set<Integer> changed) {
            this.source = source; this.revision = revision; this.overworld = overworld;
            chunkX = source.getPos().x; chunkZ = source.getPos().z; key = key(chunkX, chunkZ);
            fullScan = old == null || old.source != source || changed == null;
            requestedSections = fullScan ? Set.of() : Set.copyOf(changed);
            sectionData = fullScan ? new SectionData[source.getSectionArray().length] : old.sectionData.clone();
            lastFullScanAt = fullScan ? -1 : old.lastFullScanAt;
        }

        int advance(int limit) {
            int processed = 0;
            ChunkSection[] sections = source.getSectionArray();
            while (processed < limit && sectionIndex < sections.length) {
                ChunkSection section = sections[sectionIndex];
                if (!sectionChecked) {
                    if (!fullScan && !requestedSections.contains(sectionIndex)) { sectionIndex++; processed++; continue; }
                    sectionData[sectionIndex] = new SectionData();
                    if (section == null || section.isEmpty() || !section.hasAny(state -> {
                        Kind kind = kind(state.getBlock());
                        return kind.signal != null || kind.marker != null || (overworld && kind.material != null);
                    })) { sectionIndex++; processed++; continue; }
                    sectionChecked = true;
                }
                int x = cellIndex & 15, z = cellIndex >> 4 & 15, y = cellIndex >> 8;
                BlockState state = section.getBlockState(x, y, z);
                Kind kind = kind(state.getBlock());
                if (kind.signal != null || kind.marker != null || (overworld && kind.material != null)) {
                    record(sectionData[sectionIndex], new BlockPos(chunkX * 16 + x,
                        source.getBottomY() + sectionIndex * 16 + y, chunkZ * 16 + z), state, kind);
                }
                processed++;
                if (++cellIndex == 4096) { cellIndex = 0; sectionIndex++; sectionChecked = false; }
            }
            scanComplete = sectionIndex == sections.length;
            if (scanComplete) aggregate();
            return processed;
        }

        private void record(SectionData data, BlockPos pos, BlockState state, Kind kind) {
            Signal signal = qualifies(state, kind.signal) ? kind.signal : null;
            if (data.tracked.size() < 256 && (signal != null || state.isOf(Blocks.BUDDING_AMETHYST))) {
                data.tracked.put(pos, state.getBlock());
            }
            if (signal != null && !(signal == Signal.BED && state.get(Properties.BED_PART) == BedPart.HEAD)) {
                data.counts.add(signal);
                if (data.placements.size() < 256 && signal != Signal.BED
                        && signal != Signal.MANUFACTURED && !isGrowthSignal(signal)) {
                    data.placements.add(new Placement(cell(pos), signal));
                }
            }
            if (kind.marker != null) data.markers[kind.marker.ordinal()] += state.isOf(Blocks.BELL) || state.isOf(Blocks.END_PORTAL_FRAME) ? 32 : 1;
            if (overworld && kind.material != null) {
                if (data.geodes.size() < 2048) data.geodes.put(cell(pos), kind.material);
                else data.geodesOverflow = true;
            }
        }

        private void aggregate() {
            for (SectionData data : sectionData) {
                if (data == null) continue;
                counts.addAll(data.counts);
                for (int i = 0; i < markers.length; i++) markers[i] += data.markers[i];
                for (var entry : data.tracked.entrySet()) {
                    if (tracked.size() == MAX_TRACKED_BLOCKS) break;
                    tracked.put(entry.getKey(), entry.getValue());
                }
                geodesOverflow |= data.geodesOverflow;
                for (var entry : data.geodes.entrySet()) {
                    if (geodes.size() == MAX_GEODE_CELLS) { geodesOverflow = true; break; }
                    geodes.put(entry.getKey(), entry.getValue());
                }
                for (Placement placement : data.placements) {
                    if (placements.size() == MAX_PLACEMENTS) break;
                    placements.add(placement);
                }
            }
        }

        boolean structureContext() {
            return markers[Marker.MINESHAFT.ordinal()] >= 2 || markers[Marker.SPAWNER.ordinal()] > 0
                    || markers[Marker.MOSSY.ordinal()] >= 16 || markers[Marker.STRONGHOLD.ordinal()] >= 32
                    || markers[Marker.TRIAL.ordinal()] >= 16 || markers[Marker.VILLAGE.ordinal()] >= 8
                    || markers[Marker.ANCIENT.ordinal()] > 0 || markers[Marker.END_CITY.ordinal()] >= 16;
        }
    }

    /** Per-section evidence is immutable after publication and reused on unrelated updates. */
    private static final class SectionData {
        final Counts counts = new Counts();
        final int[] markers = new int[Marker.values().length];
        final Map<BlockPos, Block> tracked = new HashMap<>();
        final Map<Cell, Material> geodes = new HashMap<>();
        final List<Placement> placements = new ArrayList<>();
        boolean geodesOverflow;
    }

    private static final class Snapshot {
        final long key, scannedAt, lastFullScanAt;
        final WorldChunk source;
        final Counts counts;
        final Map<BlockPos, Block> tracked;
        final Map<Cell, Material> geodes;
        final List<Placement> placements;
        final boolean structureContext, geodesOverflow;
        final int[] surface;
        final SectionData[] sectionData;
        final int geometrySignature;
        Snapshot(ChunkWork work, int[] surface, long ticks) {
            key = work.key; source = work.source; scannedAt = ticks; counts = work.counts;
            tracked = Map.copyOf(work.tracked); geodes = Map.copyOf(work.geodes); placements = List.copyOf(work.placements);
            structureContext = work.structureContext(); geodesOverflow = work.geodesOverflow; this.surface = surface;
            sectionData = work.sectionData.clone();
            lastFullScanAt = work.fullScan ? ticks : work.lastFullScanAt;
            geometrySignature = work.geometrySignature;
        }
    }

    private record RenderFlag(Snapshot snapshot, Result result, Signal signal, int slot, int stripes) {
        RenderFlag(Snapshot snapshot, Result result) { this(snapshot, result, null, 0, 1); }
    }
    private static Cell cell(BlockPos pos) { return new Cell(pos.getX(), pos.getY(), pos.getZ()); }
    private static int index(int x, int z) { return (x & 15) * 16 + (z & 15); }
    private int scanRadiusBlocks() { return scanRadius.get() * 16; }
    private static long key(int x, int z) { return (long) x << 32 | z & 0xffffffffL; }
    private static int chunkX(long key) { return (int) (key >> 32); }
    private static int chunkZ(long key) { return (int) key; }
    private static WorldChunk loaded(MinecraftClient client, int x, int z) {
        return client.world.getChunkManager().getChunk(x, z, ChunkStatus.FULL, false);
    }
    private static double distanceSquared(MinecraftClient client, int x, int z) {
        double dx = x * 16.0 + 8 - client.player.getX(), dz = z * 16.0 + 8 - client.player.getZ();
        return dx * dx + dz * dz;
    }
    private static boolean inRange(MinecraftClient client, int x, int z, int radius) {
        double minX = x * 16.0, minZ = z * 16.0;
        double dx = Math.max(minX, Math.min(client.player.getX(), minX + 16)) - client.player.getX();
        double dz = Math.max(minZ, Math.min(client.player.getZ(), minZ + 16)) - client.player.getZ();
        return dx * dx + dz * dz <= (double) radius * radius;
    }
}
