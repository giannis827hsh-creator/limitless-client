package com.example.mod.gui;

import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.ColorSetting;
import com.example.mod.setting.EnumSetting;
import com.example.mod.setting.IntegerSetting;
import com.example.mod.setting.ModeSetting;
import com.example.mod.setting.NumberSetting;
import com.example.mod.setting.Setting;
import com.example.mod.setting.StringSetting;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/** One reusable control in the module drawer; coordinates are in the GUI's local space. */
public final class SettingComponent {
    private static final int[] SWATCHES = {
            ClickGuiPalette.PRIMARY, 0xFF26D9FF, 0xFFFF5555, 0xFF55FF55,
            0xFFFFFF55, 0xFFFF55FF, 0xFFFFAA33, ClickGuiPalette.TEXT
    };

    private final Setting setting;
    private final boolean compact;
    private int x;
    private int y;
    private int width;
    private boolean laidOut;
    private boolean dragging;
    private boolean editing;
    private float hover, toggleProgress;

    public SettingComponent(Setting setting) {
        this(setting, false);
    }

    public SettingComponent(Setting setting, boolean compact) {
        this.setting = Objects.requireNonNull(setting, "setting");
        this.compact = compact;
        toggleProgress = setting instanceof BooleanSetting bool && bool.get() ? 1 : 0;
    }

    public boolean isVisible() { return setting.isVisible(); }
    public String label() { return setting.label(); }

    public String helpAt(double mouseX, double mouseY) {
        return isVisible() && laidOut && GuiDraw.inside(mouseX, mouseY, x, y, width,
                compact ? height() : height() - 4)
                ? setting.description() : "";
    }

    /** A hidden row must relinquish stale hit bounds and keyboard/drag focus. */
    public void hide() { blur(); laidOut = false; }

    public int height() {
        if (compact) {
            if (setting instanceof NumberSetting || setting instanceof IntegerSetting) return 31;
            return 21;
        }
        if (setting instanceof ColorSetting) return 66;
        if (setting instanceof NumberSetting || setting instanceof IntegerSetting) return 46;
        if (setting instanceof ModeSetting || setting instanceof EnumSetting
                || setting instanceof StringSetting) return 52;
        return 34;
    }

    public void render(DrawContext context, TextRenderer renderer, int x, int y, int width,
                       double mouseX, double mouseY, float opacity, float dt) {
        this.x = x;
        this.y = y;
        this.width = Math.max(24, width);
        laidOut = true;
        if (setting instanceof BooleanSetting bool)
            toggleProgress = GuiDraw.approach(toggleProgress, bool.get() ? 1 : 0, 18, dt);
        if (compact) {
            renderCompact(context, renderer, mouseX, mouseY, opacity);
            return;
        }
        boolean hovered = GuiDraw.inside(mouseX, mouseY, x, y, this.width, height() - 4);
        hover = GuiDraw.approach(hover, hovered ? 1.0f : 0.0f, 12.0f, dt);
        int row = GuiDraw.mix(ClickGuiPalette.PANEL, ClickGuiPalette.HOVER, hover * 0.35f);
        GuiDraw.rounded(context, x, y, this.width, height() - 4, 5, GuiDraw.fade(row, opacity));

        if (setting instanceof BooleanSetting bool) {
            label(context, renderer, setting.label(), x + 10, y + 10, this.width - 56,
                    ClickGuiPalette.TEXT, opacity);
            int switchX = x + this.width - 34;
            GuiDraw.switchControl(context, switchX, y + 9, 24, 12, toggleProgress, opacity);
        } else if (setting instanceof NumberSetting number) {
            renderSlider(context, renderer, format(number.get()),
                    fraction(number.get(), number.min(), number.max()), opacity);
        } else if (setting instanceof IntegerSetting integer) {
            renderSlider(context, renderer, Integer.toString(integer.get()),
                    fraction(integer.get(), integer.min(), integer.max()), opacity);
        } else if (setting instanceof ColorSetting color) {
            renderColor(context, renderer, color, opacity);
        } else if (setting instanceof ModeSetting mode) {
            renderMode(context, renderer, mode.get(), opacity);
        } else if (setting instanceof EnumSetting mode) {
            renderMode(context, renderer, mode.get(), opacity);
        } else if (setting instanceof StringSetting string) {
            renderString(context, renderer, string, opacity);
        } else {
            label(context, renderer, setting.label(), x + 10, y + 10, this.width - 20,
                    ClickGuiPalette.TEXT, opacity);
        }
    }

