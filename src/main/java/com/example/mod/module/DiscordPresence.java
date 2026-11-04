package com.example.mod.module;

import com.example.mod.discord.DiscordRpcClient;
import com.example.mod.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

public final class DiscordPresence extends Module {
    private static final String APPLICATION_ID = "1567287158933557268";
    private static final String LOGO_ASSET = "limitless";
    private final BooleanSetting showServer = addSetting(new BooleanSetting("Show server", true));
    private final DiscordRpcClient rpc = new DiscordRpcClient();
    private int ticks;

    public DiscordPresence() {
        super("DiscordPresence", false, Category.HUD);
    }

    @Override protected void onEnable() {
        rpc.start();
    }

    @Override protected void onDisable() {
        rpc.stop();
    }

    public void tick(MinecraftClient client) {
        if (!isEnabled() || ++ticks % 20 != 0) return;
        String state = "In menus";
        if (client.world != null) {
            state = "In singleplayer";
            if (!client.isInSingleplayer()) {
                state = "In multiplayer";
                if (showServer.get()) {
                    ServerInfo server = client.getCurrentServerEntry();
                    if (server != null && server.address != null && !server.address.isBlank())
                        state = "Playing on " + server.address;
                }
            }
        }
        rpc.update(APPLICATION_ID, state, LOGO_ASSET);
    }
}
