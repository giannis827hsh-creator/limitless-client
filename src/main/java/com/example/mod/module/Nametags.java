package com.example.mod.module;

import com.example.mod.Theme;
import com.example.mod.gui.GuiDraw;
import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.ColorSetting;
import com.example.mod.setting.IntegerSetting;
import com.example.mod.setting.ModeSetting;
import com.example.mod.setting.NumberSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

/** Custom player cards positioned above players in the world. */
public final class Nametags extends Module {
    private static final EquipmentSlot[] EQUIPMENT = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private final ModeSetting style = addSetting(new ModeSetting("Style", "Detailed", "Detailed", "Compact"));
    private final BooleanSetting showHealth = addSetting(new BooleanSetting("Health", true));
    private final BooleanSetting showPing = addSetting(new BooleanSetting("Ping", true));
    private final BooleanSetting showDistance = addSetting(new BooleanSetting("Distance", true));
    private final BooleanSetting showHands = addSetting(new BooleanSetting("Held items", true));
    private final BooleanSetting showArmor = addSetting(new BooleanSetting("Armor", true));
    private final BooleanSetting showDurability = addSetting(new BooleanSetting("Durability bars", true));
    private final BooleanSetting includeInvisible = addSetting(new BooleanSetting("Include invisible", true));
    private final NumberSetting range = addSetting(new NumberSetting("Range", 128, 8, 256, 8));
    private final IntegerSetting maxTags = addSetting(new IntegerSetting("Max tags", 24, 1, 64));
    private final NumberSetting scale = addSetting(new NumberSetting("Scale", 1, 0.5, 2, 0.1));
    private final NumberSetting backgroundOpacity = addSetting(new NumberSetting("Background opacity", 0.82, 0, 1, 0.05));
    private final ColorSetting accent = addSetting(new ColorSetting("Accent color", Theme.PRIMARY));

    public Nametags() {
        super("Nametags", false, Category.VISUALS);
        style.describe("Detailed cards show status and equipment; Compact cards show one line.");
        range.describe("Only players in loaded chunks and within this distance can be shown.");
        maxTags.describe("Limit cards in crowded areas.");
        backgroundOpacity.describe("Transparency of the dark purple card.");
        includeInvisible.describe("Show invisible player entities when the server sends them.");
    }

