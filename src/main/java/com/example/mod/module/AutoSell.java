package com.example.mod.module;

import com.example.mod.setting.StringSetting;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

/** Operates a server /sell menu only after this module requested it. */
public final class AutoSell extends Module {
    private final StringSetting neverSell = addSetting(new StringSetting("Never sell IDs",
            "minecraft:totem_of_undying,minecraft:golden_apple,minecraft:enchanted_golden_apple,minecraft:netherite_sword", 2048));
    private long commandAt, openedUntil, nextAction;
    private int menuId = -1;
    private boolean awaitingConfirm;

    public AutoSell() {
        super("Auto Sell", false, Category.DONUT);
        neverSell.describe("Comma-separated item IDs to keep when using the server /sell menu.");
    }

    @Override protected void onEnable() { reset(); }
    @Override protected void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && menuId >= 0 && client.player.currentScreenHandler.syncId == menuId)
            client.player.closeHandledScreen();
        reset();
    }
    @Override public void onWorldLeave() { reset(); }

    private void reset() { commandAt = openedUntil = nextAction = 0; menuId = -1; awaitingConfirm = false; }

    public void tick(MinecraftClient client) {
        if (!isEnabled() || client.player == null || client.world == null || client.interactionManager == null) return;
        long now = System.currentTimeMillis();
        ScreenHandler handler = client.player.currentScreenHandler;
        if (menuId < 0 && openedUntil > now && client.currentScreen instanceof HandledScreen<?>
                && handler != client.player.playerScreenHandler && handler.slots.size() >= 54
                && client.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT).contains("sell")) {
            menuId = handler.syncId;
            nextAction = now + 350;
        }
        if (menuId < 0) {
            if (client.currentScreen == null && now - commandAt >= 4000) {
                client.player.networkHandler.sendChatCommand("sell");
                commandAt = now; openedUntil = now + 8000;
            }
            return;
        }
        if (!(client.currentScreen instanceof HandledScreen<?>) || handler.syncId != menuId) {
            menuId = -1; awaitingConfirm = false; return;
        }
        if (now < nextAction) return;
        nextAction = now + 350;
        if (awaitingConfirm) {
            awaitingConfirm = false;
            if (handler.slots.size() > 53 && handler.getSlot(53).hasStack()) {
                client.interactionManager.clickSlot(menuId, 53, 0, SlotActionType.PICKUP, client.player);
                nextAction = now + 900;
            }
            return;
        }
        Set<String> excluded = parse(neverSell.get());
        int moved = 0;
        for (Slot slot : handler.slots) {
            if (slot.inventory != client.player.getInventory() || slot.getIndex() >= 36 || !slot.hasStack()) continue;
            ItemStack stack = slot.getStack();
            String id = Registries.ITEM.getId(stack.getItem()).toString();
            if (excluded.contains(id) || excluded.contains(id.substring(id.indexOf(':') + 1))) continue;
            client.interactionManager.clickSlot(menuId, slot.id, 0, SlotActionType.QUICK_MOVE, client.player);
            if (++moved >= 4) break;
        }
        if (moved > 0) awaitingConfirm = true;
        else if (handler.slots.size() <= 53)
            client.player.sendMessage(Text.literal("[limitless] Sell menu layout changed; Auto Sell stopped."), false);
    }

    private static Set<String> parse(String raw) {
        Set<String> ids = new HashSet<>();
        for (String part : raw.toLowerCase(Locale.ROOT).split("[,\\s]+"))
            if (!part.isBlank()) ids.add(part.trim());
        return ids;
    }
}
