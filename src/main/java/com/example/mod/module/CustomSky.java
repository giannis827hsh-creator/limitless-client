package com.example.mod.module;

import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.ModeSetting;
import com.example.mod.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.dimension.DimensionType;

public final class CustomSky extends Module {
    private final ModeSetting style = addSetting(new ModeSetting("Style", "Nebula",
            "Nebula", "Violet Dusk", "Aurora", "Crimson", "Midnight", "Custom"));
    private final NumberSetting strength = addSetting(new NumberSetting("Strength", 0.8, 0.0, 1.0, 0.05));
    private final BooleanSetting colorShift = addSetting(new BooleanSetting("Slow color shift", true));
    private final BooleanSetting dawnGlow = addSetting(new BooleanSetting("Dawn glow", true));
    private final NumberSetting nightDepth = addSetting(new NumberSetting("Night depth", 0.85, 0.0, 1.0, 0.05));
    private final BooleanSetting stars = addSetting(new BooleanSetting("Stars", true));
    private final NumberSetting starBoost = addSetting(new NumberSetting("Star brightness", 1.5, 0.5, 3.0, 0.1));
    private final BooleanSetting skyEffects = addSetting(new BooleanSetting("Sky effects", true));
    private final BooleanSetting shootingStars = addSetting(new BooleanSetting("Shooting stars", true));
    private final NumberSetting shootingFrequency = addSetting(new NumberSetting("Shooting star frequency", 3, 1, 5, 1));
    private final NumberSetting hue = addSetting(new NumberSetting("Custom hue", 270, 0, 360, 1));
    private final NumberSetting saturation = addSetting(new NumberSetting("Custom saturation", 0.7, 0, 1, 0.05));
    private final NumberSetting brightness = addSetting(new NumberSetting("Custom brightness", 0.45, 0.05, 1, 0.05));
    private String activeStyle, previousStyle;
    private Palette previousPalette, activePalette;
    private long transitionStart;
    private float nightAmount;

    public CustomSky() {
        super("Custom Sky", false, Category.VISUALS);
        style.describe("Choose an overworld sky palette or tune your own color.");
        strength.describe("Blend between Minecraft's sky and the chosen palette.");
        colorShift.describe("Gently move between colors in the current palette.");
        dawnGlow.describe("Tint the horizon during sunrise and sunset.");
        nightDepth.describe("Darken the palette at night without changing daytime colors.");
        stars.describe("Show or hide the vanilla night stars.");
        starBoost.describe("Adjust the visibility of stars at night.");
        starBoost.visibleWhen(stars::get);
        skyEffects.describe("Show aurora ribbons or nebula clouds for their matching styles.");
        shootingStars.describe("Occasional streaks across the night sky.");
        shootingFrequency.describe("How often a shooting star can appear.");
        shootingFrequency.visibleWhen(shootingStars::get);
        hue.visibleWhen(() -> "Custom".equals(style.get()));
        saturation.visibleWhen(() -> "Custom".equals(style.get()));
        brightness.visibleWhen(() -> "Custom".equals(style.get()));
    }

    public void apply(SkyRenderState state) {
        if (!isEnabled() || state.skybox != DimensionType.Skybox.OVERWORLD) return;
        double amount = strength.get();
        if (amount <= 0) return;

        Palette palette = displayedPalette();
        nightAmount = (float) smoothstep(0.02, 0.2, state.starBrightness);
        double daylight = 1.0 - nightAmount;
        int nightColor = blend(palette.night(), palette.day(), 1.0 - nightDepth.get());
        int color = blend(nightColor, palette.day(), daylight);
        if (colorShift.get()) {
            double pulse = (Math.sin(System.currentTimeMillis() / 14000.0) + 1.0) * 0.12;
            color = blend(color, palette.accent(), pulse);
        }
        state.skyColor = (state.skyColor & 0xFF000000) | blend(state.skyColor, color, amount);
        if (dawnGlow.get() && state.sunriseAndSunsetColor != 0) {
            int originalAlpha = state.sunriseAndSunsetColor & 0xFF000000;
            state.sunriseAndSunsetColor = originalAlpha
                    | blend(state.sunriseAndSunsetColor, palette.dawn(), amount * 0.75);
        }
        state.starBrightness = stars.get()
                ? (float) Math.min(1.0, state.starBrightness * (1.0 + (starBoost.get() - 1.0) * amount))
                : 0.0f;
    }

    @Override protected void onDisable() {
        activeStyle = null;
        previousStyle = null;
        activePalette = null;
        previousPalette = null;
        nightAmount = 0;
    }

    public int previewColor(boolean night) {
        Palette selected = palette();
        return 0xFF000000 | (night ? blend(selected.night(), selected.day(), 1.0 - nightDepth.get()) : selected.day());
    }

    public int previewAccent() { return 0xFF000000 | palette().accent(); }

