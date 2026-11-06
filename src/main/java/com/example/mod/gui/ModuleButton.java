package com.example.mod.gui;

import com.example.mod.module.Module;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Animated module row inside a floating category. */
public final class ModuleButton {
    private final Module module;
    private int x, y, width, height;
    private float hover, enabled;
    public ModuleButton(Module module) { this.module = module; enabled = module.isEnabled() ? 1 : 0; }
    public Module module() { return module; }
    public void render(DrawContext ctx, TextRenderer font, int x, int y, int width, int height,
                       boolean settingsOpen, double mx, double my, float opacity, float dt) {
        this.x = x; this.y = y; this.width = width; this.height = height;
        hover = GuiDraw.approach(hover, contains(mx, my) ? 1 : 0, 16, dt);
        enabled = GuiDraw.approach(enabled, module.isEnabled() ? 1 : 0, 18, dt);
        float highlight = Math.max(enabled * 0.32f, settingsOpen ? 0.9f : hover * 0.7f);
        GuiDraw.rounded(ctx, x + 2, y + 1, width - 4, height - 2, 2,
                GuiDraw.fade(ClickGuiPalette.HOVER, opacity * highlight));
        if (settingsOpen) ctx.fill(x + 2, y + 5, x + 3, y + height - 5, GuiDraw.fade(ClickGuiPalette.PRIMARY, opacity));
        String label = GuiDraw.fit(font, module.name(), width - 40);
        GuiDraw.text(ctx, font, label, x + 7, y + (height - 8) / 2,
                GuiDraw.fade(GuiDraw.mix(ClickGuiPalette.MUTED, ClickGuiPalette.TEXT,
                        Math.max(enabled, hover)), opacity));
        int sx = x + width - 25, sy = y + (height - 8) / 2;
        GuiDraw.switchControl(ctx, sx, sy, 18, 8, enabled, opacity);
    }
    public boolean contains(double mx, double my) { return GuiDraw.inside(mx, my, x, y, width, height); }
}
