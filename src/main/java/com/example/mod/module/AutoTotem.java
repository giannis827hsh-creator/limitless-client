package com.example.mod.module;

import com.example.mod.setting.IntegerSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/** Refills an empty offhand from the hotbar. */
public final class AutoTotem extends Module {
    private final IntegerSetting delay = addSetting(new IntegerSetting("Refill delay ticks", 3, 1, 20));
    private int ticks;

    public AutoTotem() {
        super("Auto Totem", false, Category.COMBAT);
        delay.describe("Wait this many ticks between offhand refill attempts from the hotbar.");
    }

    @Override public void onWorldLeave() { ticks = 0; }

    public void tick(MinecraftClient client) {
        if (!isEnabled() || !ActionSupport.ready(client)
                || client.player.currentScreenHandler != client.player.playerScreenHandler) return;
        if (!client.player.getOffHandStack().isEmpty()
                || !client.player.playerScreenHandler.getCursorStack().isEmpty()) {
            ticks = 0;
            return;
        }
        if (++ticks < delay.get()) return;
        ticks = 0;
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                client.interactionManager.clickSlot(client.player.playerScreenHandler.syncId,
                        36 + i, 40, SlotActionType.SWAP, client.player);
                return;
            }
        }
    }
}