    private void renderCompact(DrawContext context, TextRenderer renderer,
                               double mouseX, double mouseY, float opacity) {
        boolean hovered = GuiDraw.inside(mouseX, mouseY, x, y, width, height());
        if (hovered) context.fill(x, y, x + width, y + height(), GuiDraw.fade(ClickGuiPalette.HOVER, opacity));
        context.fill(x + 2, y + height() - 1, x + width - 2, y + height(),
                GuiDraw.fade(ClickGuiPalette.DIVIDER, opacity));
        int labelWidth = width - 37;
        if (setting instanceof BooleanSetting bool) {
            label(context, renderer, setting.label(), x + 5, y + 6, labelWidth,
                    ClickGuiPalette.TEXT, opacity);
            GuiDraw.switchControl(context, x + width - 27, y + 6, 21, 9, toggleProgress, opacity);
        } else if (setting instanceof IntegerSetting integer) {
            compactSlider(context, renderer, Integer.toString(integer.get()),
                    fraction(integer.get(), integer.min(), integer.max()), opacity);
        } else if (setting instanceof NumberSetting number) {
            compactSlider(context, renderer, format(number.get()),
                    fraction(number.get(), number.min(), number.max()), opacity);
        } else if (setting instanceof ColorSetting color) {
            label(context, renderer, setting.label(), x + 5, y + 6, labelWidth,
                    ClickGuiPalette.TEXT, opacity);
            GuiDraw.rounded(context, x + width - 18, y + 4, 13, 13, 3,
                    GuiDraw.fade(color.get(), opacity));
        } else {
            label(context, renderer, setting.label(), x + 5, y + 6, labelWidth,
                    ClickGuiPalette.TEXT, opacity);
        }
    }

    private void compactSlider(DrawContext context, TextRenderer renderer, String value,
                               double fraction, float opacity) {
        int valueWidth = renderer.getWidth(value);
        label(context, renderer, setting.label(), x + 5, y + 3,
                width - valueWidth - 16, ClickGuiPalette.TEXT, opacity);
        GuiDraw.text(context, renderer, value, x + width - valueWidth - 5, y + 3,
                GuiDraw.fade(ClickGuiPalette.PRIMARY, opacity));
        int trackWidth = Math.max(1, width - 14);
        int filled = (int) Math.round(trackWidth * fraction);
        GuiDraw.rounded(context, x + 7, y + 22, trackWidth, 2, 1,
                GuiDraw.fade(ClickGuiPalette.TRACK, opacity));
        if (filled > 0) GuiDraw.rounded(context, x + 7, y + 22, filled, 2, 1,
                GuiDraw.fade(ClickGuiPalette.PRIMARY, opacity));
        GuiDraw.rounded(context, x + 4 + filled, y + 19, 6, 8, 3,
                GuiDraw.fade(ClickGuiPalette.TEXT, opacity));
    }

    private void renderSlider(DrawContext context, TextRenderer renderer, String value,
                              double fraction, float opacity) {
        String fittedValue = GuiDraw.fit(renderer, value, Math.max(20, width / 3));
        int valueWidth = renderer.getWidth(fittedValue);
        label(context, renderer, setting.label(), x + 10, y + 7,
                width - valueWidth - 30, ClickGuiPalette.TEXT, opacity);
        GuiDraw.text(context, renderer, fittedValue, x + width - valueWidth - 10, y + 7,
                GuiDraw.fade(ClickGuiPalette.PRIMARY, opacity));
        int trackWidth = Math.max(1, width - 24);
        int filled = (int) Math.round(trackWidth * fraction);
        GuiDraw.rounded(context, x + 12, y + 28, trackWidth, 4, 2,
                GuiDraw.fade(ClickGuiPalette.TRACK, opacity));
        if (filled > 0) {
            GuiDraw.rounded(context, x + 12, y + 28, filled, 4, 2,
                    GuiDraw.fade(ClickGuiPalette.PRIMARY, opacity));
        }
        GuiDraw.rounded(context, x + 9 + filled, y + 26, 6, 8, 3,
                GuiDraw.fade(dragging ? ClickGuiPalette.TEXT : ClickGuiPalette.PRIMARY, opacity));
    }

