package com.example.mod.module;

import com.example.mod.setting.StringSetting;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Replaces client-side player skins with a configurable account's textures. */
public final class SkinOverride extends Module {
    private static final long EDIT_DELAY_MS = 500L;
    private final StringSetting skinUsername = addSetting(new StringSetting("Skin username", "archivePedro", 16));
    private volatile SkinTextures resolvedSkin;
    private String activeName = "";
    private String loadingName = "";
    private long nameChangedAt;
    private int lookupGeneration;

    public SkinOverride() {
        super("Skin Override", false, Category.MISC);
        skinUsername.describe("Enter a Minecraft Java username. Its skin is shown on all players on your screen, including you.");
    }

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable() {
        reset();
    }

    public void tick(MinecraftClient client) {
        if (!isEnabled() || client.getNetworkHandler() == null) return;
        String name = skinUsername.get().trim();
        if (!activeName.equalsIgnoreCase(name)) {
            activeName = name;
            loadingName = "";
            resolvedSkin = null;
            nameChangedAt = System.currentTimeMillis();
            lookupGeneration++;
        }
        if (!validName(name) || loadingName.equalsIgnoreCase(name)
                || System.currentTimeMillis() - nameChangedAt < EDIT_DELAY_MS) return;
        loadingName = name;
        resolveSkin(client, name, lookupGeneration);
    }

    /** Called on the render thread by the player-skin mixin. */
    public SkinTextures replacement(MinecraftClient client) {
        if (!isEnabled()) return null;
        String name = skinUsername.get().trim();
        if (!validName(name) || !activeName.equalsIgnoreCase(name)) return null;
        SkinTextures skin = resolvedSkin;
        if (skin != null) return skin;

        // A player already on this server can supply their skin immediately.
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) return null;
        PlayerListEntry entry = handler.getPlayerListEntry(name);
        return entry == null ? null : entry.getSkinTextures();
    }

    private void resolveSkin(MinecraftClient client, String name, int generation) {
        CompletableFuture.supplyAsync(() -> client.getApiServices().profileResolver().getProfileByName(name))
                .thenCompose(profile -> profile.map(found -> client.getSkinProvider().fetchSkinTextures(found)
                        .thenApply(textures -> Optional.of(textures.orElseGet(
                                () -> DefaultSkinHelper.getSkinTextures(found)))))
                        .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())))
                .whenComplete((textures, error) -> client.execute(() -> {
                    if (generation != lookupGeneration || !isEnabled()
                            || !activeName.equalsIgnoreCase(name)) return;
                    if (error != null || textures == null || textures.isEmpty()) {
                        notifyPlayer(client, "Skin Override: couldn't load " + name, Formatting.RED);
                        return;
                    }
                    resolvedSkin = textures.get();
                    notifyPlayer(client, "Skin Override: using " + name, Formatting.GREEN);
                }));
    }

    private static boolean validName(String name) {
        return !name.isEmpty() && name.length() <= 16 && name.matches("[A-Za-z0-9_]+");
    }

    private static void notifyPlayer(MinecraftClient client, String message, Formatting color) {
        if (client.player != null) client.player.sendMessage(Text.literal(message).formatted(color), false);
    }

    private void reset() {
        lookupGeneration++;
        activeName = "";
        loadingName = "";
        resolvedSkin = null;
        nameChangedAt = 0L;
    }
}
