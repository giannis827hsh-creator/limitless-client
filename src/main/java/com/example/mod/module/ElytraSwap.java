package com.example.mod.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;

/** One key press swaps the chest slot between an elytra and a stored chestplate. */
public final class ElytraSwap extends Module {
    private boolean pending;

    public ElytraSwap() { super("Elytra Swap", false, Category.COMBAT); }

    @Override protected void onEnable() { pending = true; }
    @Override protected void onDisable() { pending = false; }
    @Override public void onWorldLeave() { pending = false; }

    public void tick(MinecraftClient client) {
        if (!isEnabled() || !pending || !ActionSupport.ready(client)
                || client.player.currentScreenHandler != client.player.playerScreenHandler
                || !client.player.playerScreenHandler.getCursorStack().isEmpty()) return;
        pending = false;
        ItemStack chest = client.player.getEquippedStack(EquipmentSlot.CHEST);
        boolean wearingElytra = chest.isOf(Items.ELYTRA);
        int source = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (!wearingElytra && stack.isOf(Items.ELYTRA)
                    || wearingElytra && stack.isIn(ItemTags.CHEST_ARMOR)) {
                source = i < 9 ? 36 + i : i;
                break;
            }
        }
        if (source >= 0) {
            int syncId = client.player.playerScreenHandler.syncId;
            client.interactionManager.clickSlot(syncId, source, 0, SlotActionType.PICKUP, client.player);
            client.interactionManager.clickSlot(syncId, 6, 0, SlotActionType.PICKUP, client.player);
            client.interactionManager.clickSlot(syncId, source, 0, SlotActionType.PICKUP, client.player);
        }
        setEnabled(false);
    }
}
