package com.example.mod.module;

import com.example.mod.Theme;
import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.ColorSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;

/** Compact target card with smooth fade and health animation. */
public final class TargetHUD extends Module {
    private final BooleanSetting targetHud = addSetting(new BooleanSetting("Target HUD", true));
    private final ColorSetting accentColor = addSetting(new ColorSetting("Accent color", Theme.PRIMARY));

    private LivingEntity desiredTarget;
    private LivingEntity displayedTarget;
    private float opacity;
    private float displayedHealth;

    public TargetHUD() {
        super("Target HUD", false, Category.HUD);
        targetHud.describe("Show a card for the living entity you look at, with health, distance and equipment.");
        accentColor.describe("Choose the target card's accent lines. Health and durability bars use the limitless theme color.");
    }

    public BooleanSetting targetHud() { return targetHud; }
    public ColorSetting accentColor() { return accentColor; }

    @Override protected void onDisable() { onWorldLeave(); }
    @Override public void onWorldLeave() {
        desiredTarget = null; displayedTarget = null; opacity = 0; displayedHealth = 0;
    }

    public void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled() || !targetHud.get()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        LivingEntity candidate = findTarget(client);
        if (candidate != desiredTarget) desiredTarget = candidate;

        if (displayedTarget != desiredTarget) {
            opacity = Math.max(0.0f, opacity - 0.16f);
            if (opacity <= 0.02f) {
                displayedTarget = desiredTarget;
                if (displayedTarget != null) displayedHealth = displayedTarget.getHealth();
            }
        } else if (displayedTarget != null) {
            opacity = Math.min(1.0f, opacity + 0.14f);
            displayedHealth += (displayedTarget.getHealth() - displayedHealth) * 0.22f;
        } else {
            opacity = Math.max(0.0f, opacity - 0.14f);
        }

        if (displayedTarget == null || opacity <= 0.01f) return;
        drawCard(context, client, displayedTarget);
    }

    private LivingEntity findTarget(MinecraftClient client) {
        if (client.player == null || !(client.crosshairTarget instanceof EntityHitResult hit)) return null;
        if (!(hit.getEntity() instanceof LivingEntity living) || living == client.player) return null;
        return client.player.canSee(living) ? living : null;
    }

    private void drawCard(DrawContext context, MinecraftClient client, LivingEntity target) {
        int width = 196;
        int height = 78;
        int x = (context.getScaledWindowWidth() - width) / 2;
        int y = context.getScaledWindowHeight() - height - 42;
        int alpha = Math.round(opacity * 255.0f);
        int panelAlpha = Math.round(opacity * 217.0f);
        int accent = Theme.withAlpha(accentColor.get(), alpha);

        context.fill(x + 2, y + 3, x + width + 2, y + height + 3, Theme.withAlpha(Theme.BORDER, alpha / 2));
        context.fill(x, y, x + width, y + height, Theme.withAlpha(Theme.BORDER, alpha));
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1,
                Theme.withAlpha(Theme.PANEL_BACKGROUND, panelAlpha));
        context.fill(x + 1, y + 1, x + width - 1, y + 2, accent);
        context.fill(x + 1, y + 2, x + 2, y + height - 1, accent);

        drawFace(context, target, x + 8, y + 9);
        String name = target.getName().getString();
        context.drawTextWithShadow(client.textRenderer, name, x + 36, y + 7,
                Theme.withAlpha(Theme.TEXT, alpha));

        float maxHealth = Math.max(1.0f, target.getMaxHealth());
        float health = Math.max(0.0f, Math.min(maxHealth, displayedHealth));
        String hp = String.format(java.util.Locale.ROOT, "%.1f / %.1f HP", health, maxHealth);
        double distance = client.player == null ? 0.0 : client.player.distanceTo(target);
        String distanceText = String.format(java.util.Locale.ROOT, "%.1fm", distance);
        int distanceX = x + width - client.textRenderer.getWidth(distanceText) - 8;
        context.drawTextWithShadow(client.textRenderer, distanceText, distanceX, y + 7,
                Theme.withAlpha(Theme.MUTED_TEXT, alpha));
        context.drawTextWithShadow(client.textRenderer, hp, x + 36, y + 20,
                Theme.withAlpha(Theme.TEXT, alpha));

        int barX = x + 36;
        int barY = y + 33;
        int barWidth = width - 46;
        context.fill(barX, barY, barX + barWidth, barY + 4, Theme.withAlpha(Theme.BORDER, alpha));
        int filled = Math.round(barWidth * Math.min(1.0f, health / maxHealth));
        if (filled > 0) context.fill(barX, barY, barX + filled, barY + 4,
                Theme.withAlpha(Theme.PRIMARY, alpha));

        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
        int itemX = x + 8;
        int itemY = y + 44;
        for (EquipmentSlot slot : slots) {
            ItemStack stack = target.getEquippedStack(slot);
            int slotWidth = 29;
            context.fill(itemX, itemY, itemX + 25, itemY + 20,
                    Theme.withAlpha(Theme.PANEL_BACKGROUND, panelAlpha));
            if (!stack.isEmpty()) {
                context.drawItem(stack, itemX + 4, itemY + 1);
                if (stack.isDamageable()) {
                    float durability = 1.0f - (float) stack.getDamage() / Math.max(1, stack.getMaxDamage());
                    context.fill(itemX + 3, itemY + 18, itemX + 22, itemY + 20,
                            Theme.withAlpha(Theme.BORDER, alpha));
                    context.fill(itemX + 3, itemY + 18, itemX + 3 + Math.round(19 * durability), itemY + 20,
                            Theme.withAlpha(Theme.PRIMARY, alpha));
                    String percent = Math.round(durability * 100.0f) + "%";
                    int textX = itemX + (25 - client.textRenderer.getWidth(percent)) / 2;
                    context.drawTextWithShadow(client.textRenderer, percent, textX, itemY + 20,
                            Theme.withAlpha(Theme.TEXT, alpha));
                }
            }
            itemX += slotWidth;
        }
    }

    private static void drawFace(DrawContext context, LivingEntity target, int x, int y) {
        if (target instanceof AbstractClientPlayerEntity player) {
            Identifier skin = player.getSkin().body().texturePath();
            context.drawTexture(RenderPipelines.GUI_TEXTURED, skin, x, y, 8.0f, 8.0f,
                    20, 20, 8, 8, 64, 64);
            context.drawTexture(RenderPipelines.GUI_TEXTURED, skin, x, y, 40.0f, 8.0f,
                    20, 20, 8, 8, 64, 64);
        } else if (target instanceof PlayerEntity) {
            context.fill(x, y, x + 20, y + 20, Theme.ACCENT);
        } else {
            context.fill(x, y, x + 20, y + 20, Theme.ACCENT);
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    target.getName(), x + 10, y + 6, Theme.TEXT);
        }
    }
}
