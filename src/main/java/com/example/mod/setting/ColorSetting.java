package com.example.mod.setting;

/** ARGB color option; the Click GUI cycles through a small built-in palette. */
public final class ColorSetting extends Setting {
    private static final int[] PALETTE = {
            0xFF26D9FF, 0xFFFF5555, 0xFF55FF55, 0xFFFFFF55, 0xFFFF55FF, 0xFFFFFFFF
    };
    private int color;

    public ColorSetting(String name, int initialColor) {
        super(name);
        color = initialColor;
    }

    public int get() { return color; }
    public void set(int color) { this.color = color | 0xFF000000; }
    public void cycle() {
        for (int i = 0; i < PALETTE.length; i++) {
            if (PALETTE[i] == color) {
                color = PALETTE[(i + 1) % PALETTE.length];
                return;
            }
        }
        color = PALETTE[0];
    }
}
