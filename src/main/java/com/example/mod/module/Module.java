package com.example.mod.module;

import com.example.mod.license.LicenseManager;
import com.example.mod.setting.Setting;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Shared state and settings for client modules. */
public abstract class Module {
    public enum Category { COMBAT, VISUALS, MOVEMENT, BASEFINDER, MISC, HUD, DONUT }

    private final String name;
    private final Category category;
    private boolean enabled;
    private int keybind = GLFW.GLFW_KEY_UNKNOWN;
    private final List<Setting> settings = new ArrayList<>();
    protected Module(String name, boolean initiallyEnabled) {
        this(name, initiallyEnabled, Category.VISUALS);
    }
    protected Module(String name, boolean initiallyEnabled, Category category) {
        this.name = name;
        this.enabled = initiallyEnabled;
        this.category = category;
    }
    public final String name() { return name; }
    public final Category category() { return category; }
    public final int keybind() { return keybind; }

    /** Unknown clears the binding; reserved GUI keys and invalid key codes are ignored. */
    public final void setKeybind(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN || isBindableKey(keyCode)) keybind = keyCode;
    }

    public final String keyName() {
        return keybind == GLFW.GLFW_KEY_UNKNOWN ? "None"
                : InputUtil.Type.KEYSYM.createFromCode(keybind).getLocalizedText().getString();
    }

    /** GLFW key codes are sparse; do not pass gaps or mouse codes to keyboard polling. */
    public static boolean isBindableKey(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) return false;
        return keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_APOSTROPHE
                || (keyCode >= GLFW.GLFW_KEY_COMMA && keyCode <= GLFW.GLFW_KEY_9)
                || keyCode == GLFW.GLFW_KEY_SEMICOLON || keyCode == GLFW.GLFW_KEY_EQUAL
                || (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_RIGHT_BRACKET)
                || keyCode == GLFW.GLFW_KEY_GRAVE_ACCENT
                || keyCode == GLFW.GLFW_KEY_WORLD_1 || keyCode == GLFW.GLFW_KEY_WORLD_2
                || (keyCode >= GLFW.GLFW_KEY_ENTER && keyCode <= GLFW.GLFW_KEY_END)
                || (keyCode >= GLFW.GLFW_KEY_CAPS_LOCK && keyCode <= GLFW.GLFW_KEY_PAUSE)
                || (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F25)
                || (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_EQUAL)
                || (keyCode >= GLFW.GLFW_KEY_LEFT_SHIFT && keyCode <= GLFW.GLFW_KEY_MENU);
    }
    protected final <T extends Setting> T addSetting(T setting) {
        settings.add(setting);
        return setting;
    }
    public final List<Setting> settings() { return Collections.unmodifiableList(settings); }
    public final boolean isEnabled() { return enabled; }
    public final void setEnabled(boolean enabled) {
        // All entry points use this gate; disabling must always be possible.
        if (enabled && !LicenseManager.isLicensed()) return;
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) onEnable(); else onDisable();
    }
    public final void toggle() { setEnabled(!enabled); }
    protected void onEnable() {}
    protected void onDisable() {}
    /** Release server/world observations without changing saved settings or the session toggle. */
    public void onWorldLeave() {}
}
