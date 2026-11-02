package com.example.mod.setting;

/** Base class for a module option displayed by the Click GUI. */
public abstract class Setting {
    private final String name;
    private String label;
    private String description = "";
    private java.util.function.BooleanSupplier visibility = () -> true;

    protected Setting(String name) { this.name = name; }

    public final String name() { return name; }

    /** Display copy is independent of the saved configuration key. */
    public final String label() { return label == null ? name : label; }
    public final String description() { return description; }
    public final boolean isVisible() { return visibility.getAsBoolean(); }
    public final void describe(String description) {
        this.description = java.util.Objects.requireNonNull(description);
    }
    public final void describe(String label, String description) {
        this.label = java.util.Objects.requireNonNull(label);
        this.description = java.util.Objects.requireNonNull(description);
    }
    public final void visibleWhen(java.util.function.BooleanSupplier visibility) {
        this.visibility = java.util.Objects.requireNonNull(visibility);
    }
}
