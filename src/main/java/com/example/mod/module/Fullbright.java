package com.example.mod.module;

import com.example.mod.setting.ModeSetting;
import com.example.mod.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;

/** Client-side fullbright. Gamma mode uses the vanilla gamma option; night-vision
 * mode is supplied by GameRendererMixin and never adds a potion effect to the HUD. */
public final class Fullbright extends Module {
    private final ModeSetting mode = addSetting(
            new ModeSetting("Mode", "Gamma", "Gamma", "NightVision"));
    // Minecraft's gamma option is bounded to 0..1; 1.0 is its maximum brightness.
    private final NumberSetting gammaLevel = addSetting(
            new NumberSetting("Gamma level", 1.0, 0.0, 1.0, 0.05));

    private Double originalGamma;

    public Fullbright() {
        super("Fullbright", false, Category.MISC);
        mode.describe("Gamma uses Minecraft's brightness setting; NightVision brightens the view without a potion icon.");
        gammaLevel.describe("Brightness used in Gamma mode, from 0 to Minecraft's maximum of 1. Has no effect in NightVision mode.");
    }

    public ModeSetting mode() { return mode; }
    public NumberSetting gammaLevel() { return gammaLevel; }
    public boolean usesNightVision() { return isEnabled() && "NightVision".equals(mode.get()); }

    @Override
    protected void onEnable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null) {
            originalGamma = client.options.getGamma().getValue();
            applyMode(client);
        }
    }

    /** Re-applies settings after a GUI mode/slider change while preserving the user's gamma. */
    public void tick(MinecraftClient client) {
        if (client.world == null) { restoreGamma(); return; }
        if (isEnabled() && client.options != null) {
            if (originalGamma == null) originalGamma = client.options.getGamma().getValue();
            applyMode(client);
        }
    }

    @Override
    protected void onDisable() {
        restoreGamma();
    }
    @Override public void onWorldLeave() { restoreGamma(); }

    private void applyMode(MinecraftClient client) {
        SimpleOption<Double> gamma = client.options.getGamma();
        if ("Gamma".equals(mode.get())) {
            if (Double.compare(gamma.getValue(), gammaLevel.get()) != 0) gamma.setValue(gammaLevel.get());
        } else {
            restoreGamma();
        }
    }

    private void restoreGamma() {
        if (originalGamma == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null) client.options.getGamma().setValue(originalGamma);
        originalGamma = null;
    }
}
