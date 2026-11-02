package com.example.mod.gui;

import com.example.mod.Theme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Small DrawContext primitives; alpha is applied per draw for the 1.21.11 GUI pipeline. */
public final class GuiDraw {
    private GuiDraw() { }

    public static void rounded(DrawContext context, int x, int y, int width, int height,
                               int radius, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0) {
            context.fill(x, y, x + width, y + height, color);
            return;
        }
        // Each pixel row is drawn once, so translucent corners never accumulate opacity.
        context.fill(x, y + r, x + width, y + height - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            int inset = (int) Math.ceil(r - Math.sqrt(r * r - dy * dy));
            context.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            context.fill(x + inset, y + height - row - 1, x + width - inset, y + height - row, color);
        }
    }

    public static int fade(int color, float opacity) {
        return Theme.withAlpha(color, Math.round((color >>> 24) * Math.max(0, Math.min(1, opacity))));
    }

    /** Draw only the border; translucent panel interiors retain their original opacity. */
    public static void outline(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        if (width < 3 || height < 3) return;
        int r = Math.max(1, Math.min(radius, Math.min(width, height) / 2));
        for (int row = 0; row < height; row++) {
            int edge = Math.min(row, height - row - 1);
            int outer = cornerInset(r, edge);
            if (row == 0 || row == height - 1) {
                context.fill(x + outer, y + row, x + width - outer, y + row + 1, color);
            } else {
                int inner = 1 + cornerInset(r - 1, edge - 1);
                context.fill(x + outer, y + row, x + inner, y + row + 1, color);
                context.fill(x + width - inner, y + row, x + width - outer, y + row + 1, color);
            }
        }
    }
    private static int cornerInset(int radius, int row) {
        if (radius <= 0 || row >= radius) return 0;
        double dy = radius - row - 0.5;
        return (int) Math.ceil(radius - Math.sqrt(radius * radius - dy * dy));
    }

    public static int mix(int from, int to, float amount) {
        float t = Math.max(0, Math.min(1, amount));
        int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * t);
        int r = Math.round(((from >>> 16) & 255) + (((to >>> 16) & 255) - ((from >>> 16) & 255)) * t);
        int g = Math.round(((from >>> 8) & 255) + (((to >>> 8) & 255) - ((from >>> 8) & 255)) * t);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void text(DrawContext context, TextRenderer renderer, String text,
                            int x, int y, int color) {
        context.drawText(renderer, text, x, y, color, false);
    }

    public static String fit(TextRenderer renderer, String text, int width) {
        if (width <= 0) return "";
        if (renderer.getWidth(text) <= width) return text;
        String suffix = "...";
        if (renderer.getWidth(suffix) > width) return renderer.trimToWidth(text, width);
        return renderer.trimToWidth(text, width - renderer.getWidth(suffix)) + suffix;
    }

    public static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public static float approach(float current, float target, float speed, float dt) {
        float value = current + (target - current) * (1.0f - (float) Math.exp(-speed * dt));
        return Math.abs(value - target) < 0.001f ? target : value;
    }
}
