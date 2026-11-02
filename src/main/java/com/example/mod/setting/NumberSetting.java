package com.example.mod.setting;

/** Bounded numeric option, suitable for GUI sliders. */
public final class NumberSetting extends Setting {
    private final double min;
    private final double max;
    private final double step;
    private double value;

    public NumberSetting(String name, double initialValue, double min, double max, double step) {
        super(name);
        if (max <= min || step <= 0) throw new IllegalArgumentException("Invalid number setting bounds");
        this.min = min;
        this.max = max;
        this.step = step;
        set(initialValue);
    }

    public double get() { return value; }
    public double min() { return min; }
    public double max() { return max; }
    public void set(double value) {
        double bounded = Math.max(min, Math.min(max, value));
        this.value = Math.max(min, Math.min(max, min + Math.round((bounded - min) / step) * step));
    }
}
