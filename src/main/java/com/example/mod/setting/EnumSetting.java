package com.example.mod.setting;

import java.util.List;

/** Cyclic enum-like option used by the Click GUI. */
public final class EnumSetting extends Setting {
    private final List<String> values;
    private int index;

    public EnumSetting(String name, String initialValue, String... values) {
        super(name);
        if (values.length == 0) throw new IllegalArgumentException("An enum setting needs at least one value");
        this.values = List.of(values);
        this.index = this.values.indexOf(initialValue);
        if (index < 0) throw new IllegalArgumentException("Unknown initial value: " + initialValue);
    }

    public String get() { return values.get(index); }
    public List<String> values() { return values; }
    public void cycle() { index = (index + 1) % values.size(); }
}
