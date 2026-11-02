package com.example.mod.module;

import net.minecraft.client.MinecraftClient;

/** Holds the vanilla sprint key while moving forward. */
public final class Sprint extends Module {
    private boolean holding;

    public Sprint() { super("Sprint", false, Category.COMBAT); }

    @Override protected void onDisable() { release(MinecraftClient.getInstance()); }
    @Override public void onWorldLeave() { release(MinecraftClient.getInstance()); }

    public void tick(MinecraftClient client) {
        if (!isEnabled() || !ActionSupport.ready(client) || !client.options.forwardKey.isPressed()) {
            release(client); return;
        }
        client.options.sprintKey.setPressed(true);
        holding = true;
    }

    private void release(MinecraftClient client) {
        if (holding) ActionSupport.restore(client, client.options.sprintKey);
        holding = false;
    }
}
