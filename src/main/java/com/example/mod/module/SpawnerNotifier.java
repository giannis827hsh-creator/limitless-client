package com.example.mod.module;

import com.example.mod.Theme;
import com.example.mod.gui.GuiDraw;
import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.ColorSetting;
import com.example.mod.setting.IntegerSetting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

/** Reports real mob spawners in chunks the server has sent to this client. */
public final class SpawnerNotifier extends Module {
    private static final int CHUNKS_PER_TICK = 4;
    private static final long SCAN_BUDGET_NANOS = 1_500_000L;
    private static final long RESCAN_TICKS = 100;
    private static final int MAX_VISIBLE = 256;
    private static final int MAX_NOTIFIED = 4096;
    private static final int MAX_POPUPS = 4;
    private static final ItemStack SPAWNER_ICON = new ItemStack(Blocks.SPAWNER);

    private final IntegerSetting range = addSetting(new IntegerSetting("Range", 128, 16, 256));
    private final BooleanSetting popup = addSetting(new BooleanSetting("Popup", true));
    private final IntegerSetting popupSeconds = addSetting(new IntegerSetting("Popup seconds", 5, 2, 10));
    private final BooleanSetting chatAlerts = addSetting(new BooleanSetting("Chat alerts", true));
    private final BooleanSetting sound = addSetting(new BooleanSetting("Sound", true));
    private final BooleanSetting highlight = addSetting(new BooleanSetting("Highlight", true));
    private final ColorSetting accent = addSetting(new ColorSetting("Accent color", Theme.PRIMARY));

    private final ArrayDeque<ChunkCoordinate> scanQueue = new ArrayDeque<>();
    private final Map<BlockPos, Spawner> found = new HashMap<>();
    private final Set<Long> notified = new LinkedHashSet<>();
    private final ArrayDeque<Spawner> popupQueue = new ArrayDeque<>();
    private List<Spawner> visible = List.of();
    private Object observedWorld;
    private int centerX = Integer.MIN_VALUE, centerZ = Integer.MIN_VALUE, scannedRange = -1;
    private long nextRescan, lastSoundNanos, popupStartedNanos;
    private Spawner currentPopup;

    public SpawnerNotifier() {
        super("Spawner Notifier", false, Category.DONUT);
        range.describe("Look for mob spawners in already loaded chunks within this distance. No server chunks are requested.");
        popup.describe("Show a short limitless card at the top of the screen when a new spawner is found.");
        popupSeconds.describe("How long the spawner card stays on screen.");
        chatAlerts.describe("Print a local chat message with the mob type, coordinates and distance.");
        sound.describe("Play a short local notification sound when a spawner is found.");
        highlight.describe("Draw a colored outline around found spawners through blocks.");
        accent.describe("Color of the notification progress bar and spawner outlines.");
    }

    @Override protected void onEnable() { onWorldLeave(); }
    @Override protected void onDisable() { onWorldLeave(); }
    @Override public void onWorldLeave() {
        scanQueue.clear(); found.clear(); notified.clear(); popupQueue.clear(); visible = List.of();
        currentPopup = null; popupStartedNanos = 0; observedWorld = null;
        centerX = Integer.MIN_VALUE; centerZ = Integer.MIN_VALUE; scannedRange = -1;
        nextRescan = 0; lastSoundNanos = 0;
    }

    public void tick(MinecraftClient client) {
        if (!isEnabled() || client.world == null || client.player == null || client.isPaused()) return;
        if (observedWorld != client.world) {
            onWorldLeave();
            observedWorld = client.world;
        }
        if (!popup.get()) { popupQueue.clear(); currentPopup = null; }
        int chunkX = client.player.getBlockX() >> 4;
        int chunkZ = client.player.getBlockZ() >> 4;
        int wantedRange = range.get();
        long worldTime = client.world.getTime();
        if (chunkX != centerX || chunkZ != centerZ || wantedRange != scannedRange
                || scanQueue.isEmpty() && worldTime >= nextRescan) {
            queueLoadedChunks(client, chunkX, chunkZ, wantedRange);
            centerX = chunkX; centerZ = chunkZ; scannedRange = wantedRange;
            nextRescan = worldTime + RESCAN_TICKS;
        }
        long deadline = System.nanoTime() + SCAN_BUDGET_NANOS;
        boolean changed = false;
        for (int count = 0; count < CHUNKS_PER_TICK && !scanQueue.isEmpty()
                && System.nanoTime() < deadline; count++) {
            ChunkCoordinate coordinate = scanQueue.removeFirst();
            changed |= scanChunk(client, coordinate);
        }
        if (changed) visible = List.copyOf(found.values().stream().limit(MAX_VISIBLE).toList());
    }