    private void renderMode(DrawContext context, TextRenderer renderer, String value, float opacity) {
        label(context, renderer, setting.label(), x + 10, y + 6, width - 20, ClickGuiPalette.MUTED, opacity);
        GuiDraw.rounded(context, x + 8, y + 20, width - 16, 22, 4,
                GuiDraw.fade(ClickGuiPalette.TRACK, opacity));
        label(context, renderer, value, x + 16, y + 27, width - 46, ClickGuiPalette.TEXT, opacity);
        GuiDraw.text(context, renderer, ">", x + width - 23, y + 27,
                GuiDraw.fade(ClickGuiPalette.PRIMARY, opacity));
    }

    private void renderColor(DrawContext context, TextRenderer renderer, ColorSetting color, float opacity) {
        label(context, renderer, setting.label(), x + 10, y + 7, width - 20, ClickGuiPalette.TEXT, opacity);
        String hex = String.format(Locale.ROOT, "#%06X", color.get() & 0xFFFFFF);
        GuiDraw.rounded(context, x + 10, y + 22, 10, 10, 3, GuiDraw.fade(color.get(), opacity));
        label(context, renderer, hex, x + 27, y + 23, width - 37, ClickGuiPalette.MUTED, opacity);
        int size = swatchSize();
        for (int index = 0; index < SWATCHES.length; index++) {
            int left = swatchLeft(index);
            boolean selected = (color.get() & 0xFFFFFF) == (SWATCHES[index] & 0xFFFFFF);
            if (selected) {
                GuiDraw.rounded(context, left - 1, y + 40, size + 2, 16, 3,
                        GuiDraw.fade(ClickGuiPalette.TEXT, opacity));
            }
            GuiDraw.rounded(context, left, y + 41, size, 14, 3,
                    GuiDraw.fade(SWATCHES[index], opacity));
        }
    }

