package com.example.mod.setting;

/** Short editable text option used for registry-name search filters. */
public final class StringSetting extends Setting {
    private final int maxLength;
    private String value;

    public StringSetting(String name, String initialValue, int maxLength) {
        super(name);
        this.maxLength = Math.max(1, maxLength);
        set(initialValue);
    }

    public String get() { return value; }

    public void set(String value) {
        this.value = value == null ? "" : value.substring(0, Math.min(value.length(), maxLength));
    }

    public void append(char character) {
        if (!Character.isISOControl(character) && value.length() < maxLength) value += character;
    }

    public void append(String text) {
        if (text == null || text.isEmpty()) return;
        for (int i = 0; i < text.length() && value.length() < maxLength; i++) append(text.charAt(i));
    }

    public void backspace() {
        if (!value.isEmpty()) value = value.substring(0, value.length() - 1);
    }
}
