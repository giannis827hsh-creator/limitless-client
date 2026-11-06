package com.example.mod;

/** Shared limitless UI palette. Colors are ARGB for Minecraft's DrawContext. */
public final class Theme {
    public static final int PRIMARY = 0xFF8B5CF6;          // #8B5CF6
    public static final int ACCENT = 0xFF241C37;
    public static final int BORDER = 0xFF37303F;
    public static final int PANEL_BACKGROUND = 0xF20C0C10;
    public static final int TEXT = 0xFFF2F0F7;
    public static final int MUTED_TEXT = 0xFFA39DAC;
    public static final int SURFACE = 0xFF16151C;
    public static final int TRACK = 0xFF2A2732;

    private Theme() { }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }
}
