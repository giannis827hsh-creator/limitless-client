package com.example.limitlessaddon;

import com.example.mod.api.AddonRegistry;
import com.example.mod.api.AddonCategory;
import com.example.mod.api.LimitlessAddon;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.text.Text;

public final class HelloAddon implements LimitlessAddon {
    @Override
    public void onInitialize(AddonRegistry registry) {
        AddonCategory category = new AddonCategory("hello", "Hello");
        registry.registerCategory(category);
        registry.register(new HelloModule(category));
        registry.registerCommand(dispatcher -> dispatcher.register(
                ClientCommandManager.literal("helloaddon").executes(context -> {
                    context.getSource().sendFeedback(Text.literal("Hello from a limitless addon"));
                    return 1;
                })));
    }
}
