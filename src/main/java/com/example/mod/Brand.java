package com.example.mod;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;

/** Shared lowercase name and supplied emblem for client screens. */
public final class Brand {
    public static final String NAME = "limitless client";
    public static final String SHORT_NAME = "limitless";
    private static final Identifier ICON = Identifier.of(LimitlessClient.MOD_ID, "icon.png");

    private Brand() { }

    public static void drawIcon(DrawContext context, int x, int y, int size) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, ICON, x, y, 0.0f, 0.0f,
                size, size, 1280, 1280, 1280, 1280);
    }
}
