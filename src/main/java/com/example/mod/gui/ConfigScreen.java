package com.example.mod.gui;

import com.example.mod.Brand;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class ConfigScreen extends Screen {
    private static final int W = 300, H = 205;
    private final Screen parent;
    private TextFieldWidget nameField;
    private List<String> names = List.of();
    private int selected = -1, scroll, left, top;
    private String status = "Save a config or import a share code";

    public ConfigScreen(Screen parent) {
        super(Text.literal("Configs"));
        this.parent = parent;
    }

    @Override protected void init() {
        left = (width - W) / 2;
        top = Math.max(8, (height - H) / 2);
        nameField = addDrawableChild(new TextFieldWidget(textRenderer, left + 18, top + 41,
                W - 36, 15, Text.literal("Config name")));
        nameField.setDrawsBackground(false);
        nameField.setMaxLength(32);
        nameField.setPlaceholder(Text.literal("Config name"));
        refresh();
    }

    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB8070610);
        GuiDraw.surface(ctx, left, top, W, H, 6, ClickGuiPalette.PRIMARY, 1);
        text(ctx, Brand.SHORT_NAME + "  /  Configs", left + 12, top + 12, ClickGuiPalette.TEXT);
        button(ctx, "Back", 250, 7, 38, 18, mouseX, mouseY, false);
        GuiDraw.rounded(ctx, left + 12, top + 36, W - 24, 24, 4, ClickGuiPalette.INPUT);
        text(ctx, "NAME", left + 13, top + 29, ClickGuiPalette.MUTED);
        button(ctx, "Save current", 12, 66, 134, 22, mouseX, mouseY, true);
        button(ctx, "Load selected", 154, 66, 134, 22, mouseX, mouseY, false);
        text(ctx, "SAVED CONFIGS", left + 13, top + 99, ClickGuiPalette.MUTED);
        ctx.enableScissor(left + 12, top + 109, left + W - 12, top + 157);
        for (int row = 0; row < 3 && row + scroll < names.size(); row++) {
            int index = row + scroll, y = top + 110 + row * 16;
            if (index == selected) GuiDraw.rounded(ctx, left + 12, y, W - 24, 15, 3, ClickGuiPalette.INPUT);
            text(ctx, GuiDraw.fit(textRenderer, names.get(index), W - 42), left + 18, y + 3,
                    index == selected ? ClickGuiPalette.PRIMARY : ClickGuiPalette.TEXT);
        }
        ctx.disableScissor();
        button(ctx, "Copy code", 12, 163, 82, 20, mouseX, mouseY, false);
        button(ctx, "Import code", 100, 163, 90, 20, mouseX, mouseY, false);
        button(ctx, "Delete", 196, 163, 92, 20, mouseX, mouseY, false);
        text(ctx, GuiDraw.fit(textRenderer, status, W - 24), left + 12, top + 191, ClickGuiPalette.MUTED);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void button(DrawContext ctx, String title, int x, int y, int w, int h,
                        int mouseX, int mouseY, boolean primary) {
        boolean hover = GuiDraw.inside(mouseX, mouseY, left + x, top + y, w, h);
        int background = primary ? ClickGuiPalette.PRIMARY : hover ? ClickGuiPalette.HEADER_HOVER : ClickGuiPalette.INPUT;
        GuiDraw.rounded(ctx, left + x, top + y, w, h, 4, background);
        int color = primary ? 0xFF0D0818 : hover ? ClickGuiPalette.PRIMARY : ClickGuiPalette.TEXT;
        text(ctx, title, left + x + (w - textRenderer.getWidth(title)) / 2, top + y + (h - 8) / 2, color);
    }

    private void text(DrawContext ctx, String value, int x, int y, int color) {
        ctx.drawText(textRenderer, value, x, y, color, false);
    }

    @Override public boolean mouseClicked(Click click, boolean doubled) {
        double x = click.x(), y = click.y();
        if (inside(x, y, 250, 7, 38, 18)) { close(); return true; }
        if (inside(x, y, 12, 66, 134, 22)) {
            act(() -> ConfigManager.save(nameField.getText().strip()), "Saved config"); return true;
        }
        if (inside(x, y, 154, 66, 134, 22)) {
            act(() -> ConfigManager.load(selectedName(), client), "Loaded config"); return true;
        }
        if (inside(x, y, 12, 163, 82, 20)) {
            act(() -> client.keyboard.setClipboard(ConfigManager.share(selectedName())), "Copied share code"); return true;
        }
        if (inside(x, y, 100, 163, 90, 20)) {
            act(() -> ConfigManager.importShared(nameField.getText().strip(), client.keyboard.getClipboard()), "Imported config"); return true;
        }
        if (inside(x, y, 196, 163, 92, 20)) {
            act(() -> ConfigManager.delete(selectedName()), "Deleted config"); return true;
        }
        if (inside(x, y, 12, 109, W - 24, 48)) {
            int index = scroll + (int) ((y - top - 110) / 16);
            if (index >= 0 && index < names.size()) {
                selected = index;
                nameField.setText(names.get(index));
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private boolean inside(double x, double y, int bx, int by, int w, int h) {
        return GuiDraw.inside(x, y, left + bx, top + by, w, h);
    }

    private String selectedName() throws IOException {
        if (selected < 0 || selected >= names.size()) throw new IOException("Select a config first");
        return names.get(selected);
    }

    private void act(ConfigAction action, String success) {
        try { action.run(); status = success; refresh(); }
        catch (IOException | IllegalArgumentException exception) { status = exception.getMessage(); }
    }

    private void refresh() {
        try {
            String previous = selected >= 0 && selected < names.size() ? names.get(selected) : null;
            names = ConfigManager.names();
            selected = previous == null ? -1 : names.indexOf(previous);
            scroll = Math.min(scroll, Math.max(0, names.size() - 3));
        } catch (IOException exception) { status = exception.getMessage(); }
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scroll = (int) ClickGuiLayout.clamp(scroll - Math.signum(vertical), 0, Math.max(0, names.size() - 3));
        return true;
    }

    @Override public void close() { if (client != null) client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }

    @FunctionalInterface private interface ConfigAction { void run() throws IOException; }
}