    public void renderHud(DrawContext context, RenderTickCounter ticks) {
        if (!isEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.currentScreen != null) return;
        Camera camera = client.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getCameraPos();
        Vec3d forward = new Vec3d(camera.getHorizontalPlane());
        float partial = ticks.getTickProgress(false);
        double maxSq = range.get() * range.get();
        List<PlayerEntity> players = new ArrayList<>();
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player == client.player || !player.isAlive() || player.isRemoved()
                    || (!includeInvisible.get() && player.isInvisible())) continue;
            if (player.squaredDistanceTo(client.player) <= maxSq) players.add(player);
        }
        players.sort(Comparator.comparingDouble(player -> player.squaredDistanceTo(client.player)));
        int count = Math.min(maxTags.get(), players.size());
        for (int i = count - 1; i >= 0; i--) {
            PlayerEntity player = players.get(i);
            Vec3d anchor = player.getLerpedPos(partial).add(0, player.getHeight() + 0.28, 0);
            if (anchor.subtract(cameraPos).dotProduct(forward) <= 0.05) continue;
            Vec3d projected = client.gameRenderer.project(anchor);
            if (!Double.isFinite(projected.x) || !Double.isFinite(projected.y)
                    || Math.abs(projected.x) > 1.1 || Math.abs(projected.y) > 1.1) continue;
            float x = (float) ((projected.x + 1) * context.getScaledWindowWidth() / 2);
            float y = (float) ((1 - projected.y) * context.getScaledWindowHeight() / 2);
            drawCard(context, client, player, x, y);
        }
    }

    private void drawCard(DrawContext context, MinecraftClient client, PlayerEntity player, float x, float y) {
        boolean detailed = style.get().equals("Detailed");
        String name = GuiDraw.fit(client.textRenderer, player.getName().getString(), 140);
        String distance = showDistance.get() ? Math.round(client.player.distanceTo(player)) + "m" : "";
        int ping = -1;
        if (showPing.get() && client.getNetworkHandler() != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (entry != null) ping = entry.getLatency();
        }
        String pingText = ping >= 0 ? ping + "ms" : "";
        String stats = "";
        if (showHealth.get()) {
            stats = Math.round(player.getHealth() + player.getAbsorptionAmount()) + " HP";
        }
        if (!distance.isEmpty()) stats += (stats.isEmpty() ? "" : "  ·  ") + distance;
        if (!pingText.isEmpty()) stats += (stats.isEmpty() ? "" : "  ·  ") + pingText;
        String heading = detailed ? name : GuiDraw.fit(client.textRenderer,
                name + (stats.isEmpty() ? "" : "  ·  " + stats), 240);
        List<ItemStack> items = new ArrayList<>();
        if (detailed) {
            for (int i = 0; i < EQUIPMENT.length; i++) {
                if (i < 2 && !showHands.get() || i >= 2 && !showArmor.get()) continue;
                ItemStack item = player.getEquippedStack(EQUIPMENT[i]);
                if (!item.isEmpty()) items.add(item);
            }
        }
        int width = Math.max(72, Math.max(client.textRenderer.getWidth(heading),
                detailed ? client.textRenderer.getWidth(stats) : 0) + 16);
        if (!items.isEmpty()) width = Math.max(width, items.size() * 15 + 12);
        int height = detailed ? 17 + (!stats.isEmpty() ? 12 : 0) + (!items.isEmpty() ? 19 : 0) : 16;
        float distanceScale = (float) Math.max(0.65, Math.min(1, 14.0 / Math.max(1, client.player.distanceTo(player))));
        float drawScale = (float) scale.get() * distanceScale;
        int panel = (Math.round(220 * (float) backgroundOpacity.get()) << 24) | 0x120C1F;
        context.getMatrices().pushMatrix();
        try {
            context.getMatrices().translate(x, y - 3);
            context.getMatrices().scale(drawScale, drawScale);
            int left = -width / 2;
            int top = -height;
            GuiDraw.rounded(context, left + 1, top + 2, width, height, 5, 0x66000000);
            GuiDraw.rounded(context, left, top, width, height, 5, panel);
            GuiDraw.outline(context, left, top, width, height, 5, 0xAA8B5CF6);
            context.fill(left + 5, top + 2, left + width - 5, top + 3, accent.get());
            context.drawText(client.textRenderer, heading, left + 8, top + 6, 0xFFF6F0FF, true);
            if (detailed) {
                int row = top + 17;
                if (!stats.isEmpty()) {
                    int healthColor = player.getHealth() / Math.max(1, player.getMaxHealth()) < 0.3
                            ? 0xFFFF7676 : 0xFFD0B7FF;
                    context.drawText(client.textRenderer, stats, left + 8, row, healthColor, false);
                    row += 12;
                }
                int itemX = left + 7;
                for (ItemStack item : items) {
                    context.getMatrices().pushMatrix();
                    context.getMatrices().translate(itemX, row);
                    context.getMatrices().scale(0.75f, 0.75f);
                    context.drawItem(item, 0, 0);
                    context.getMatrices().popMatrix();
                    if (showDurability.get() && item.isDamageable()) {
                        int fill = Math.round(11f * (1f - (float) item.getDamage() / Math.max(1, item.getMaxDamage())));
                        context.fill(itemX, row + 13, itemX + 11, row + 14, 0xFF4B3A5D);
                        context.fill(itemX, row + 13, itemX + fill, row + 14, accent.get());
                    }
                    itemX += 15;
                }
            }
        } finally {
            context.getMatrices().popMatrix();
        }
    }
}
