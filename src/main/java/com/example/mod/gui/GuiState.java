package com.example.mod.gui;

import com.example.mod.module.Module;
import com.example.mod.module.ModuleManager;
import com.example.mod.setting.Setting;
import com.example.mod.setting.StringSetting;
import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.IntegerSetting;
import com.example.mod.setting.NumberSetting;
import com.example.mod.setting.ColorSetting;
import com.example.mod.setting.EnumSetting;
import com.example.mod.setting.ModeSetting;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Saves GUI placement, module bindings, and editable settings outside render/tick callbacks. */
public final class GuiState {
    private static final Logger LOGGER = LoggerFactory.getLogger("limitless/GUI");
    private static final Set<String> CATEGORIES = Set.of("COMBAT", "RENDER", "MOVEMENT", "MISC", "BASEFINDER", "HUD", "DONUT");
    public static final GuiState INSTANCE = new GuiState();

    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("limitless-gui.properties");
    private final Properties properties = new Properties();
    private final Properties lastSaved = new Properties();
    private double xFraction = 0.5;
    private double yFraction = 0.5;
    private String category = "RENDER";

    private GuiState() {
        Path source = Files.isRegularFile(file) ? file :
                FabricLoader.getInstance().getConfigDir().resolve("nova-gui.properties");
        if (!Files.isRegularFile(source)) return;
        try (InputStream input = Files.newInputStream(source)) {
            properties.load(input);
            lastSaved.putAll(properties);
            xFraction = readFraction("window.x", 0.5);
            yFraction = readFraction("window.y", 0.5);
            String savedCategory = properties.getProperty("category", "RENDER");
            if (CATEGORIES.contains(savedCategory)) category = savedCategory;
        } catch (IOException | IllegalArgumentException exception) {
            properties.clear();
            LOGGER.warn("Unable to read limitless GUI settings; using defaults", exception);
        }
    }

    public double xFraction() { return xFraction; }
    public double yFraction() { return yFraction; }
    public String category() { return category; }

    public double panelX(String panel, double fallback, double travel) {
        return panelCoordinate(panel, "x", fallback, travel);
    }
    public double panelY(String panel, double fallback, double travel) {
        return panelCoordinate(panel, "y", fallback, travel);
    }
    private double panelCoordinate(String panel, String axis, double fallback, double travel) {
        // Start the six-panel layout with separate defaults, preserving the earlier layout in the file.
        String key = "compact.panel." + panel + "." + axis;
        if (!properties.containsKey(key)) return fallback;
        return 8 + readFraction(key, 0.5) * Math.max(0, travel);
    }
    public void panelPosition(String panel, double x, double y, double travelX, double travelY) {
        properties.setProperty("compact.panel." + panel + ".x", Double.toString(fraction((x - 8) / Math.max(1, travelX), 0.5)));
        properties.setProperty("compact.panel." + panel + ".y", Double.toString(fraction((y - 8) / Math.max(1, travelY), 0.5)));
    }

    /** Fractions of available travel: x/(screenWidth-windowWidth), likewise for y. */
    public void position(double x, double y) {
        xFraction = fraction(x, 0.5);
        yFraction = fraction(y, 0.5);
    }

    public void category(String selectedCategory) {
        if (selectedCategory != null && CATEGORIES.contains(selectedCategory)) category = selectedCategory;
    }

    /** Call after registering all modules. Unknown modules/config keys are preserved. */
    public void loadModules(ModuleManager modules) {
        for (Module module : modules.all()) {
            String value = properties.getProperty("bind." + module.name());
            if (value == null && module.name().equals("Anti Vanish")) value = properties.getProperty("bind.Staff Detector");
            if (value != null) {
                try {
                    int key = Integer.parseInt(value);
                    if (key != GLFW.GLFW_KEY_UNKNOWN && !Module.isBindableKey(key)) {
                        LOGGER.warn("Ignoring invalid key binding {} for {}", value, module.name());
                    } else {
                        module.setKeybind(key);
                    }
                } catch (NumberFormatException exception) {
                    LOGGER.warn("Ignoring malformed key binding for {}: {}", module.name(), value);
                }
            }
            for (Setting setting : module.settings()) {
                String saved = properties.getProperty("setting." + module.name() + "." + setting.name());
                if (saved == null) continue;
                try {
                    if (setting instanceof StringSetting string) string.set(saved);
                    else if (setting instanceof BooleanSetting bool) {
                        if (!saved.equals("true") && !saved.equals("false")) throw new IllegalArgumentException("Invalid boolean");
                        bool.set(Boolean.parseBoolean(saved));
                    } else if (setting instanceof IntegerSetting integer) integer.set(Integer.parseInt(saved));
                    else if (setting instanceof NumberSetting number) {
                        double valueNumber = Double.parseDouble(saved);
                        if (!Double.isFinite(valueNumber)) throw new IllegalArgumentException("Non-finite number");
                        number.set(valueNumber);
                    } else if (setting instanceof ColorSetting color) color.set(Integer.parseUnsignedInt(saved, 16));
                    else if (setting instanceof EnumSetting mode && mode.values().contains(saved)) {
                        while (!mode.get().equals(saved)) mode.cycle();
                    } else if (setting instanceof ModeSetting mode && mode.modes().contains(saved)) {
                        while (!mode.get().equals(saved)) mode.cycle();
                    }
                } catch (IllegalArgumentException exception) {
                    LOGGER.warn("Ignoring invalid saved setting {} for {}", setting.name(), module.name());
                }
            }
        }
    }

    /** Call on drag release, binding changes, or screen removal to persist current state. */
    public void saveModules(ModuleManager modules) {
        properties.setProperty("window.x", Double.toString(xFraction));
        properties.setProperty("window.y", Double.toString(yFraction));
        properties.setProperty("category", category);
        for (Module module : modules.all()) {
            properties.setProperty("bind." + module.name(), Integer.toString(module.keybind()));
            for (Setting setting : module.settings()) {
                String saved = null;
                if (setting instanceof StringSetting string) saved = string.get();
                else if (setting instanceof BooleanSetting bool) saved = Boolean.toString(bool.get());
                else if (setting instanceof IntegerSetting integer) saved = Integer.toString(integer.get());
                else if (setting instanceof NumberSetting number) saved = Double.toString(number.get());
                else if (setting instanceof ColorSetting color) saved = Integer.toHexString(color.get());
                else if (setting instanceof EnumSetting mode) saved = mode.get();
                else if (setting instanceof ModeSetting mode) saved = mode.get();
                if (saved != null) properties.setProperty("setting." + module.name() + "." + setting.name(), saved);
            }
        }
        // Mouse releases and disconnect/shutdown can repeat an identical save.
        if (properties.equals(lastSaved) && Files.isRegularFile(file)) return;
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "limitless-gui-", ".tmp");
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "limitless Click GUI placement, module keyboard bindings, and settings");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            lastSaved.clear();
            lastSaved.putAll(properties);
        } catch (IOException | SecurityException exception) {
            LOGGER.warn("Unable to save limitless GUI settings", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException exception) {
                    LOGGER.debug("Unable to remove temporary GUI settings file", exception);
                }
            }
        }
    }

    private double readFraction(String key, double fallback) {
        try {
            return fraction(Double.parseDouble(properties.getProperty(key, Double.toString(fallback))), fallback);
        } catch (NumberFormatException exception) {
            LOGGER.warn("Ignoring invalid GUI position {}", key);
            return fallback;
        }
    }

    private static double fraction(double value, double fallback) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : fallback;
    }
}