    private Palette displayedPalette() {
        Palette selected = palette();
        String selectedStyle = style.get();
        long now = System.nanoTime();
        if (activePalette == null) {
            activeStyle = selectedStyle;
            previousStyle = selectedStyle;
            activePalette = selected;
            previousPalette = selected;
        } else if (!selectedStyle.equals(activeStyle)) {
            previousPalette = transitionPalette(now);
            previousStyle = activeStyle;
            activePalette = selected;
            activeStyle = selectedStyle;
            transitionStart = now;
        } else if ("Custom".equals(selectedStyle)) {
            activePalette = selected;
        }
        return transitionPalette(now);
    }

    private Palette transitionPalette(long now) {
        double progress = transitionProgress(now);
        return new Palette(blend(previousPalette.day(), activePalette.day(), progress),
                blend(previousPalette.night(), activePalette.night(), progress),
                blend(previousPalette.dawn(), activePalette.dawn(), progress),
                blend(previousPalette.accent(), activePalette.accent(), progress));
    }

    private double transitionProgress(long now) {
        double t = Math.min(1.0, Math.max(0.0, (now - transitionStart) / 900_000_000.0));
        return t * t * (3.0 - 2.0 * t);
    }

    public void renderWorld(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || nightAmount < 0.1f || client.world == null
                || client.world.getDimension().skybox() != DimensionType.Skybox.OVERWORLD) return;
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) return;
        double radius = Math.max(48, Math.min(192, client.options.getViewDistance().getValue() * 16 - 24));
        Vec3d camera = context.worldState().cameraRenderState.pos;
        VertexConsumer quads = consumers.getBuffer(EspRenderLayers.SKY_EFFECTS);
        int accent = displayedPalette().accent();
        double progress = transitionProgress(System.nanoTime());
        matrices.push();
        try {
            matrices.translate(-camera.x, -camera.y, -camera.z);
            MatrixStack.Entry matrix = matrices.peek();
            if (skyEffects.get()) {
                drawEffect(quads, matrix, camera, radius, previousStyle, accent, 1.0 - progress);
                drawEffect(quads, matrix, camera, radius, activeStyle, accent, progress);
            }
            if (shootingStars.get()) drawShootingStar(quads, matrix, camera, radius);
        } finally {
            matrices.pop();
        }
    }

    private void drawEffect(VertexConsumer quads, MatrixStack.Entry matrix, Vec3d camera,
                            double radius, String effect, int accent, double opacity) {
        if (opacity <= 0) return;
        if ("Aurora".equals(effect)) drawAurora(quads, matrix, camera, radius, accent, opacity);
        if ("Nebula".equals(effect)) drawNebula(quads, matrix, camera, radius, accent, opacity);
    }

    private void drawAurora(VertexConsumer quads, MatrixStack.Entry matrix, Vec3d camera,
                            double radius, int accent, double opacity) {
        double time = System.currentTimeMillis() / 3100.0;
        for (int ribbon = 0; ribbon < 3; ribbon++) {
            double base = -1.9 + ribbon * 0.2;
            int alpha = (int) (nightAmount * opacity * (35 + ribbon * 8));
            for (int segment = 0; segment < 36; segment++) {
                double a = segment / 36.0, b = (segment + 1) / 36.0;
                double lonA = base + a * 2.6, lonB = base + b * 2.6;
                double bottomA = 0.48 + 0.07 * Math.sin(a * 13 + time + ribbon);
                double bottomB = 0.48 + 0.07 * Math.sin(b * 13 + time + ribbon);
                double topA = bottomA + 0.22 + 0.08 * Math.sin(a * 9 - time * 0.6);
                double topB = bottomB + 0.22 + 0.08 * Math.sin(b * 9 - time * 0.6);
                quad(quads, matrix, camera, point(radius, lonA, bottomA), point(radius, lonB, bottomB),
                        point(radius, lonB, topB), point(radius, lonA, topA),
                        alpha << 24 | accent & 0xFFFFFF);
            }
        }
    }

    private void drawNebula(VertexConsumer quads, MatrixStack.Entry matrix, Vec3d camera,
                            double radius, int accent, double opacity) {
        double time = System.currentTimeMillis() / 16000.0;
        for (int cloud = 0; cloud < 3; cloud++) {
            double center = 0.6 + cloud * 0.32 + 0.025 * Math.sin(time + cloud);
            for (int row = 0; row < 5; row++) for (int col = 0; col < 16; col++) {
                double u0 = col / 16.0, u1 = (col + 1) / 16.0;
                double v0 = row / 5.0, v1 = (row + 1) / 5.0;
                double falloff = Math.sin(Math.PI * (u0 + u1) * 0.5)
                        * Math.sin(Math.PI * (v0 + v1) * 0.5);
                int alpha = (int) (nightAmount * opacity * falloff * (28 + cloud * 4));
                double left = center + (u0 - 0.5) * 1.2, right = center + (u1 - 0.5) * 1.2;
                double low = 0.72 + (v0 - 0.5) * 0.36, high = 0.72 + (v1 - 0.5) * 0.36;
                quad(quads, matrix, camera, point(radius, left, low), point(radius, right, low),
                        point(radius, right, high), point(radius, left, high),
                        alpha << 24 | accent & 0xFFFFFF);
            }
        }
    }

    private void drawShootingStar(VertexConsumer quads, MatrixStack.Entry matrix,
                                  Vec3d camera, double radius) {
        long window = (long) (27 - shootingFrequency.get() * 4) * 1000L;
        long now = System.currentTimeMillis();
        double age = (now % window) / 1000.0;
        if (age > 0.8) return;
        long seed = now / window;
        double longitude = ((seed * 73 % 360) / 180.0) * Math.PI;
        double elevation = 0.45 + (seed * 37 % 35) / 100.0;
        double travel = age * 0.5;
        for (int i = 0; i < 5; i++) {
            double position = travel - i * 0.025;
            int alpha = (int) (nightAmount * (1.0 - age / 0.8) * (120 - i * 22));
            quad(quads, matrix, camera,
                    point(radius, longitude + position, elevation - position * 0.3),
                    point(radius, longitude + position + 0.025, elevation - (position + 0.025) * 0.3),
                    point(radius, longitude + position + 0.025, elevation - (position + 0.025) * 0.3 + 0.003),
                    point(radius, longitude + position, elevation - position * 0.3 + 0.003),
                    alpha << 24 | 0xE8E2FF);
        }
    }

    private static float[] point(double radius, double longitude, double elevation) {
        double horizontal = Math.cos(elevation) * radius;
        return new float[] {(float) (Math.cos(longitude) * horizontal),
                (float) (Math.sin(elevation) * radius),
                (float) (Math.sin(longitude) * horizontal)};
    }

    private static void quad(VertexConsumer consumer, MatrixStack.Entry matrix, Vec3d camera,
                             float[] a, float[] b, float[] c, float[] d, int color) {
        vertex(consumer, matrix, camera, a, color);
        vertex(consumer, matrix, camera, b, color);
        vertex(consumer, matrix, camera, c, color);
        vertex(consumer, matrix, camera, d, color);
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry matrix, Vec3d camera,
                               float[] point, int color) {
        consumer.vertex(matrix, (float) (camera.x + point[0]), (float) (camera.y + point[1]),
                (float) (camera.z + point[2])).color(color);
    }

    private Palette palette() {
        return switch (style.get()) {
            case "Violet Dusk" -> new Palette(0xA788CC, 0x271B45, 0xE89BCD, 0xC29BD9);
            case "Aurora" -> new Palette(0x70A9B8, 0x0C3245, 0x79D5B7, 0x4EBC9E);
            case "Crimson" -> new Palette(0xC47D93, 0x35152F, 0xFFAC91, 0xD667A0);
            case "Midnight" -> new Palette(0x6E82B9, 0x0A1029, 0xAF8AD0, 0x5964A8);
            case "Custom" -> {
                int day = hsv(hue.get() / 360.0, saturation.get(), brightness.get());
                yield new Palette(day, blend(day, 0x080B18, 0.8),
                        hsv((hue.get() + 30.0) / 360.0, saturation.get(), Math.min(1.0, brightness.get() + 0.2)),
                        hsv((hue.get() + 25.0) / 360.0, saturation.get(), brightness.get()));
            }
            default -> new Palette(0x8A73BC, 0x1A1239, 0xD99ADC, 0x6650AD);
        };
    }

    private static double smoothstep(double start, double end, double value) {
        double t = Math.max(0.0, Math.min(1.0, (value - start) / (end - start)));
        return t * t * (3.0 - 2.0 * t);
    }

    private record Palette(int day, int night, int dawn, int accent) { }

    private static int blend(int from, int to, double amount) {
        int red = mix(from >>> 16 & 255, to >>> 16 & 255, amount);
        int green = mix(from >>> 8 & 255, to >>> 8 & 255, amount);
        int blue = mix(from & 255, to & 255, amount);
        return red << 16 | green << 8 | blue;
    }

    private static int mix(int from, int to, double amount) {
        return (int) Math.round(from + (to - from) * amount);
    }

    private static int hsv(double hue, double saturation, double brightness) {
        double sector = (hue % 1.0) * 6.0;
        double chroma = brightness * saturation;
        double x = chroma * (1.0 - Math.abs(sector % 2.0 - 1.0));
        double offset = brightness - chroma;
        double red, green, blue;
        if (sector < 1) { red = chroma; green = x; blue = 0; }
        else if (sector < 2) { red = x; green = chroma; blue = 0; }
        else if (sector < 3) { red = 0; green = chroma; blue = x; }
        else if (sector < 4) { red = 0; green = x; blue = chroma; }
        else if (sector < 5) { red = x; green = 0; blue = chroma; }
        else { red = chroma; green = 0; blue = x; }
        return (int) Math.round((red + offset) * 255) << 16
                | (int) Math.round((green + offset) * 255) << 8
                | (int) Math.round((blue + offset) * 255);
    }
}