    private void queueLoadedChunks(MinecraftClient client, int chunkX, int chunkZ, int radius) {
        scanQueue.clear();
        int chunkRadius = Math.min((radius + 15) / 16, client.options.getClampedViewDistance() + 1);
        int maxDistanceSq = radius * radius;
        found.entrySet().removeIf(entry -> {
            Vec3d point = Vec3d.ofCenter(entry.getKey());
            double dx = point.x - client.player.getX(), dy = point.y - client.player.getY();
            double dz = point.z - client.player.getZ();
            return dx * dx + dy * dy + dz * dz > maxDistanceSq
                    || client.world.getChunkManager().getChunk(entry.getKey().getX() >> 4,
                            entry.getKey().getZ() >> 4, ChunkStatus.FULL, false) == null;
        });
        visible = List.copyOf(found.values().stream().limit(MAX_VISIBLE).toList());
        List<ChunkCoordinate> loaded = new ArrayList<>();
        for (int x = chunkX - chunkRadius; x <= chunkX + chunkRadius; x++) {
            for (int z = chunkZ - chunkRadius; z <= chunkZ + chunkRadius; z++) {
                if (client.world.getChunkManager().getChunk(x, z, ChunkStatus.FULL, false) == null) continue;
                loaded.add(new ChunkCoordinate(x, z));
            }
        }
        loaded.sort((a, b) -> Long.compare(distanceSq(a, chunkX, chunkZ), distanceSq(b, chunkX, chunkZ)));
        scanQueue.addAll(loaded);
    }

    private boolean scanChunk(MinecraftClient client, ChunkCoordinate coordinate) {
        WorldChunk chunk = client.world.getChunkManager().getChunk(coordinate.x(), coordinate.z(), ChunkStatus.FULL, false);
        Map<BlockPos, Spawner> previousInChunk = new HashMap<>();
        found.entrySet().removeIf(entry -> {
            BlockPos pos = entry.getKey();
            if (pos.getX() >> 4 != coordinate.x() || pos.getZ() >> 4 != coordinate.z()) return false;
            previousInChunk.put(pos, entry.getValue());
            return true;
        });
        if (chunk == null) return !previousInChunk.isEmpty();
        boolean changed = false;
        double radiusSq = (double) range.get() * range.get();
        int checked = 0;
        for (BlockPos position : chunk.getBlockEntityPositions()) {
            if (++checked > 2048) break;
            if (!chunk.getBlockState(position).isOf(Blocks.SPAWNER)) continue;
            BlockPos pos = position.toImmutable();
            Vec3d point = Vec3d.ofCenter(pos);
            if (point.squaredDistanceTo(client.player.getX(), client.player.getY(), client.player.getZ()) > radiusSq) continue;
            Spawner previous = previousInChunk.remove(pos);
            if (previous == null && found.size() >= MAX_VISIBLE) continue;
            Spawner spawner = previous == null || previous.name().equals("Mob")
                    ? new Spawner(pos, mobName(chunk, pos)) : previous;
            found.put(pos, spawner);
            changed |= previous == null || !spawner.equals(previous);
            if (notified.add(pos.asLong())) {
                if (notified.size() > MAX_NOTIFIED) notified.remove(notified.iterator().next());
                announce(client, spawner);
            }
        }
        return changed || !previousInChunk.isEmpty();
    }

    private static long distanceSq(ChunkCoordinate coordinate, int x, int z) {
        long dx = coordinate.x() - x, dz = coordinate.z() - z;
        return dx * dx + dz * dz;
    }

    private static String mobName(WorldChunk chunk, BlockPos pos) {
        BlockEntity entity = chunk.getBlockEntity(pos);
        if (entity instanceof MobSpawnerBlockEntity spawner) {
            try {
                Entity rendered = spawner.getLogic().getRenderedEntity(chunk.getWorld(), pos);
                if (rendered != null) return rendered.getType().getName().getString();
            } catch (RuntimeException ignored) {
                // A server may send incomplete spawner data; the block itself is still real.
            }
        }
        return "Mob";
    }

