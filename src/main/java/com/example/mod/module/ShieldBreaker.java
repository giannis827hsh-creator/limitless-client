package com.example.mod.module;

import com.example.mod.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;

/** Switches to a hotbar axe and attacks a raised shield at full charge. */
public final class ShieldBreaker extends Module {
    private final BooleanSetting switchBack = addSetting(new BooleanSetting("Switch back", true));
    private int original = -1, chosen = -1, cooldown;
    private boolean acted;

    public ShieldBreaker() {
        super("Shield Breaker", false, Category.COMBAT);
        switchBack.describe("Return to your previous hotbar slot after an axe hit.");
    }

    @Override protected void onDisable() { reset(MinecraftClient.getInstance()); }
    @Override public void onWorldLeave() { reset(MinecraftClient.getInstance()); }

    public void tick(MinecraftClient client) {
        acted = false;
        if (!isEnabled() || !ActionSupport.ready(client)) { reset(client); return; }
        if (cooldown > 0) cooldown--;
        if (original >= 0) {
            if (cooldown == 0) restore(client);
            return;
        }
        if (cooldown > 0 || !(client.crosshairTarget instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof PlayerEntity target) || target == client.player
                || !target.isAlive() || !target.isUsingItem() || client.player.isUsingItem()
                || !target.getActiveItem().isOf(Items.SHIELD)
                || client.player.getAttackCooldownProgress(0.0f) < 0.99f) return;
        int axeSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getStack(i).getItem() instanceof AxeItem) {
                axeSlot = i; break;
            }
        }
        if (axeSlot < 0) return;
        original = client.player.getInventory().getSelectedSlot();
        chosen = axeSlot;
        if (original != chosen) client.player.getInventory().setSelectedSlot(chosen);
        client.interactionManager.attackEntity(client.player, target);
        client.player.swingHand(Hand.MAIN_HAND);
        acted = true;
        cooldown = 10;
    }

    public boolean actedThisTick() { return acted; }

    private void restore(MinecraftClient client) {
        if (switchBack.get() && client.player != null && original >= 0
                && client.player.getInventory().getSelectedSlot() == chosen)
            client.player.getInventory().setSelectedSlot(original);
        original = chosen = -1;
    }

    private void reset(MinecraftClient client) { restore(client); cooldown = 0; acted = false; }
}
