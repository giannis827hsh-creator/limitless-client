package com.example.mod.module;

import com.example.mod.Theme;
import com.example.mod.gui.BlockPickerScreen;
import com.example.mod.gui.GuiDraw;
import com.example.mod.gui.LimitlessClickGUI;
import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.ModeSetting;
import com.example.mod.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

public final class ArmorHUD extends Module {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private static final String[] LABELS = {"H", "C", "L", "B", "M", "O"};
    private static final int WIDTH = 148;

    private final ModeSetting position = addSetting(new ModeSetting("Position", "Bottom left",
            "Bottom left", "Bottom right", "Top left", "Top right"));
    private final NumberSetting size = addSetting(new NumberSetting("Size", 0.75, 0.5, 1.25, 0.05));
    private final NumberSetting offsetX = addSetting(new NumberSetting("Offset X", 8, 0, 200, 1));
    private final NumberSetting offsetY = addSetting(new NumberSetting("Offset Y", 42, 0, 200, 1));
    private final BooleanSetting heldItem = addSetting(new BooleanSetting("Held item", true));
    private final BooleanSetting offhand = addSetting(new BooleanSetting("Offhand", true));
    private final BooleanSetting percentages = addSetting(new BooleanSetting("Percentages", false));
    private final NumberSetting warningAt = addSetting(new NumberSetting("Warn below %", 20, 5, 50, 5));

    public ArmorHUD() {
        super("Armor HUD", false, Category.VISUALS);
        position.describe("Place the gear card in a screen corner.");
        size.describe("Resize the complete gear card.");
        offsetX.describe("Distance from the selected horizontal edge.");
        offsetY.describe("Distance from the selected vertical edge.");
        heldItem.describe("Include the item in your main hand.");
        offhand.describe("Include the item in your offhand.");
        percentages.describe("Show remaining durability below each item.");
        warningAt.describe("Durability bars turn red below this percentage.");
    }

    public void renderHud(DrawContext context, RenderTickCounter ticks) {
        if (!isEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null
                || client.currentScreen instanceof LimitlessClickGUI
                || client.currentScreen instanceof BlockPickerScreen) return;

        int count = 4 + (heldItem.get() ? 1 : 0) + (offhand.get() ? 1 : 0);
        int width = WIDTH - (6 - count) * 23;
        int height = percentages.get() ? 44 : 34;
        float scale = (float) size.get();
        int scaledWidth = Math.round(width * scale);
        int scaledHeight = Math.round(height * scale);
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        boolean right = position.get().endsWith("right");
        boolean bottom = position.get().startsWith("Bottom");
        int x = right ? screenWidth - scaledWidth - (int) offsetX.get() : (int) offsetX.get();
        int y = bottom ? screenHeight - scaledHeight - (int) offsetY.get() : (int) offsetY.get();
        x = Math.max(0, Math.min(x, screenWidth - scaledWidth));
        y = Math.max(0, Math.min(y, screenHeight - scaledHeight));

        context.getMatrices().pushMatrix();
        try {
            context.getMatrices().translate(x, y);
            context.getMatrices().scale(scale, scale);
            GuiDraw.surface(context, 0, 0, width, height, 5, Theme.PRIMARY, 1);
            int column = 0;
            for (int i = 0; i < SLOTS.length; i++) {
                if (i == 4 && !heldItem.get() || i == 5 && !offhand.get()) continue;
                drawSlot(context, client, client.player.getEquippedStack(SLOTS[i]),
                        LABELS[i], 5 + column * 23);
                column++;
            }
        } finally {
            context.getMatrices().popMatrix();
        }
    }

    private void drawSlot(DrawContext context, MinecraftClient client, ItemStack stack,
                          String label, int x) {
        GuiDraw.rounded(context, x, 5, 21, percentages.get() ? 34 : 24, 3, Theme.SURFACE);
        if (stack.isEmpty()) {
            context.drawCenteredTextWithShadow(client.textRenderer, label, x + 10, 12, Theme.MUTED_TEXT);
            return;
        }
        context.drawItem(stack, x + 2, 7);
        if (!stack.isDamageable()) return;
        int max = Math.max(1, stack.getMaxDamage());
        int remaining = Math.max(0, max - stack.getDamage());
        float fraction = Math.min(1.0f, (float) remaining / max);
        int percent = Math.round(fraction * 100);
        int color = percent <= warningAt.get() ? 0xFFFF555F
                : percent <= 50 ? 0xFFFFC36A : Theme.PRIMARY;
        context.fill(x + 2, 26, x + 19, 28, Theme.BORDER);
        context.fill(x + 2, 26, x + 2 + Math.round(17 * fraction), 28, color);
        if (percentages.get()) {
            String text = percent + "%";
            context.drawCenteredTextWithShadow(client.textRenderer, text, x + 10, 30, color);
        }
    }
}
