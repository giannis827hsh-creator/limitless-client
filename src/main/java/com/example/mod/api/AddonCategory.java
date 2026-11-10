package com.example.mod.api;

public record AddonCategory(String id, String title) {
    public AddonCategory {
        if (id == null || !id.matches("[a-z0-9_.-]+"))
            throw new IllegalArgumentException("Category id must use lowercase letters, digits, dots, dashes or underscores");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Category title is blank");
    }
}
