package com.example.limitlessaddon;

import com.example.mod.api.AddonModule;
import com.example.mod.api.AddonCategory;
import com.example.mod.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class HelloModule extends AddonModule {
    private final BooleanSetting notify = addSetting(new BooleanSetting("Notify on enable", true));

    public HelloModule(AddonCategory category) {
        super("Hello Addon", category);
    }

    @Override
    protected void onEnable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (notify.get() && client.player != null)
            client.player.sendMessage(Text.literal("Hello from a limitless addon"), false);
    }
}
