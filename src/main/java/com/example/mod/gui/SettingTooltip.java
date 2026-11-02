package com.example.mod.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** One delayed, wrapped tooltip. Text layout is cached until the hovered control changes. */
public final class SettingTooltip {
    private static final long DELAY_NANOS = 300_000_000L;
    private Object target;
    private String title = "", description = "", fittedTitle = "";
    private long hoveredSince;
    private int layoutWidth = -1, boxWidth;
    private List<String> lines = List.of();

    public void hover(Object target, String title, String description, long now) {
        if (description.isBlank()) target = null;
        if (this.target != target || !this.description.equals(description) || !this.title.equals(title)) {
            this.target = target; this.title = title; this.description = description;
            hoveredSince = now; layoutWidth = -1;
        }
    }

    public void clear() { hover(null, "", "", 0); }

    public void render(DrawContext ctx, TextRenderer font, double mouseX, double mouseY,
                       int viewportWidth, int viewportHeight, float opacity, long now) {
        if (target == null || now - hoveredSince < DELAY_NANOS) return;
        int maxWidth = Math.max(24, Math.min(280, viewportWidth - 16));
        if (layoutWidth != maxWidth) {
            layoutWidth = maxWidth;
            fittedTitle = GuiDraw.fit(font, title, maxWidth - 20);
            lines = wrap(font, description, maxWidth - 20);
            int contentWidth = font.getWidth(fittedTitle);
            for (String line : lines) contentWidth = Math.max(contentWidth, font.getWidth(line));
            boxWidth = Math.min(maxWidth, contentWidth + 20);
        }
        int boxHeight = 28 + lines.size() * 12;
        int x = (int) mouseX + 12, y = (int) mouseY + 16;
        if (x + boxWidth > viewportWidth - 8) x = (int) mouseX - boxWidth - 10;
        if (y + boxHeight > viewportHeight - 25) y = (int) mouseY - boxHeight - 10;
        x = Math.max(8, Math.min(x, viewportWidth - boxWidth - 8));
        y = Math.max(8, Math.min(y, viewportHeight - boxHeight - 25));
        GuiDraw.rounded(ctx, x + 2, y + 3, boxWidth, boxHeight, 5, GuiDraw.fade(0xA0000000, opacity));
        GuiDraw.rounded(ctx, x, y, boxWidth, boxHeight, 5, GuiDraw.fade(ClickGuiPalette.TOOLTIP_PANEL, opacity));
        GuiDraw.outline(ctx, x, y, boxWidth, boxHeight, 5, GuiDraw.fade(ClickGuiPalette.BORDER, opacity));
        GuiDraw.text(ctx, font, fittedTitle, x + 10, y + 8, GuiDraw.fade(ClickGuiPalette.PRIMARY, opacity));
        for (int i = 0; i < lines.size(); i++)
            GuiDraw.text(ctx, font, lines.get(i), x + 10, y + 23 + i * 12, GuiDraw.fade(ClickGuiPalette.TEXT, opacity));
    }

    private static List<String> wrap(TextRenderer font, String text, int width) {
        List<String> lines = new ArrayList<>();
        String remaining = text.strip();
        while (!remaining.isEmpty()) {
            String fitted = font.trimToWidth(remaining, Math.max(1, width));
            int cut = fitted.length();
            if (cut == 0) break;
            if (cut < remaining.length()) {
                int space = fitted.lastIndexOf(' ');
                if (space > 0) cut = space;
            }
            lines.add(remaining.substring(0, cut).stripTrailing());
            remaining = remaining.substring(cut).stripLeading();
        }
        return List.copyOf(lines);
    }
}
