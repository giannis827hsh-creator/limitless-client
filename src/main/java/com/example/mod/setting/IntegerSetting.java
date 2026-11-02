package com.example.mod.setting;

/** Bounded integer slider setting for whole-number thresholds. */
public final class IntegerSetting extends Setting {
    private final int min;
    private final int max;
    private int value;

    public IntegerSetting(String name, int initialValue, int min, int max) {
        super(name);
        if (max < min) throw new IllegalArgumentException("Invalid integer setting bounds");
        this.min = min;
        this.max = max;
        set(initialValue);
    }

    public int get() { return value; }
    public int min() { return min; }
    public int max() { return max; }
    public void set(int value) { this.value = Math.max(min, Math.min(max, value)); }
}
