package com.example.mod.module;

import com.example.mod.setting.IntegerSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/** Attempts to rejoin the last multiplayer server after a disconnect screen appears. */
public final class AutoReconnect extends Module {
    private final IntegerSetting delay = addSetting(new IntegerSetting("Delay seconds", 5, 3, 60));
    private ServerInfo lastServer;
    private Object screen;
    private long reconnectAt;

    public AutoReconnect() {
        super("Auto Reconnect", false, Category.MISC);
        delay.describe("Seconds to wait on a disconnect screen before reconnecting.");
    }

    @Override protected void onDisable() { screen = null; reconnectAt = 0; }

    public void tick(MinecraftClient client) {
        if (!isEnabled()) return;
        if (client.world != null && client.player != null) {
            if (!client.isInSingleplayer()) lastServer = client.getCurrentServerEntry();
            screen = null; reconnectAt = 0;
            return;
        }
        if (lastServer == null || !(client.currentScreen instanceof DisconnectedScreen)) {
            screen = null; reconnectAt = 0; return;
        }
        if (screen != client.currentScreen) {
            screen = client.currentScreen;
            reconnectAt = System.nanoTime() + delay.get() * 1_000_000_000L;
        }
        if (System.nanoTime() < reconnectAt || reconnectAt == 0) return;
        reconnectAt = 0;
        ServerInfo server = lastServer;
        client.setScreen(null);
        ConnectScreen.connect(new MultiplayerScreen(new TitleScreen()), client,
                ServerAddress.parse(server.address), server, false, null);
    }
}
