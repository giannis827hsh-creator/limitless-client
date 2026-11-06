package com.example.mod.module;

import com.example.mod.Theme;
import com.example.mod.Brand;
import com.example.mod.gui.GuiDraw;
import com.example.mod.gui.LimitlessClickGUI;
import com.example.mod.gui.BlockPickerScreen;
import com.example.mod.module.hud.ServerLatency;
import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.ColorSetting;
import com.example.mod.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.network.packet.c2s.query.QueryPingC2SPacket;
import net.minecraft.util.Util;

/** Compact segmented brand card with live connection round-trip latency. */
public final class WatermarkHUD extends Module {
    private final BooleanSetting showWatermark = addSetting(new BooleanSetting("Show watermark", true));
    private final ColorSetting accentColor = addSetting(new ColorSetting("Accent color", Theme.PRIMARY));
    private final NumberSetting scale = addSetting(new NumberSetting("Size", 0.65, 0.45, 1.0, 0.05));
    private final BooleanSetting showFps = addSetting(new BooleanSetting("Show FPS", true));
    private final BooleanSetting showPing = addSetting(new BooleanSetting("Show ping", true));
    private final ServerLatency latency = new ServerLatency();
    private String fpsText = "--", pingText = "-- ms";
    private long nextTextRefresh;

    public WatermarkHUD() {
        super("Watermark", false, Category.HUD);
        showWatermark.describe("Show limitless's compact watermark with version, FPS and connection latency at the top left.");
        accentColor.describe("Choose the watermark's logo, border accents and connection bars color.");
        scale.describe("Resize the entire watermark, including text and spacing. Lower values make it smaller.");
        showFps.describe("Show your current frames per second in the watermark.");
        showPing.describe("Show measured round-trip latency. Sends Minecraft's ping packet about every 3 seconds; unavailable latency shows --.");
    }

    public BooleanSetting showWatermark() { return showWatermark; }
    public ColorSetting accentColor() { return accentColor; }

    @Override protected void onEnable() { onWorldLeave(); }
    @Override protected void onDisable() { onWorldLeave(); }
    @Override public void onWorldLeave() {
        latency.reset(); pingText = "-- ms"; fpsText = "--"; nextTextRefresh = 0;
    }

    public void tick(MinecraftClient client) {
        if (!isEnabled()) return;
        if (!showWatermark.get() || !showPing.get()) latency.reset();
        if (client.world == null || client.player == null) return;
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        long now = System.nanoTime();
        if (showWatermark.get() && showPing.get() && handler != null && handler.isConnectionOpen()
                && !client.isInSingleplayer()) {
            // This is the same play-state packet used by vanilla's debug ping chart.
            long echoId = Util.getMeasuringTimeMs();
            if (latency.begin(handler, echoId, now)) handler.sendPacket(new QueryPingC2SPacket(echoId));
        }
        if (nextTextRefresh != 0 && now < nextTextRefresh) return;
        nextTextRefresh = now + 250_000_000L;
        fpsText = Integer.toString(Math.max(0, client.getCurrentFps()));
        if (client.isInSingleplayer()) { pingText = "Local"; return; }
        int ping = latency.milliseconds(handler, now);
        if (ping < 0 && handler != null) {
            PlayerListEntry entry = handler.getPlayerListEntry(client.player.getUuid());
            // Some servers send zero/missing Tab latency; never present that as a measured zero.
            if (entry != null && entry.getLatency() > 0) ping = entry.getLatency();
        }
        pingText = ping > 0 ? ping + " ms" : "-- ms";
    }

    /** Called from the packet thread; the tracker never reads world or GUI state. */
    public void onPingResult(ClientPlayNetworkHandler handler, long echoId, long arrivalNanos) {
        latency.accept(handler, echoId, arrivalNanos);
    }

    public void renderHud(DrawContext context, RenderTickCounter ticks) {
        if (!isEnabled() || !showWatermark.get()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        if (client.currentScreen instanceof LimitlessClickGUI || client.currentScreen instanceof BlockPickerScreen) return;
        TextRenderer font = client.textRenderer;
        int accent = accentColor.get() | 0xFF000000;
        int brandEnd = 28 + font.getWidth("limitless") + 6;
        int versionEnd = brandEnd + font.getWidth(Brand.VERSION);
        int width = versionEnd + 10;
        if (showFps.get()) width += 19 + font.getWidth("FPS") + font.getWidth(fpsText);
        if (showPing.get()) width += 33 + font.getWidth("Ping") + font.getWidth(pingText);
        float drawScale = (float) Math.min(Math.min(scale.get(), 2.25 * (scale.get() / 0.65) / client.getWindow().getScaleFactor()),
                Math.max(0.1, (context.getScaledWindowWidth() - 12.0) / width));
        context.getMatrices().pushMatrix();
        try {
            context.getMatrices().translate(6, 6);
            context.getMatrices().scale(drawScale, drawScale);
            GuiDraw.surface(context, 0, 0, width, 26, 7, accent, 1);
            GuiDraw.rounded(context, 5, 5, 16, 16, 4, Theme.SURFACE);
            Brand.drawIcon(context, 5, 5, 16);
            GuiDraw.text(context, font, "limitless", 28, 9, accent);
            GuiDraw.text(context, font, Brand.VERSION, brandEnd, 9, Theme.MUTED_TEXT);
            int x = versionEnd + 10;
            if (showFps.get()) {
                divider(context, x); x += 9;
                GuiDraw.text(context, font, "FPS", x, 9, Theme.MUTED_TEXT); x += font.getWidth("FPS") + 5;
                GuiDraw.text(context, font, fpsText, x, 9, Theme.TEXT); x += font.getWidth(fpsText) + 5;
            }
            if (showPing.get()) {
                divider(context, x); x += 9;
                // Three rising bars provide a small connection icon without extra texture work.
                for (int i = 0; i < 3; i++)
                    context.fill(x + i * 3, 17 - i * 3, x + i * 3 + 2, 19,
                            pingText.equals("-- ms") ? Theme.MUTED_TEXT : accent);
                x += 13;
                GuiDraw.text(context, font, "Ping", x, 9, Theme.MUTED_TEXT); x += font.getWidth("Ping") + 5;
                GuiDraw.text(context, font, pingText, x, 9, Theme.TEXT);
            }
        } finally { context.getMatrices().popMatrix(); }
    }

    private static void divider(DrawContext context, int x) {
        context.fill(x, 7, x + 1, 19, Theme.withAlpha(Theme.MUTED_TEXT, 45));
    }
}
