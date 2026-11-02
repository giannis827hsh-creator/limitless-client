package com.example.mod.gui;

import com.example.mod.module.Module;
import java.util.Locale;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Compact pixel-font row with a white animated slider switch. */
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
        if (hover > 0 || settingsOpen) GuiDraw.rounded(ctx, x + 2, y + 1, width - 4, height - 2, 2,
                GuiDraw.fade(ClickGuiPalette.HOVER, opacity * (settingsOpen ? 0.8f : hover * 0.65f)));
        if (settingsOpen) ctx.fill(x + 2, y + 5, x + 3, y + height - 5, GuiDraw.fade(ClickGuiPalette.PRIMARY, opacity));
        String label = GuiDraw.fit(font, module.name().toUpperCase(Locale.ROOT), width - 40);
        GuiDraw.text(ctx, font, label, x + 7, y + (height - 8) / 2,
                GuiDraw.fade(ClickGuiPalette.TEXT, opacity));
        int sx = x + width - 25, sy = y + (height - 8) / 2;
        GuiDraw.rounded(ctx, sx, sy, 18, 8, 4,
                GuiDraw.fade(GuiDraw.mix(ClickGuiPalette.TRACK, ClickGuiPalette.PRIMARY, enabled), opacity));
        GuiDraw.rounded(ctx, sx + 1 + Math.round(10 * enabled), sy + 1, 6, 6, 3,
                GuiDraw.fade(ClickGuiPalette.TEXT, opacity));
    }
    public boolean contains(double mx, double my) { return GuiDraw.inside(mx, my, x, y, width, height); }
}
