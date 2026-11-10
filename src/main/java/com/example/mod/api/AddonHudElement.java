package com.example.mod.api;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

public interface AddonHudElement {
    Identifier id();
    void render(DrawContext context, RenderTickCounter ticks);
}
