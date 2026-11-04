package com.example.mod.module;

import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.ModeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

/** Holds one movement direction and releases it cleanly when disabled. */
public final class AutoWalk extends Module {
    private final ModeSetting direction = addSetting(new ModeSetting("Direction", "Forward",
            "Forward", "Backward", "Left", "Right"));
    private final BooleanSetting stopAtUnloaded = addSetting(new BooleanSetting("Stop at unloaded chunks", true));
    private KeyBinding held;

    public AutoWalk() {
        super("Auto Walk", false, Category.MISC);
        direction.describe("Direction to walk automatically.");
        stopAtUnloaded.describe("Pause before entering a chunk that has not loaded yet.");
    }

    @Override protected void onDisable() { release(MinecraftClient.getInstance()); }
    @Override public void onWorldLeave() { release(MinecraftClient.getInstance()); }

    public void tick(MinecraftClient client) {
        if (!isEnabled() || !ActionSupport.ready(client)) { release(client); return; }
        KeyBinding wanted = switch (direction.get()) {
            case "Backward" -> client.options.backKey;
            case "Left" -> client.options.leftKey;
            case "Right" -> client.options.rightKey;
            default -> client.options.forwardKey;
        };
        if (held != null && held != wanted) release(client);
        if (stopAtUnloaded.get()) {
            double angle = Math.toRadians(client.player.getYaw());
            double dx = -Math.sin(angle), dz = Math.cos(angle);
            if (wanted == client.options.backKey) { dx = -dx; dz = -dz; }
            if (wanted == client.options.leftKey) { double t = dx; dx = dz; dz = -t; }
            if (wanted == client.options.rightKey) { double t = dx; dx = -dz; dz = t; }
            int x = Math.floorDiv((int) Math.floor(client.player.getX() + dx * 32), 16);
            int z = Math.floorDiv((int) Math.floor(client.player.getZ() + dz * 32), 16);
            if (!client.world.getChunkManager().isChunkLoaded(x, z)) { release(client); return; }
        }
        wanted.setPressed(true);
        held = wanted;
    }

    private void release(MinecraftClient client) {
        if (held != null) ActionSupport.restore(client, held);
        held = null;
    }
}