    private void renderString(DrawContext context, TextRenderer renderer, StringSetting string, float opacity) {
        label(context, renderer, setting.label(), x + 10, y + 6, width - 20, ClickGuiPalette.MUTED, opacity);
        GuiDraw.rounded(context, x + 8, y + 20, width - 16, 23, 4,
                GuiDraw.fade(editing ? ClickGuiPalette.PRIMARY : ClickGuiPalette.TRACK, opacity));
        GuiDraw.rounded(context, x + 9, y + 21, width - 18, 21, 3,
                GuiDraw.fade(ClickGuiPalette.PANEL, opacity));
        String value = string.get();
        String visible = editing ? tail(renderer, value, width - 38) : GuiDraw.fit(renderer, value, width - 36);
        boolean placeholder = visible.isEmpty() && !editing;
        label(context, renderer, placeholder ? "Click to type..." : visible, x + 16, y + 28,
                width - 32, placeholder ? ClickGuiPalette.MUTED : ClickGuiPalette.TEXT, opacity);
        if (editing && (System.nanoTime() / 500_000_000L) % 2L == 0L) {
            int cursorX = Math.min(x + width - 14, x + 17 + renderer.getWidth(visible));
            context.fill(cursorX, y + 26, cursorX + 1, y + 37, GuiDraw.fade(ClickGuiPalette.PRIMARY, opacity));
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || !laidOut || !GuiDraw.inside(mouseX, mouseY, x, y, width,
                compact ? height() : height() - 4)) return false;
        if (button != 0 && button != 1) return false;
        editing = false;
        if (compact) {
            if (setting instanceof BooleanSetting bool && button == 0) bool.toggle();
            else if ((setting instanceof NumberSetting || setting instanceof IntegerSetting) && button == 0) {
                dragging = true;
                updateSlider(mouseX);
            } else if (setting instanceof ColorSetting color && button == 0) {
                int current = -1;
                for (int i = 0; i < SWATCHES.length; i++) {
                    if ((color.get() & 0xFFFFFF) == (SWATCHES[i] & 0xFFFFFF)) { current = i; break; }
                }
                color.set(SWATCHES[(current + 1) % SWATCHES.length]);
            }
            return true;
        }
        if (setting instanceof BooleanSetting bool && button == 0) {
            bool.toggle();
        } else if ((setting instanceof NumberSetting || setting instanceof IntegerSetting) && button == 0) {
            if (mouseY >= y + 20) {
                dragging = true;
                updateSlider(mouseX);
            }
        } else if (setting instanceof ColorSetting color && button == 0) {
            for (int index = 0; index < SWATCHES.length; index++) {
                if (GuiDraw.inside(mouseX, mouseY, swatchLeft(index) - 1, y + 39, swatchSize() + 2, 18)) {
                    color.set(SWATCHES[index]);
                    break;
                }
            }
        } else if (setting instanceof ModeSetting mode) {
            int count = button == 1 ? mode.modes().size() - 1 : 1;
            for (int i = 0; i < count; i++) mode.cycle();
        } else if (setting instanceof EnumSetting mode) {
            int count = button == 1 ? mode.values().size() - 1 : 1;
            for (int i = 0; i < count; i++) mode.cycle();
        } else if (setting instanceof StringSetting && button == 0) {
            editing = true;
        }
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!isVisible() || !dragging) return false;
        updateSlider(mouseX);
        return true;
    }

    public void mouseReleased() {
        dragging = false;
    }

    public boolean keyPressed(KeyInput input) {
        if (!isVisible() || !editing || !(setting instanceof StringSetting string)) return false;
        if (input.key() == GLFW.GLFW_KEY_ESCAPE || input.key() == GLFW.GLFW_KEY_ENTER
                || input.key() == GLFW.GLFW_KEY_KP_ENTER || input.key() == GLFW.GLFW_KEY_TAB) {
            editing = false;
        } else if (input.key() == GLFW.GLFW_KEY_BACKSPACE && !string.get().isEmpty()) {
            String value = string.get();
            string.set(value.substring(0, value.offsetByCodePoints(value.length(), -1)));
        } else if (input.key() == GLFW.GLFW_KEY_DELETE) {
            string.set("");
        }
        // Keep typed keys from triggering a module binding while this input owns focus.
        return true;
    }

    public boolean charTyped(CharInput input) {
        if (!isVisible() || !editing || !(setting instanceof StringSetting string)) return false;
        if (input.isValidChar()) {
            string.append(input.asString());
            // StringSetting limits UTF-16 length; avoid leaving half a surrogate at its limit.
            String value = string.get();
            if (!value.isEmpty() && Character.isHighSurrogate(value.charAt(value.length() - 1))) {
                string.set(value.substring(0, value.length() - 1));
            }
        }
        return true;
    }

    public void blur() {
        editing = false;
        dragging = false;
    }

    public boolean isEditing() {
        return isVisible() && editing;
    }

    private void updateSlider(double mouseX) {
        double amount = compact
                ? Math.max(0.0, Math.min(1.0, (mouseX - x - 7) / Math.max(1, width - 14)))
                : Math.max(0.0, Math.min(1.0, (mouseX - x - 12) / Math.max(1, width - 24)));
        if (setting instanceof NumberSetting number) {
            number.set(number.min() + (number.max() - number.min()) * amount);
        } else if (setting instanceof IntegerSetting integer) {
            integer.set((int) Math.round(integer.min() + ((double) integer.max() - integer.min()) * amount));
        }
    }

    private int swatchSize() {
        return Math.max(1, Math.min(16, (width - 20 - (SWATCHES.length - 1) * 4) / SWATCHES.length));
    }

    private int swatchLeft(int index) {
        return x + 10 + index * (swatchSize() + 4);
    }

    private static double fraction(double value, double min, double max) {
        return max <= min ? 0.0 : Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
    }

    private static String format(double value) {
        return Double.isFinite(value)
                ? BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                : "0";
    }

    private static String tail(TextRenderer renderer, String value, int maxWidth) {
        int start = 0;
        while (start < value.length() && renderer.getWidth(value.substring(start)) > Math.max(0, maxWidth)) {
            start = value.offsetByCodePoints(start, 1);
        }
        return value.substring(start);
    }

    private static void label(DrawContext context, TextRenderer renderer, String text, int x, int y,
                              int maxWidth, int color, float opacity) {
        GuiDraw.text(context, renderer, GuiDraw.fit(renderer, text, Math.max(0, maxWidth)),
                x, y, GuiDraw.fade(color, opacity));
    }
}
