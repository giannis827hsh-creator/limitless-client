package com.example.mod.module;

import com.example.mod.setting.ModeSetting;
import com.example.mod.setting.StringSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/** Sends one explicitly targeted teleport request per activation. */
public final class TpaMacro extends Module {
    private final StringSetting player = addSetting(new StringSetting("Player", "", 32));
    private final ModeSetting command = addSetting(new ModeSetting("Request", "TPA", "TPA", "TPA Here"));
    private boolean pending;

    public TpaMacro() {
        super("TPA Macro", false, Category.DONUT);
        player.describe("Username to request a teleport from. Set this before toggling the module.");
        command.describe("TPA teleports you to them; TPA Here asks them to come to you.");
    }

    @Override protected void onEnable() { pending = true; }
    @Override protected void onDisable() { pending = false; }

    public void tick(MinecraftClient client) {
        if (!isEnabled() || !pending || !ActionSupport.ready(client)) return;
        pending = false;
        String name = player.get().trim();
        if (name.matches("[A-Za-z0-9_.]{3,20}")) {
            client.player.networkHandler.sendChatCommand(
                    (command.get().equals("TPA Here") ? "tpahere " : "tpa ") + name);
        } else {
            client.player.sendMessage(Text.literal("[limitless] Set a valid player name in TPA Macro settings."), false);
        }
        setEnabled(false);
    }
}
