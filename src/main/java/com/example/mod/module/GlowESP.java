package com.example.mod.module;

import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.IntegerSetting;
import com.example.mod.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/** Client-side vanilla glowing outlines; restores each entity's previous local glow state. */
public final class GlowESP extends Module {
    private final BooleanSetting players = addSetting(new BooleanSetting("Players", true));
    private final BooleanSetting mobs = addSetting(new BooleanSetting("Mobs", false));
    private final BooleanSetting items = addSetting(new BooleanSetting("Items", false));
    private final BooleanSetting crystals = addSetting(new BooleanSetting("End crystals", false));
    private final IntegerSetting range = addSetting(new IntegerSetting("Range", 64, 8, 192));
    private final IntegerSetting limit = addSetting(new IntegerSetting("Max entities", 48, 4, 96));
    private final BooleanSetting tracers = addSetting(new BooleanSetting("Tracers", true));
    private final ColorSetting tracerColor = addSetting(new ColorSetting("Tracer color", 0xFFAA55FF));
    private final Map<Entity, Boolean> changed = new IdentityHashMap<>();
    private ClientWorld world;
    private long nextUpdate;
    private List<Entity> visibleTargets = List.of();

    public GlowESP() {
        super("Glow ESP", false, Category.VISUALS);
        players.describe("Use Minecraft's local glowing outline on other players.");
        mobs.describe("Include living mobs and animals.");
        items.describe("Include dropped item entities.");
        crystals.describe("Include end crystals.");
        range.describe("Maximum distance for glowing outlines. Only loaded entities can be marked.");
        limit.describe("Maximum number of entities to mark at once.");
        tracers.describe("Draw lines from the crosshair to glowing entities.");
        tracerColor.describe("Color of Glow ESP tracer lines.");
    }

    @Override protected void onEnable() { onWorldLeave(); }
    @Override protected void onDisable() { onWorldLeave(); }
    @Override public void onWorldLeave() {
        for (Map.Entry<Entity, Boolean> entry : changed.entrySet()) {
            if (!entry.getKey().isRemoved()) entry.getKey().setGlowing(entry.getValue());
        }
        changed.clear(); visibleTargets = List.of(); world = null; nextUpdate = 0;
    }

    public void tick(MinecraftClient client) {
        if (!isEnabled() || client.world == null || client.player == null) {
            if (world != null) onWorldLeave();
            return;
        }
        if (world != client.world) { onWorldLeave(); world = client.world; }
        long now = System.nanoTime();
        if (now < nextUpdate) return;
        nextUpdate = now + 250_000_000L;
        double maxDistanceSq = (double) range.get() * range.get();
        List<Entity> candidates = new ArrayList<>();
        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player || entity.isRemoved()
                    || entity.squaredDistanceTo(client.player) > maxDistanceSq) continue;
            if (entity instanceof PlayerEntity && players.get()
                    || entity instanceof LivingEntity && !(entity instanceof PlayerEntity) && mobs.get()
                    || entity instanceof ItemEntity && items.get()
                    || entity instanceof EndCrystalEntity && crystals.get()) candidates.add(entity);
        }
        candidates.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(client.player)));
        Set<Entity> selected = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < Math.min(limit.get(), candidates.size()); i++) selected.add(candidates.get(i));
        visibleTargets = List.copyOf(selected);
        changed.entrySet().removeIf(entry -> {
            Entity entity = entry.getKey();
            if (selected.contains(entity) && !entity.isRemoved()) return false;
            if (!entity.isRemoved()) entity.setGlowing(entry.getValue());
            return true;
        });
        for (Entity entity : selected) {
            if (changed.containsKey(entity)) {
                if (!entity.isGlowingLocal()) entity.setGlowing(true);
            } else if (!entity.isGlowingLocal()) {
                changed.put(entity, false);
                entity.setGlowing(true);
            }
        }
    }

    public void renderHud(DrawContext context, RenderTickCounter ticks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || !tracers.get() || client.player == null || client.world != world) return;
        float progress = ticks.getTickProgress(false);
        List<EspTracers.Target> candidates = new ArrayList<>();
        for (Entity entity : visibleTargets) {
            if (entity.isRemoved()) continue;
            Vec3d point = entity.getLerpedPos(progress).add(0, entity.getHeight() / 2, 0);
            candidates.add(new EspTracers.Target(point, tracerColor.get(),
                    entity.squaredDistanceTo(client.player)));
        }
        EspTracers.draw(context, client, candidates, 24);
    }
}
