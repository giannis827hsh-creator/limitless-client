package com.example.mod.module;

import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.ColorSetting;
import com.example.mod.setting.IntegerSetting;
import com.example.mod.setting.NumberSetting;
import com.example.mod.setting.StringSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

/** Outlines dropped item entities already present in the client world. */
public final class ItemESP extends Module {
    private final BooleanSetting boxes = addSetting(new BooleanSetting("Boxes", true));
    private final BooleanSetting tracers = addSetting(new BooleanSetting("Tracers", true));
    private final NumberSetting range = addSetting(new NumberSetting("Range", 96, 16, 256, 8));
    private final IntegerSetting maxItems = addSetting(new IntegerSetting("Max items", 64, 8, 128));
    private final StringSetting itemFilter = addSetting(new StringSetting("Item IDs", "", 256));
    private final ColorSetting color = addSetting(new ColorSetting("Item color", 0xFFFFD160));

    public ItemESP() {
        super("Item ESP", false, Category.VISUALS);
        boxes.describe("Outline dropped items in loaded chunks, including behind blocks.");
        tracers.describe("Draw short screen tracers from the crosshair to on-screen dropped items.");
        range.describe("Maximum distance to dropped items, measured from your player.");
        maxItems.describe("Maximum number of nearby items to highlight per frame.");
        itemFilter.describe("Optional comma-separated item IDs, such as diamond, netherite_ingot. Empty shows every item.");
        color.describe("Color used for item outlines and tracers.");
    }

    private List<ItemEntity> targets(MinecraftClient client, float tickProgress) {
        if (client.world == null || client.player == null) return List.of();
        double rangeSq = range.get() * range.get();
        String raw = itemFilter.get().trim().toLowerCase(Locale.ROOT);
        List<ItemEntity> result = new ArrayList<>();
        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof ItemEntity item) || item.isRemoved() || item.getStack().isEmpty()
                    || item.squaredDistanceTo(client.player) > rangeSq) continue;
            if (!raw.isEmpty()) {
                String id = Registries.ITEM.getId(item.getStack().getItem()).toString();
                boolean matched = false;
                for (String term : raw.split(",")) {
                    String needle = term.trim();
                    if (!needle.isEmpty() && (id.equals(needle) || id.endsWith(":" + needle))) {
                        matched = true; break;
                    }
                }
                if (!matched) continue;
            }
            result.add(item);
        }
        result.sort(Comparator.comparingDouble(item -> item.squaredDistanceTo(client.player)));
        if (result.size() > maxItems.get()) return result.subList(0, maxItems.get());
        return result;
    }

    public void renderWorld(WorldRenderContext context) {
        if (!isEnabled() || !boxes.get()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null || client.world == null || client.player == null) return;
        Vec3d camera = context.worldState().cameraRenderState.pos;
        VertexConsumer lines = consumers.getBuffer(EspRenderLayers.LINES_THROUGH_WALLS);
        float progress = client.getRenderTickCounter().getTickProgress(false);
        matrices.push();
        try {
            matrices.translate(-camera.x, -camera.y, -camera.z);
            for (ItemEntity item : targets(client, progress)) {
                Vec3d offset = item.getLerpedPos(progress).subtract(item.getEntityPos());
                Box box = item.getBoundingBox().offset(offset).expand(0.045);
                VertexRendering.drawOutline(matrices, lines, VoxelShapes.cuboid(box),
                        0, 0, 0, color.get(), 2.0f);
            }
        } finally { matrices.pop(); }
    }

    public void renderHud(DrawContext context, RenderTickCounter ticks) {
        if (!isEnabled() || !tracers.get()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || client.currentScreen != null) return;
        float progress = ticks.getTickProgress(false);
        List<EspTracers.Target> candidates = new ArrayList<>();
        for (ItemEntity item : targets(client, progress)) {
            Vec3d pos = item.getLerpedPos(progress).add(0, item.getHeight() / 2, 0);
            candidates.add(new EspTracers.Target(pos, color.get(), item.squaredDistanceTo(client.player)));
        }
        EspTracers.draw(context, client, candidates, 16);
    }
}
