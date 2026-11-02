package com.example.mod.gui;

import com.example.mod.module.Module;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** One independently draggable reference-style category window. */
public final class CategoryPanel {
    public enum Tab {
        COMBAT, MISC, DONUT, RENDER, HUB, BASEFINDER;
        public boolean accepts(Module module) {
            return accepts(module.category());
        }
        public boolean accepts(Module.Category category) {
            return switch (this) {
                case COMBAT -> category == Module.Category.COMBAT;
                case MISC -> category == Module.Category.MISC || category == Module.Category.MOVEMENT;
                case DONUT -> category == Module.Category.DONUT;
                case RENDER -> category == Module.Category.VISUALS;
                case HUB -> category == Module.Category.HUD;
                case BASEFINDER -> category == Module.Category.BASEFINDER;
            };
        }
    }
    private final Tab tab;
    private final List<ModuleButton> buttons = new ArrayList<>();
    private final List<SettingComponent> inlineSettings = new ArrayList<>();
    private ClickGuiLayout.Rect bounds = new ClickGuiLayout.Rect(0, 0, 0, 0);
    private double x, y, scroll;
    private boolean collapsed;
    private Module expandedModule;
    private int keybindY = -1;
    public CategoryPanel(Tab tab, List<Module> modules) {
        this.tab = tab;
        for (Module module : modules) if (tab.accepts(module)) buttons.add(new ModuleButton(module));
    }
    public Tab tab() { return tab; }
    public ClickGuiLayout.Rect bounds() { return bounds; }
    public double x() { return x; }
    public double y() { return y; }
    public Module expandedModule() { return expandedModule; }
    public List<SettingComponent> inlineSettings() { return inlineSettings; }
    private int contentHeight() {
        int height = buttons.size() * ClickGuiLayout.ROW;
        if (expandedModule != null) {
            for (SettingComponent component : inlineSettings) if (component.isVisible()) height += component.height();
            height += 22;
        }
        return height;
    }
    public void position(double x, double y, int viewportWidth, int viewportHeight) {
        bounds = ClickGuiLayout.panel(viewportWidth, viewportHeight, x, y, contentHeight(), collapsed);
        this.x = bounds.x(); this.y = bounds.y();
        scroll(0);
    }
    public void refresh(int viewportWidth, int viewportHeight) {
        if (!collapsed && bounds.height() != Math.min(ClickGuiLayout.HEADER + Math.max(ClickGuiLayout.ROW,
                contentHeight()) + 6, viewportHeight - 16)) position(x, y, viewportWidth, viewportHeight);
    }
    public void toggleInline(Module module, int viewportWidth, int viewportHeight) {
        if (expandedModule == module) closeInline(viewportWidth, viewportHeight);
        else {
            blurInline();
            inlineSettings.clear();
            expandedModule = module;
            for (var setting : module.settings()) inlineSettings.add(new SettingComponent(setting, true));
            position(x, y, viewportWidth, viewportHeight);
        }
    }
    public void closeInline(int viewportWidth, int viewportHeight) {
        blurInline(); inlineSettings.clear(); expandedModule = null; keybindY = -1;
        position(x, y, viewportWidth, viewportHeight);
    }
    public void blurInline() { for (SettingComponent component : inlineSettings) component.blur(); }
    public boolean inlineContains(double mx, double my) {
        return expandedModule != null && !collapsed && bounds.contains(mx, my)
                && my >= viewTop() && my < viewBottom();
    }
    public boolean inlineKeybindContains(double mx, double my) {
        return inlineContains(mx, my) && keybindY >= 0
                && GuiDraw.inside(mx, my, bounds.x() + 4, keybindY, bounds.width() - 8, 21);
    }
    public SettingComponent clickInline(double mx, double my, int button) {
        if (!inlineContains(mx, my)) return null;
        for (SettingComponent component : inlineSettings)
            if (component.isVisible() && component.mouseClicked(mx, my, button)) return component;
        return null;
    }
    public boolean headerContains(double mx, double my) {
        return GuiDraw.inside(mx, my, bounds.x(), bounds.y(), bounds.width(), ClickGuiLayout.HEADER);
    }
    public void collapse(int viewportWidth, int viewportHeight) {
        collapsed = !collapsed;
        position(x, y, viewportWidth, viewportHeight);
    }
    public int viewTop() { return bounds.y() + ClickGuiLayout.HEADER; }
    public int viewBottom() { return bounds.y() + bounds.height() - 4; }
    public void scroll(double amount) {
        int max = contentHeight() - Math.max(0, viewBottom() - viewTop());
        scroll = ClickGuiLayout.clamp(scroll + amount, 0, Math.max(0, max));
    }
    public ModuleButton hovered(double mx, double my) {
        if (collapsed || my < viewTop() || my >= viewBottom() || !bounds.contains(mx, my)) return null;
        int offset = (int) (my - viewTop() + scroll);
        for (ModuleButton button : buttons) {
            if (offset < ClickGuiLayout.ROW) return button;
            offset -= ClickGuiLayout.ROW;
            if (button.module() == expandedModule) {
                int extra = 22;
                for (SettingComponent component : inlineSettings) if (component.isVisible()) extra += component.height();
                if (offset < extra) return null;
                offset -= extra;
            }
        }
        return null;
    }
    public void render(DrawContext ctx, TextRenderer font, double mx, double my, Module settings,
                       float opacity, float dt) {
        int x = bounds.x(), y = bounds.y(), w = bounds.width(), h = bounds.height();
        GuiDraw.rounded(ctx, x + 2, y + 3, w, h, 5, GuiDraw.fade(0x60000000, opacity));
        GuiDraw.rounded(ctx, x, y, w, h, 5, GuiDraw.fade(ClickGuiPalette.PANEL, opacity));
        GuiDraw.outline(ctx, x, y, w, h, 5, GuiDraw.fade(ClickGuiPalette.BORDER, opacity));
        if (headerContains(mx, my)) GuiDraw.rounded(ctx, x + 2, y + 2, w - 4, ClickGuiLayout.HEADER - 4, 3,
                GuiDraw.fade(ClickGuiPalette.HEADER_HOVER, opacity));
        String title = tab.name();
        GuiDraw.text(ctx, font, title, x + (w - font.getWidth(title)) / 2, y + (ClickGuiLayout.HEADER - 8) / 2,
                GuiDraw.fade(ClickGuiPalette.PRIMARY, opacity));
        if (collapsed) return;
        ctx.fill(x + 7, viewTop() - 1, x + w - 7, viewTop(), GuiDraw.fade(ClickGuiPalette.DIVIDER, opacity));
        ctx.enableScissor(x + 2, viewTop(), x + w - 2, viewBottom());
        try {
            if (buttons.isEmpty()) GuiDraw.text(ctx, font, "No modules yet", x + 10, viewTop() + 6,
                    GuiDraw.fade(ClickGuiPalette.MUTED, opacity));
            int cy = viewTop() - (int) scroll;
            keybindY = -1;
            for (ModuleButton button : buttons) {
                button.render(ctx, font, x + 3, cy, w - 6, ClickGuiLayout.ROW,
                        settings == button.module() || expandedModule == button.module(), mx, my, opacity, dt);
                cy += ClickGuiLayout.ROW;
                if (button.module() == expandedModule) {
                    for (SettingComponent component : inlineSettings) {
                        if (!component.isVisible()) { component.hide(); continue; }
                        component.render(ctx, font, x + 4, cy, w - 8, mx, my, opacity, dt);
                        cy += component.height();
                    }
                    keybindY = cy;
                    ctx.fill(x + 6, cy + 20, x + w - 6, cy + 21,
                            GuiDraw.fade(ClickGuiPalette.DIVIDER, opacity));
                    GuiDraw.text(ctx, font, "Keybind", x + 9, cy + 6, GuiDraw.fade(ClickGuiPalette.TEXT, opacity));
                    String key = GuiDraw.fit(font, expandedModule.keyName(), w - 62);
                    GuiDraw.text(ctx, font, key, x + w - 9 - font.getWidth(key), cy + 6,
                            GuiDraw.fade(ClickGuiPalette.PRIMARY, opacity));
                    cy += 22;
                }
            }
        } finally { ctx.disableScissor(); }
        int content = contentHeight(), view = viewBottom() - viewTop();
        if (content > view) {
            int thumb = Math.max(12, view * view / content);
            int offset = (int) ((view - thumb) * scroll / (content - view));
            ctx.fill(x + w - 3, viewTop() + offset, x + w - 2, viewTop() + offset + thumb,
                    GuiDraw.fade(ClickGuiPalette.PRIMARY, opacity));
        }
    }
}
