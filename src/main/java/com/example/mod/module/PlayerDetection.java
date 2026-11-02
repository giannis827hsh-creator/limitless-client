package com.example.mod.module;

import com.example.mod.Theme;
import com.example.mod.gui.GuiDraw;
import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.IntegerSetting;
import com.example.mod.setting.StringSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Reports real player entities in the loaded client world; Tab entries alone are not proximity evidence. */
public final class PlayerDetection extends Module {
    private static final long SCAN_INTERVAL_MS = 250;
    private static final int MAX_HUD_ROWS = 4;
    private final IntegerSetting range = addSetting(new IntegerSetting("Range", 96, 16, 256));
    private final BooleanSetting chatAlerts = addSetting(new BooleanSetting("Chat alerts", true));
    private final BooleanSetting showHud = addSetting(new BooleanSetting("Show HUD", true));
    private final BooleanSetting includeInvisible = addSetting(new BooleanSetting("Include invisible", true));
    private final StringSetting ignoredNames = addSetting(new StringSetting("Ignore names", "", 512));
    private final IntegerSetting alertCooldown = addSetting(new IntegerSetting("Alert cooldown seconds", 30, 5, 180));
    private final Set<UUID> present = new HashSet<>();
    private final Map<UUID, Long> lastAlert = new HashMap<>();
    private List<NearbyPlayer> nearby = List.of();
    private ClientWorld observedWorld;
    private long nextScan;
    private String lastIgnoredRaw = "";
    private Set<String> ignored = Set.of();

    public PlayerDetection() {
        super("Player Detection", false, Category.VISUALS);
        range.describe("Maximum distance from your player, in blocks. Only entities in loaded chunks can be detected.");
        chatAlerts.describe("Show one local chat alert when a player enters range. Several arrivals are grouped together.");
        showHud.describe("Show a small list of nearby players at the bottom left of the screen.");
        includeInvisible.describe("Count invisible player entities too. This does not reveal players the server never sent to your client.");
        ignoredNames.describe("Usernames to ignore, separated by commas or spaces. Comparison ignores capitalization.");
        alertCooldown.describe("Minimum time before the same player can produce another chat alert after leaving and returning.");
    }

    @Override protected void onEnable() { clear(); }
    @Override protected void onDisable() { clear(); }
    @Override public void onWorldLeave() { clear(); }

    private void clear() {
        present.clear(); lastAlert.clear(); nearby = List.of(); observedWorld = null; nextScan = 0;
    }

    public void tick(MinecraftClient client) {
        if (!isEnabled() || client.world == null || client.player == null) {
            if (observedWorld != null) clear();
            return;
        }
        if (observedWorld != client.world) {
            clear();
            observedWorld = client.world;
        }
        long now = System.nanoTime() / 1_000_000;
        if (now < nextScan) return;
        nextScan = now + SCAN_INTERVAL_MS;
        updateIgnoredNames();

        double maxDistanceSq = (double) range.get() * range.get();
        List<NearbyPlayer> found = new ArrayList<>();
        Set<UUID> current = new HashSet<>();
        List<NearbyPlayer> arrivals = new ArrayList<>();
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player == client.player || player.isRemoved() || !player.isAlive()
                    || !includeInvisible.get() && player.isInvisible()) continue;
            String name = player.getGameProfile().name();
            if (name == null || name.isBlank() || ignored.contains(name.toLowerCase(Locale.ROOT))) continue;
            double distanceSq = player.squaredDistanceTo(client.player);
            if (distanceSq > maxDistanceSq) continue;
            UUID id = player.getUuid();
            current.add(id);
            NearbyPlayer sighting = new NearbyPlayer(name, Math.round(Math.sqrt(distanceSq)), player.isInvisible());
            found.add(sighting);
            if (!present.contains(id) && now - lastAlert.getOrDefault(id, Long.MIN_VALUE / 2)
                    >= alertCooldown.get() * 1_000L) {
                arrivals.add(sighting);
                lastAlert.put(id, now);
            }
        }
        found.sort(Comparator.comparingLong(NearbyPlayer::distance).thenComparing(NearbyPlayer::name));
        nearby = List.copyOf(found);
        present.clear();
        present.addAll(current);
        if (lastAlert.size() > 512) lastAlert.entrySet().removeIf(entry -> now - entry.getValue() > 600_000);
        if (chatAlerts.get() && !arrivals.isEmpty()) {
            StringBuilder names = new StringBuilder();
            int shown = Math.min(3, arrivals.size());
            for (int i = 0; i < shown; i++) {
                if (i > 0) names.append(", ");
                NearbyPlayer player = arrivals.get(i);
                names.append(player.name()).append(" (").append(player.distance()).append("m)");
            }
            if (arrivals.size() > shown) names.append(" +").append(arrivals.size() - shown).append(" more");
            client.player.sendMessage(Text.literal("[limitless Player Detection] Nearby: " + names)
                    .formatted(Formatting.LIGHT_PURPLE), false);
        }
    }

    private void updateIgnoredNames() {
        String raw = ignoredNames.get();
        if (raw.equals(lastIgnoredRaw)) return;
        lastIgnoredRaw = raw;
        Set<String> parsed = new HashSet<>();
        for (String part : raw.split("[,\\s]+")) {
            if (!part.isBlank()) parsed.add(part.toLowerCase(Locale.ROOT));
        }
        ignored = Set.copyOf(parsed);
    }

    public void renderHud(DrawContext context, RenderTickCounter ticks) {
        if (!isEnabled() || !showHud.get() || nearby.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || client.currentScreen != null) return;
        int rows = Math.min(MAX_HUD_ROWS, nearby.size());
        int width = 150, height = 25 + rows * 11 + (nearby.size() > rows ? 10 : 0);
        int x = 8, y = Math.max(8, context.getScaledWindowHeight() - height - 52);
        GuiDraw.rounded(context, x, y, width, height, 5, Theme.PANEL_BACKGROUND);
        GuiDraw.outline(context, x, y, width, height, 5, Theme.BORDER);
        context.drawTextWithShadow(client.textRenderer, "PLAYERS NEARBY", x + 8, y + 6, Theme.PRIMARY);
        String count = Integer.toString(nearby.size());
        context.drawTextWithShadow(client.textRenderer, count,
                x + width - 8 - client.textRenderer.getWidth(count), y + 6, Theme.MUTED_TEXT);
        context.fill(x + 7, y + 18, x + width - 7, y + 19, Theme.BORDER);
        for (int i = 0; i < rows; i++) {
            NearbyPlayer player = nearby.get(i);
            String distance = player.distance() + "m";
            String name = GuiDraw.fit(client.textRenderer, player.name(),
                    width - 24 - client.textRenderer.getWidth(distance));
            int rowY = y + 23 + i * 11;
            context.drawTextWithShadow(client.textRenderer, name, x + 8, rowY,
                    player.invisible() ? 0xFFCC99FF : Theme.TEXT);
            context.drawTextWithShadow(client.textRenderer, distance,
                    x + width - 8 - client.textRenderer.getWidth(distance), rowY, Theme.MUTED_TEXT);
        }
        if (nearby.size() > rows) context.drawTextWithShadow(client.textRenderer,
                "+" + (nearby.size() - rows) + " more", x + 8, y + 23 + rows * 11, Theme.MUTED_TEXT);
    }

    private record NearbyPlayer(String name, long distance, boolean invisible) { }
}
