package com.example.mod;

/** Shared limitless UI palette. Colors are ARGB for Minecraft's DrawContext. */
public final class Theme {
    public static final int PRIMARY = 0xFF8B5CF6;          // #8B5CF6
    public static final int ACCENT = 0xFF6D28D9;
    public static final int BORDER = 0xFF4C1D95;
    public static final int PANEL_BACKGROUND = 0xE0080810;
    public static final int TEXT = 0xFFFFFFFF;             // #FFFFFF
    public static final int MUTED_TEXT = 0xFFD8DEE9;

    private Theme() { }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }
}
