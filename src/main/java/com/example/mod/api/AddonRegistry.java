package com.example.mod.api;

import com.example.mod.module.Module;
import com.example.mod.module.ModuleManager;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class AddonRegistry {
    private final ModuleManager modules;
    private final List<AddonModule> addons = new ArrayList<>();
    private final List<AddonCategory> categories = new ArrayList<>();
    private final List<AddonHudElement> hudElements = new ArrayList<>();
    private final List<Consumer<CommandDispatcher<FabricClientCommandSource>>> commands = new ArrayList<>();
    private final List<Consumer<MinecraftClient>> ticks = new ArrayList<>();
    private final List<Consumer<MinecraftClient>> joins = new ArrayList<>();
    private final List<Consumer<MinecraftClient>> disconnects = new ArrayList<>();
    private final List<Consumer<WorldRenderContext>> worldRenders = new ArrayList<>();
    private final List<Consumer<HudRender>> hudRenders = new ArrayList<>();
    private boolean closed;

    public record HudRender(DrawContext context, RenderTickCounter ticks) { }

    public AddonRegistry(ModuleManager modules) {
        this.modules = Objects.requireNonNull(modules);
    }

    public void register(AddonModule module) {
        ensureOpen();
        Objects.requireNonNull(module, "module");
        if (module.name().isBlank()) throw new IllegalArgumentException("Addon module name is blank");
        if (module.addonCategory() != null && categories.stream().noneMatch(c -> c.id().equals(module.addonCategory())))
            throw new IllegalArgumentException("Category must be registered before its modules: " + module.addonCategory());
        for (Module existing : modules.all()) {
            if (existing.name().equalsIgnoreCase(module.name()))
                throw new IllegalArgumentException("Duplicate module name: " + module.name());
        }
        modules.add(module);
        addons.add(module);
    }

    public void registerCategory(AddonCategory category) {
        ensureOpen();
        Objects.requireNonNull(category, "category");
        if (categories.stream().anyMatch(c -> c.id().equals(category.id())))
            throw new IllegalArgumentException("Duplicate addon category: " + category.id());
        categories.add(category);
    }

    public void registerHud(AddonHudElement element) {
        ensureOpen();
        Objects.requireNonNull(element, "element");
        Identifier id = Objects.requireNonNull(element.id(), "HUD id");
        if (hudElements.stream().anyMatch(existing -> existing.id().equals(id)))
            throw new IllegalArgumentException("Duplicate HUD id: " + id);
        hudElements.add(element);
    }

    public void registerCommand(Consumer<CommandDispatcher<FabricClientCommandSource>> command) {
        ensureOpen();
        commands.add(Objects.requireNonNull(command));
    }

    public void onTick(Consumer<MinecraftClient> listener) { ensureOpen(); ticks.add(Objects.requireNonNull(listener)); }
    public void onJoin(Consumer<MinecraftClient> listener) { ensureOpen(); joins.add(Objects.requireNonNull(listener)); }
    public void onDisconnect(Consumer<MinecraftClient> listener) { ensureOpen(); disconnects.add(Objects.requireNonNull(listener)); }
    public void onWorldRender(Consumer<WorldRenderContext> listener) { ensureOpen(); worldRenders.add(Objects.requireNonNull(listener)); }
    public void onHudRender(Consumer<HudRender> listener) { ensureOpen(); hudRenders.add(Objects.requireNonNull(listener)); }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Addon registration has ended");
    }

    public List<AddonModule> modules() {
        return List.copyOf(addons);
    }

    public List<AddonCategory> categories() { return List.copyOf(categories); }
    public List<AddonHudElement> hudElements() { return List.copyOf(hudElements); }
    public List<Consumer<CommandDispatcher<FabricClientCommandSource>>> commands() { return List.copyOf(commands); }
    public List<Consumer<MinecraftClient>> ticks() { return List.copyOf(ticks); }
    public List<Consumer<MinecraftClient>> joins() { return List.copyOf(joins); }
    public List<Consumer<MinecraftClient>> disconnects() { return List.copyOf(disconnects); }
    public List<Consumer<WorldRenderContext>> worldRenders() { return List.copyOf(worldRenders); }
    public List<Consumer<HudRender>> hudRenders() { return List.copyOf(hudRenders); }

    public void close() {
        closed = true;
    }
}
