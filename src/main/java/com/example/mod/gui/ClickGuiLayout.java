package com.example.mod.gui;

/** Logical coordinates shared by rendering, clipping, pointer input and saved positions. */
public final class ClickGuiLayout {
    public static final int PANEL_COUNT = 6;
    public static final int PANEL_WIDTH = 140, GAP = 10, HEADER = 20, ROW = 17;
    public static final int GROUP_WIDTH = PANEL_WIDTH * PANEL_COUNT + GAP * (PANEL_COUNT - 1);
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
    public static float scale(int width, int height) {
        return Math.max(0.1f, Math.min(0.85f, Math.min((width - 24f) / GROUP_WIDTH, (height - 24f) / 320f)));
    }
    public static Rect panel(int viewportWidth, int viewportHeight, double x, double y, int contentHeight, boolean collapsed) {
        int h = collapsed ? HEADER : HEADER + Math.max(ROW, contentHeight) + 6;
        h = Math.min(h, viewportHeight - 16);
        return new Rect((int) clamp(x, 8, viewportWidth - PANEL_WIDTH - 8),
                (int) clamp(y, 8, viewportHeight - h - 8), PANEL_WIDTH, h);
    }
    public static Rect drawer(int viewportWidth, int viewportHeight, Rect anchor) {
        int w = Math.min(270, viewportWidth - 16), h = Math.min(310, viewportHeight - 32);
        int x = anchor.x + anchor.width + GAP;
        if (x + w > viewportWidth - 8) x = anchor.x - w - GAP;
        return new Rect((int) clamp(x, 8, viewportWidth - w - 8),
                (int) clamp(anchor.y, 8, viewportHeight - h - 8), w, h);
    }
    public static double local(double pixel, float scale) { return pixel / scale; }
    public static double clamp(double value, double min, double max) { return Math.max(min, Math.min(Math.max(min, max), value)); }
    private ClickGuiLayout() { }
}
