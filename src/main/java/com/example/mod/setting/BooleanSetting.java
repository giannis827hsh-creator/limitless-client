package com.example.mod.setting;

/** Mutable on/off option. */
public final class BooleanSetting extends Setting {
    private boolean value;

    public BooleanSetting(String name, boolean initialValue) {
        super(name);
        value = initialValue;
    }

    public boolean get() { return value; }
    public void set(boolean value) { this.value = value; }
    public void toggle() { value = !value; }
}
