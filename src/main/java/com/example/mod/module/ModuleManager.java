package com.example.mod.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Owns client module instances. */
public final class ModuleManager {
    private final List<Module> modules = new ArrayList<>();
    public void add(Module module) { modules.add(module); }
    public List<Module> all() { return Collections.unmodifiableList(modules); }
    public List<Module> enabled() { return modules.stream().filter(Module::isEnabled).toList(); }
    public void disableAll() { for (Module module : modules) module.setEnabled(false); }
    public void clearWorldState() { for (Module module : modules) module.onWorldLeave(); }
    public <T extends Module> T get(Class<T> type) {
        return modules.stream().filter(type::isInstance).map(type::cast).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No module registered: " + type.getName()));
    }
}