    private void announce(MinecraftClient client, Spawner spawner) {
        BlockPos pos = spawner.pos();
        String title = spawner.name() + " Spawner";
        if (chatAlerts.get()) {
            int distance = (int) Math.round(Math.sqrt(Vec3d.ofCenter(pos).squaredDistanceTo(
                    client.player.getX(), client.player.getY(), client.player.getZ())));
            client.player.sendMessage(Text.literal("[limitless] " + title + " found at "
                    + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " (" + distance + "m)")
                    .formatted(Formatting.LIGHT_PURPLE), false);
        }
        if (popup.get() && popupQueue.size() < MAX_POPUPS) popupQueue.addLast(spawner);
        long now = System.nanoTime();
        if (sound.get() && now - lastSoundNanos > 750_000_000L) {
            client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 1.0f));
            lastSoundNanos = now;
        }
    }

    public void renderWorld(WorldRenderContext context) {
        if (!isEnabled() || !highlight.get() || visible.isEmpty()) return;
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) return;
        Vec3d camera = context.worldState().cameraRenderState.pos;
        VertexConsumer lines = consumers.getBuffer(EspRenderLayers.LINES_THROUGH_WALLS);
        matrices.push();
        try {
            matrices.translate(-camera.x, -camera.y, -camera.z);
            for (Spawner spawner : visible) {
                BlockPos pos = spawner.pos();
                VertexRendering.drawOutline(matrices, lines, VoxelShapes.cuboid(new Box(
                        pos.getX() + 0.03, pos.getY() + 0.03, pos.getZ() + 0.03,
                        pos.getX() + 0.97, pos.getY() + 0.97, pos.getZ() + 0.97)),
                        0.0, 0.0, 0.0, accent.get(), 2.0f);
            }
        } finally { matrices.pop(); }
    }

    public void renderHud(DrawContext context, RenderTickCounter ticks) {
        if (!isEnabled() || !popup.get()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        long now = System.nanoTime();
        long duration = popupSeconds.get() * 1_000_000_000L;
        if (currentPopup == null || now - popupStartedNanos >= duration) {
            currentPopup = popupQueue.pollFirst();
            popupStartedNanos = now;
        }
        if (currentPopup == null) return;

        int width = Math.min(200, context.getScaledWindowWidth() - 20);
        if (width < 130) return;
        int x = (context.getScaledWindowWidth() - width) / 2;
        int y = 10;
        int height = 54;
        float remaining = Math.max(0.0f, 1.0f - (float) (now - popupStartedNanos) / duration);
        int alpha = Math.round(255.0f * Math.min(1.0f, Math.min((now - popupStartedNanos) / 200_000_000.0f,
                (duration - (now - popupStartedNanos)) / 250_000_000.0f)));
        alpha = Math.max(0, Math.min(255, alpha));
        int color = Theme.withAlpha(accent.get(), alpha);

        GuiDraw.rounded(context, x + 2, y + 2, width, height, 10, Theme.withAlpha(0xFF000000, alpha / 3));
        GuiDraw.rounded(context, x, y, width, height, 10, Theme.withAlpha(0xFF141422, Math.round(alpha * 0.91f)));
        GuiDraw.outline(context, x, y, width, height, 10, Theme.withAlpha(accent.get(), alpha / 3));
        GuiDraw.rounded(context, x + 9, y + 8, 34, 34, 8, Theme.withAlpha(0xFF30203C, alpha / 2));
        context.getMatrices().pushMatrix();
        try {
            context.getMatrices().translate(x + 17, y + 16);
            context.getMatrices().scale(1.125f, 1.125f);
            context.drawItem(SPAWNER_ICON, 0, 0);
        } finally { context.getMatrices().popMatrix(); }

        String title = GuiDraw.fit(client.textRenderer, currentPopup.name() + " Spawner", width - 58);
        context.drawTextWithShadow(client.textRenderer, title, x + 51, y + 11,
                Theme.withAlpha(Theme.TEXT, alpha));
        BlockPos pos = currentPopup.pos();
        String coordinates = "at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        coordinates = GuiDraw.fit(client.textRenderer, coordinates, width - 58);
        context.drawTextWithShadow(client.textRenderer, coordinates, x + 51, y + 25,
                Theme.withAlpha(Theme.MUTED_TEXT, alpha));
        GuiDraw.rounded(context, x + 11, y + 45, width - 22, 3, 1,
                Theme.withAlpha(0xFF6E6875, alpha / 2));
        int progress = Math.round((width - 22) * remaining);
        if (progress > 0) GuiDraw.rounded(context, x + 11, y + 45, progress, 3, 1, color);
    }

    private record ChunkCoordinate(int x, int z) { }
    private record Spawner(BlockPos pos, String name) { }
}
