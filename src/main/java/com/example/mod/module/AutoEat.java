package com.example.mod.module;

import com.example.mod.setting.IntegerSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

/** Eats only hotbar or offhand food, then returns to the player's previous slot. */
public final class AutoEat extends Module {
    private final IntegerSetting hunger = addSetting(new IntegerSetting("Hunger at or below", 16, 0, 20));
    private int originalSlot = -1;
    private int selectedByUs = -1;
    private boolean using;

    public AutoEat() {
        super("Auto Eat", false, Category.MISC);
        hunger.describe("Eat hotbar or offhand food when hunger reaches this value. Leaves special foods for manual use.");
    }

    @Override protected void onDisable() { stop(MinecraftClient.getInstance()); }
    @Override public void onWorldLeave() { stop(MinecraftClient.getInstance()); }

    public void tick(MinecraftClient client) {
        if (!isEnabled() || !ActionSupport.ready(client) || client.player.getHungerManager().getFoodLevel() > hunger.get()
                || ActionSupport.physicallyDown(client, client.options.useKey)) {
            stop(client);
            return;
        }
        if (client.player.isUsingItem() && !using) return;
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (safeFood(client.player.getInventory().getStack(i))) { slot = i; break; }
        }
        if (slot >= 0) {
            int current = client.player.getInventory().getSelectedSlot();
            if (originalSlot < 0) originalSlot = current;
            if (current != slot) client.player.getInventory().setSelectedSlot(slot);
            selectedByUs = slot;
        } else if (!safeFood(client.player.getOffHandStack())) {
            stop(client); return;
        } else if (!client.player.isUsingItem()) {
            client.interactionManager.interactItem(client.player, Hand.OFF_HAND);
        }
        client.options.useKey.setPressed(true);
        using = true;
    }

    private static boolean safeFood(ItemStack stack) {
        if (stack.isEmpty() || !stack.contains(DataComponentTypes.FOOD)) return false;
        String path = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getPath();
        return !path.contains("golden_apple") && !path.equals("chorus_fruit")
                && !path.equals("pufferfish") && !path.equals("poisonous_potato");
    }

    private void stop(MinecraftClient client) {
        if (using) ActionSupport.restore(client, client.options.useKey);
        using = false;
        if (originalSlot >= 0 && client.player != null
                && client.player.getInventory().getSelectedSlot() == selectedByUs)
            client.player.getInventory().setSelectedSlot(originalSlot);
        originalSlot = selectedByUs = -1;
    }
}
