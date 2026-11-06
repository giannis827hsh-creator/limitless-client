package com.example.mod.gui;

import com.example.mod.Brand;
import com.example.mod.LimitlessClient;
import com.example.mod.module.Module;
import com.example.mod.module.BlockESP;
import com.example.mod.module.SusChunk;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Compact floating categories, with shared coordinates for drawing and input. */
public class LimitlessClickGUI extends Screen {
    private final List<CategoryPanel> panels = new ArrayList<>();
    private final List<SettingComponent> components = new ArrayList<>();
    private final SettingTooltip settingTooltip = new SettingTooltip();
    private final Set<Integer> pressedKeys = new HashSet<>();
    private CategoryPanel draggingPanel, settingsAnchor;
    private Module settingsModule, bindingModule;
    private SettingComponent draggingSetting;
    private ClickGuiLayout.Rect drawer;
    private float scale = 1, opacity;
    private int viewportWidth, viewportHeight;
    private double mouseX = -10000, mouseY = -10000, dragX, dragY, settingsScroll;
    private boolean closing, initialized;
    private long lastFrame, noticeUntil;
    private String notice = "";
    private TextFieldWidget searchBox;

    public LimitlessClickGUI() {
        super(Text.literal(Brand.NAME));
        for (CategoryPanel.Tab tab : CategoryPanel.Tab.values())
            panels.add(new CategoryPanel(tab, LimitlessClient.MODULES.all()));
    }

    @Override protected void init() {
        scale = ClickGuiLayout.scale(width, height);
        viewportWidth = (int) (width / scale);
        viewportHeight = (int) (height / scale);
        int left = (viewportWidth - ClickGuiLayout.GROUP_WIDTH) / 2;
        for (CategoryPanel panel : panels) {
            panel.reserveTop(40);
            int order = panel.tab().ordinal();
            double defaultX = left + order * (ClickGuiLayout.PANEL_WIDTH + ClickGuiLayout.GAP);
            double px = initialized ? panel.x() : GuiState.INSTANCE.panelX(panel.tab().name(),
                    defaultX, viewportWidth - ClickGuiLayout.PANEL_WIDTH - 16);
            double py = initialized ? panel.y() : GuiState.INSTANCE.panelY(panel.tab().name(),
                    30, viewportHeight - ClickGuiLayout.HEADER - 16);
            panel.position(px, py, viewportWidth, viewportHeight);
        }
        initialized = true; draggingPanel = null;
        clearFocus(); pressedKeys.clear(); lastFrame = System.nanoTime();
        layoutDrawer();
        String query = searchBox == null ? "" : searchBox.getText();
        int searchWidth = Math.min(150, viewportWidth - 32);
        searchBox = null;
        if (searchWidth >= 70) {
            searchBox = addSelectableChild(new TextFieldWidget(textRenderer, viewportWidth - searchWidth - 19, 15,
                    searchWidth, 14, Text.literal("Search modules")));
            searchBox.setDrawsBackground(false);
            searchBox.setEditableColor(ClickGuiPalette.TEXT);
            searchBox.setPlaceholder(Text.literal("Search modules..."));
            searchBox.setMaxLength(64);
            searchBox.setChangedListener(value -> panels.forEach(panel -> panel.filter(value)));
            searchBox.setText(query);
        } else panels.forEach(panel -> panel.filter(""));
    }

