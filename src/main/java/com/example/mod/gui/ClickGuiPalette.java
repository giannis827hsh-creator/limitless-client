package com.example.mod.gui;

import com.example.mod.Theme;

/** Click GUI colors share limitless's electric-purple theme. */
public final class ClickGuiPalette {
    public static final int PANEL = Theme.PANEL_BACKGROUND;
    public static final int BORDER = Theme.BORDER;
    public static final int TEXT = Theme.TEXT;
    public static final int MUTED = Theme.MUTED_TEXT;
    public static final int PRIMARY = Theme.PRIMARY;
    public static final int HOVER = Theme.ACCENT;
    public static final int TRACK = Theme.TRACK;
    public static final int BACKDROP = 0x94040408;
    public static final int MODAL_PANEL = Theme.withAlpha(PANEL, 242);
    public static final int TOOLTIP_PANEL = Theme.withAlpha(PANEL, 250);
    public static final int INPUT = Theme.SURFACE;
    public static final int HEADER_HOVER = Theme.withAlpha(Theme.ACCENT, 39);
    public static final int BADGE_BACKGROUND = Theme.withAlpha(PRIMARY, 30);
    public static final int DIVIDER = 0xFF25222D;
    private ClickGuiPalette() { }
}
