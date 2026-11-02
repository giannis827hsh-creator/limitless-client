package com.example.mod.setting;

import java.util.List;

/** Cyclic string mode option used by the Click GUI. */
public final class ModeSetting extends Setting {
    private final List<String> modes;
    private int index;

    public ModeSetting(String name, String initialMode, String... modes) {
        super(name);
        if (modes.length == 0) throw new IllegalArgumentException("A mode setting needs at least one mode");
        this.modes = List.of(modes);
        this.index = this.modes.indexOf(initialMode);
        if (index < 0) throw new IllegalArgumentException("Unknown initial mode: " + initialMode);
    }

    public String get() { return modes.get(index); }
    public List<String> modes() { return modes; }

    public void cycle() { index = (index + 1) % modes.size(); }
}