    @Override public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, Math.max(0.001f, (now - lastFrame) / 1_000_000_000f));
        lastFrame = now;
        opacity = GuiDraw.approach(opacity, closing ? 0 : 1, 18, dt);
        if (closing && opacity < 0.015f) { if (client != null) client.setScreen(null); return; }
        mouseX = local(mx); mouseY = local(my);
        renderBackdrop(ctx);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(scale, scale);
        try {
            if (searchBox != null && settingsModule == null) {
                GuiDraw.surface(ctx, searchBox.getX() - 8, 6, searchBox.getWidth() + 16, 25, 5,
                        ClickGuiPalette.PRIMARY, opacity);
                searchBox.render(ctx, (int) mouseX, (int) mouseY, delta);
            }
            for (CategoryPanel panel : panels) panel.refresh(viewportWidth, viewportHeight);
            CategoryPanel hovered = settingsModule == null && !closing ? panelAt(mouseX, mouseY) : null;
            for (CategoryPanel panel : panels) panel.render(ctx, textRenderer,
                    panel == hovered ? mouseX : -10000, panel == hovered ? mouseY : -10000,
                    settingsModule, opacity, dt);
            if (settingsModule != null) renderSettings(ctx, dt);
            renderHint(ctx, now);
            renderSettingTooltip(ctx, now);
        } finally { ctx.getMatrices().popMatrix(); }
    }

    private void renderBackdrop(DrawContext ctx) {
        ctx.fill(0, 0, width, height, GuiDraw.fade(ClickGuiPalette.BACKDROP, opacity));
        // A bounded 64-column light field: strongest at the top center, fading into the scene.
        int glowHeight = Math.max(1, (int) (height * 0.55f));
        for (int col = 0; col < 64; col++) {
            float dx = ((col + 0.5f) / 64f - 0.5f) * 2;
            int alpha = Math.round(28 * (float) Math.exp(-dx * dx * 9) * opacity);
            int glow = ClickGuiPalette.PRIMARY & 0x00FFFFFF;
            int top = (alpha << 24) | glow;
            int middle = (Math.round(alpha * 0.20f) << 24) | glow;
            int x1 = col * width / 64, x2 = (col + 1) * width / 64;
            ctx.fillGradient(x1, 0, x2, glowHeight / 3, top, middle);
            ctx.fillGradient(x1, glowHeight / 3, x2, glowHeight, middle, glow);
        }
    }

    private void layoutDrawer() {
        drawer = settingsAnchor == null ? null : ClickGuiLayout.drawer(viewportWidth, viewportHeight, settingsAnchor.bounds());
        settingsScroll = ClickGuiLayout.clamp(settingsScroll, 0, maxSettingsScroll());
    }
    private int settingsTop() { return drawer.y() + 91; }
    private int settingsBottom() { return drawer.y() + drawer.height() - 10; }
    private int settingsContentHeight() { return components.stream().filter(SettingComponent::isVisible).mapToInt(c -> c.height() + 5).sum(); }
    private double maxSettingsScroll() {
        return drawer == null ? 0 : Math.max(0, settingsContentHeight() - (settingsBottom() - settingsTop()));
    }
    private void renderSettings(DrawContext ctx, float dt) {
        if (drawer == null) return;
        settingsScroll = ClickGuiLayout.clamp(settingsScroll, 0, maxSettingsScroll());
        int x = drawer.x(), y = drawer.y(), w = drawer.width(), h = drawer.height();
        // Modal capture keeps covered rows from receiving accidental toggles or key bindings.
        ctx.fill(0, 0, viewportWidth, viewportHeight, GuiDraw.fade(0x50030705, opacity));
        GuiDraw.surface(ctx, x, y, w, h, 6, ClickGuiPalette.PRIMARY, opacity, ClickGuiPalette.MODAL_PANEL);
        label(ctx, GuiDraw.fit(textRenderer, settingsModule.name(), w - 42), x + 12, y + 12, ClickGuiPalette.TEXT);
        label(ctx, "x", x + w - 20, y + 12, ClickGuiPalette.MUTED);
        label(ctx, "SETTINGS", x + 12, y + 29, ClickGuiPalette.PRIMARY);
        {
            String badge = settingsModule.isEnabled() ? "Enabled" : "Disabled";
            int bw = textRenderer.getWidth(badge) + 16;
            GuiDraw.rounded(ctx, x + w - bw - 12, y + 26, bw, 16, 8, GuiDraw.fade(ClickGuiPalette.BADGE_BACKGROUND, opacity));
            label(ctx, badge, x + w - bw - 4, y + 30, ClickGuiPalette.PRIMARY);
        }
        GuiDraw.rounded(ctx, x + 10, y + 53, w - 20, 26, 4,
                GuiDraw.fade(bindingModule == settingsModule ? ClickGuiPalette.HOVER : ClickGuiPalette.INPUT, opacity));
        label(ctx, "Keybind", x + 18, y + 62, ClickGuiPalette.TEXT);
        String key = GuiDraw.fit(textRenderer, bindingModule == settingsModule ? "Press a key..." : settingsModule.keyName(), w - 105);
        label(ctx, key, x + w - 18 - textRenderer.getWidth(key), y + 62, ClickGuiPalette.PRIMARY);
        ctx.enableScissor(x + 8, settingsTop(), x + w - 8, settingsBottom());
        try {
            int cy = settingsTop() - (int) settingsScroll;
            for (SettingComponent component : components) {
                if (!component.isVisible()) {
                    component.hide();
                    if (draggingSetting == component) draggingSetting = null;
                    continue;
                }
                component.render(ctx, textRenderer, x + 10, cy, w - 24,
                        overSettings(mouseX, mouseY) && !closing ? mouseX : -10000,
                        overSettings(mouseX, mouseY) && !closing ? mouseY : -10000, opacity, dt);
                cy += component.height() + 5;
            }
            if (components.isEmpty()) label(ctx, "No additional settings", x + 14, settingsTop() + 9, ClickGuiPalette.MUTED);
        } finally { ctx.disableScissor(); }
        if (maxSettingsScroll() > 0) {
            int vh = settingsBottom() - settingsTop();
            int thumb = Math.max(12, vh * vh / settingsContentHeight());
            int offset = (int) ((vh - thumb) * settingsScroll / maxSettingsScroll());
            GuiDraw.rounded(ctx, x + w - 5, settingsTop() + offset, 2, thumb, 1, GuiDraw.fade(ClickGuiPalette.PRIMARY, opacity));
        }
    }
    private void renderHint(DrawContext ctx, long now) {
        String text = now < noticeUntil ? notice : bindingModule != null
                ? "Press a key / Delete clears / Esc cancels" : settingsModule != null
                ? "Hover for help / Scroll for settings / Esc to return" : "Left click: toggle   Right click: settings   Drag headers to move";
        ModuleButton hovered = hoveredButton(mouseX, mouseY);
        if (hovered != null && now >= noticeUntil) text = hovered.module() instanceof BlockESP
                ? "Block ESP  /  Right click to choose blocks"
                : hovered.module().name() + "  /  " + hovered.module().keyName() + "  /  Press a key to bind";
        text = GuiDraw.fit(textRenderer, text, viewportWidth - 32);
        int x = (viewportWidth - textRenderer.getWidth(text)) / 2;
        label(ctx, text, x, viewportHeight - 17, ClickGuiPalette.MUTED);
    }
    private void renderSettingTooltip(DrawContext ctx, long now) {
        if (closing || bindingModule != null) {
            settingTooltip.clear(); return;
        }
        Object target = null;
        String title = "", help = "";
        if (settingsModule != null && drawer != null) {
            if (GuiDraw.inside(mouseX, mouseY, drawer.x() + 10, drawer.y() + 53, drawer.width() - 20, 26)) {
                target = settingsModule; title = "Keybind";
                help = "Click, then press a key to toggle this module. Delete clears the binding; Esc cancels.";
            } else if (overSettings(mouseX, mouseY)) {
                for (SettingComponent component : components) {
                    help = component.helpAt(mouseX, mouseY);
                    if (!help.isEmpty()) { target = component; title = component.label(); break; }
                }
            }
        } else {
            CategoryPanel panel = panelAt(mouseX, mouseY);
            if (panel != null && panel.inlineContains(mouseX, mouseY)) {
                if (panel.inlineKeybindContains(mouseX, mouseY)) {
                    target = panel.expandedModule(); title = "Keybind";
                    help = "Click, then press a key to toggle this module. Delete clears the binding; Esc cancels.";
                } else for (SettingComponent component : panel.inlineSettings()) {
                    help = component.helpAt(mouseX, mouseY);
                    if (!help.isEmpty()) { target = component; title = component.label(); break; }
                }
            }
        }
        settingTooltip.hover(target, title, help, now);
        settingTooltip.render(ctx, textRenderer, mouseX, mouseY, viewportWidth, viewportHeight, opacity, now);
    }
    private void label(DrawContext ctx, String text, int x, int y, int color) {
        GuiDraw.text(ctx, textRenderer, text, x, y, GuiDraw.fade(color, opacity));
    }
    private CategoryPanel panelAt(double mx, double my) {
        for (int i = panels.size() - 1; i >= 0; i--) if (panels.get(i).bounds().contains(mx, my)) return panels.get(i);
        return null;
    }
    private ModuleButton hoveredButton(double mx, double my) {
        if (settingsModule != null || closing) return null;
        CategoryPanel panel = panelAt(mx, my);
        return panel == null ? null : panel.hovered(mx, my);
    }
    private boolean overSettings(double mx, double my) {
        return drawer != null && GuiDraw.inside(mx, my, drawer.x() + 8, settingsTop(), drawer.width() - 16, settingsBottom() - settingsTop());
    }
    private void openSettings(CategoryPanel panel, Module module) {
        clearFocus(); settingsAnchor = panel; settingsModule = module; settingsScroll = 0;
        components.clear();
        for (var setting : module.settings()) components.add(new SettingComponent(setting));
        layoutDrawer();
    }
    public void showBlockOptions(BlockESP module) {
        for (CategoryPanel panel : panels) {
            if (panel.tab() == CategoryPanel.Tab.RENDER) {
                openSettings(panel, module);
                break;
            }
        }
        if (client != null) client.setScreen(this);
    }
    public void saveStateNow() { saveState(); }
    private void closeSettings() { clearFocus(); settingsModule = null; settingsAnchor = null; drawer = null; components.clear(); saveState(); }

    @Override public void mouseMoved(double x, double y) { mouseX = local(x); mouseY = local(y); }
    @Override public boolean mouseClicked(Click click, boolean doubled) {
        if (closing) return true;
        if (searchBox != null && settingsModule == null) {
            boolean searchClick = GuiDraw.inside(local(click.x()), local(click.y()), searchBox.getX() - 8, 6,
                    searchBox.getWidth() + 16, 25);
            searchBox.setFocused(searchClick && click.button() == 0);
            if (searchClick) {
                searchBox.mouseClicked(new Click(local(click.x()), local(click.y()), click.buttonInfo()), doubled);
                searchBox.setFocused(click.button() == 0);
                return true;
            }
        }
        double mx = local(click.x()), my = local(click.y());
        mouseX = mx; mouseY = my;
        if (settingsModule != null && drawer != null) {
            if (!drawer.contains(mx, my) || GuiDraw.inside(mx, my, drawer.x() + drawer.width() - 28, drawer.y() + 5, 24, 25)) {
                closeSettings(); return true;
            }
            clearFocus();
            if (click.button() == 0 && GuiDraw.inside(mx, my, drawer.x() + 10, drawer.y() + 53, drawer.width() - 20, 26)) {
                bindingModule = settingsModule; return true;
            }
            if (overSettings(mx, my)) for (SettingComponent c : components) if (c.isVisible() && c.mouseClicked(mx, my, click.button())) {
                draggingSetting = click.button() == 0 ? c : null; break;
            }
            return true;
        }
        CategoryPanel panel = panelAt(mx, my);
        clearFocus();
        if (panel == null) return true;
        panels.remove(panel); panels.add(panel);
        if (panel.headerContains(mx, my)) {
            if (click.button() == 0) { draggingPanel = panel; dragX = mx - panel.x(); dragY = my - panel.y(); }
            else if (click.button() == 1) panel.collapse(viewportWidth, viewportHeight);
        } else {
            if (panel.inlineKeybindContains(mx, my) && click.button() == 0) {
                bindingModule = panel.expandedModule(); return true;
            }
            SettingComponent inline = panel.clickInline(mx, my, click.button());
            if (inline != null) {
                draggingSetting = click.button() == 0 ? inline : null;
                panel.refresh(viewportWidth, viewportHeight);
                return true;
            }
            ModuleButton row = panel.hovered(mx, my);
            if (row != null) {
                if (click.button() == 0) row.module().toggle();
                else if (click.button() == 1) {
                    if (row.module() instanceof BlockESP blockEsp) {
                        if (client != null) client.setScreen(new BlockPickerScreen(this, blockEsp));
                    } else if (row.module() instanceof SusChunk) panel.toggleInline(row.module(), viewportWidth, viewportHeight);
                    else openSettings(panel, row.module());
                }
            }
        }
        return true;
    }
    @Override public boolean mouseDragged(Click click, double dx, double dy) {
        if (closing || click.button() != 0) return true;
        if (draggingPanel != null) draggingPanel.position(local(click.x()) - dragX, local(click.y()) - dragY, viewportWidth, viewportHeight);
        else if (draggingSetting != null) draggingSetting.mouseDragged(local(click.x()), local(click.y()));
        return true;
    }
    @Override public boolean mouseReleased(Click click) {
        if (click.button() == 0) {
            if (draggingSetting != null) draggingSetting.mouseReleased();
            draggingSetting = null; draggingPanel = null; saveState();
        }
        return true;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (closing || draggingSetting != null || draggingPanel != null) return true;
        double mx = local(x), my = local(y);
        if (settingsModule != null) {
            if (overSettings(mx, my)) settingsScroll = ClickGuiLayout.clamp(settingsScroll - vertical * 28, 0, maxSettingsScroll());
        } else {
            CategoryPanel panel = panelAt(mx, my);
            if (panel != null) panel.scroll(-vertical * ClickGuiLayout.ROW);
        }
        return true;
    }
    @Override public boolean keyPressed(KeyInput input) {
        if (closing) return true;
        int key = input.key();
        if (searchBox != null && searchBox.isFocused() && settingsModule == null) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) searchBox.setFocused(false);
            else searchBox.keyPressed(input);
            return true;
        }
        boolean first = pressedKeys.add(key);
        for (SettingComponent c : components) if (c.isEditing()) return c.keyPressed(input);
        if (!first) return true;
        if (bindingModule != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { bindingModule = null; return true; }
            if (key != GLFW.GLFW_KEY_RIGHT_SHIFT) { bind(bindingModule, key); return true; }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && settingsModule != null) { closeSettings(); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            for (CategoryPanel panel : panels) if (panel.expandedModule() != null) {
                panel.closeInline(viewportWidth, viewportHeight); return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_RIGHT_SHIFT) { close(); return true; }
        ModuleButton hovered = hoveredButton(mouseX, mouseY);
        if (hovered != null) bind(hovered.module(), key);
        return true;
    }
    private void bind(Module module, int key) {
        if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) key = GLFW.GLFW_KEY_UNKNOWN;
        if (key != GLFW.GLFW_KEY_UNKNOWN && !Module.isBindableKey(key)) return;
        module.setKeybind(key); bindingModule = null;
        notice = module.name() + " / " + module.keyName(); noticeUntil = System.nanoTime() + 2_500_000_000L;
        saveState();
    }
    @Override public boolean keyReleased(KeyInput input) { pressedKeys.remove(input.key()); return true; }
    @Override public boolean charTyped(CharInput input) {
        if (!closing && searchBox != null && searchBox.isFocused() && settingsModule == null)
            return searchBox.charTyped(input);
        if (!closing) for (SettingComponent c : components) if (c.isEditing()) return c.charTyped(input);
        return true;
    }
    private void clearFocus() {
        settingTooltip.clear();
        for (SettingComponent c : components) c.blur();
        for (CategoryPanel panel : panels) panel.blurInline();
        draggingSetting = null; bindingModule = null;
    }
    private void saveState() {
        if (!initialized) return;
        for (CategoryPanel panel : panels) GuiState.INSTANCE.panelPosition(panel.tab().name(), panel.x(), panel.y(),
                viewportWidth - ClickGuiLayout.PANEL_WIDTH - 16, viewportHeight - ClickGuiLayout.HEADER - 16);
        GuiState.INSTANCE.saveModules(LimitlessClient.MODULES);
    }
    @Override public void close() {
        if (closing) return;
        closing = true; draggingPanel = null; clearFocus(); saveState();
    }
    @Override public void removed() { clearFocus(); saveState(); super.removed(); }
    private double local(double pixel) { return ClickGuiLayout.local(pixel, scale); }
    @Override protected void applyBlur(DrawContext context) { }
    @Override public void renderBackground(DrawContext context, int mx, int my, float delta) { }
    @Override public boolean shouldPause() { return false; }
}
