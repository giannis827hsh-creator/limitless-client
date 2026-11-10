package com.example.mod.api;

import com.example.mod.module.Module;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public abstract class AddonModule extends Module {
    private final String addonCategory;

    protected AddonModule(String name, Category category) {
        super(name, false, category);
        addonCategory = null;
    }

    protected AddonModule(String name, AddonCategory category) {
        super(name, false, Category.MISC);
        addonCategory = category.id();
    }

    public final String addonCategory() { return addonCategory; }
    public void onClientTick(MinecraftClient client) { }
    public void onWorldRender(WorldRenderContext context) { }
    public void onHudRender(DrawContext context, RenderTickCounter ticks) { }
}
