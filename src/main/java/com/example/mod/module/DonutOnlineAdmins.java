package com.example.mod.module;

import com.example.mod.Theme;
import com.example.mod.gui.GuiDraw;
import com.example.mod.setting.StringSetting;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

/** Shows Tab List entries marked with a green or purple staff star. */
public final class DonutOnlineAdmins extends Module {
    private static final String HEADER = "ONLINE ADMINS";
    private static final float HUD_SCALE = 0.8f;
    private static final int MAX_VISIBLE = 6;
    private static final int ALERT_RED = 0xFFFF4040;
    private static final int NONE_GRAY = 0xFFAAAAAA;
    private List<String> cachedAdmins = List.of();
    private Object cachedHandler;
    private String cachedSymbols = "";
    private long nextRefresh;
    private final StringSetting starSymbols = addSetting(
            new StringSetting("Staff star symbols", "\u2605\u2606\u2726\u2727\u2729\u272a\u272b\u272c\u272d\u272e\u272f\u2730\u2736\u2737\u2738\u2b50\ud83c\udf1f", 32));

    public DonutOnlineAdmins() {
        super("Online Admins", false, Category.DONUT);
        starSymbols.describe("Symbols to recognize as staff stars in Tab names. Formatted stars must be green or purple; ranks can vary by server.");
    }

    @Override protected void onDisable() { onWorldLeave(); }
    @Override public void onWorldLeave() {
        cachedAdmins = List.of(); cachedHandler = null; nextRefresh = 0;
    }

    public void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) return;

        // Names need two updates per second, rather than a full Tab scan every frame.
        long now = System.nanoTime();
        if (cachedHandler != client.getNetworkHandler() || !cachedSymbols.equals(starSymbols.get()) || now >= nextRefresh) {
            cachedAdmins = client.getNetworkHandler().getListedPlayerListEntries().stream()
                .filter(this::hasAdminStar)
                .map(entry -> entry.getProfile().name())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
            cachedHandler = client.getNetworkHandler(); cachedSymbols = starSymbols.get();
            nextRefresh = now + 500_000_000L;
        }
        List<String> admins = cachedAdmins;

        // Draw in local coordinates so the whole card, including its border and
        // spacing, shrinks evenly without changing the text size elsewhere.
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(8.0f, 26.0f); // Below the limitless watermark.
        context.getMatrices().scale(HUD_SCALE, HUD_SCALE);
        if (admins.isEmpty()) {
            drawPanel(context, 106, 19);
            context.fill(9, 8, 13, 12, NONE_GRAY);
            context.drawTextWithShadow(client.textRenderer, "Admins: None", 20, 5, NONE_GRAY);
            context.getMatrices().popMatrix();
            return;
        }

        int availableRows = Math.max(1, (int) ((context.getScaledWindowHeight() - 36) / HUD_SCALE - 27) / 11);
        int shown = Math.min(admins.size(), Math.min(MAX_VISIBLE, availableRows));
        int hidden = admins.size() - shown;
        int width = Math.max(116, client.textRenderer.getWidth(HEADER) + 42);
        for (int i = 0; i < shown; i++) {
            width = Math.max(width, client.textRenderer.getWidth(admins.get(i)) + 18);
        }
        width = Math.min(width, 165);
        int height = 21 + shown * 11 + (hidden > 0 ? 11 : 0);
        drawPanel(context, width, height);
        context.fill(8, 7, 12, 11, ALERT_RED);
        context.drawTextWithShadow(client.textRenderer, HEADER, 17, 5, Theme.PRIMARY);
        String count = Integer.toString(admins.size());
        int countX = width - client.textRenderer.getWidth(count) - 8;
        context.drawTextWithShadow(client.textRenderer, count, countX, 5, Theme.MUTED_TEXT);
        context.fill(7, 17, width - 7, 18, Theme.BORDER);
        for (int i = 0; i < shown; i++) {
            context.drawTextWithShadow(client.textRenderer,
                    GuiDraw.fit(client.textRenderer, admins.get(i), width - 16),
                    8, 21 + i * 11, ALERT_RED);
        }
        if (hidden > 0) {
            context.drawTextWithShadow(client.textRenderer, "+" + hidden + " more",
                    8, 21 + shown * 11, NONE_GRAY);
        }
        context.getMatrices().popMatrix();
    }

    public boolean hasAdminStar(PlayerListEntry entry) {
        String symbols = starSymbols.get();
        if (symbols.isEmpty()) return false;
        Text displayName = entry.getDisplayName();
        if (displayName == null) {
            displayName = Team.decorateName(entry.getScoreboardTeam(), Text.literal(entry.getProfile().name()));
        }
        // Visit styled components so a star can be checked with its actual Tab
        // color, including a color inherited from the component's parent.
        return displayName.visit((style, part) ->
                containsStar(part, symbols) && isStaffStarColor(style.getColor())
                        ? Optional.of(Boolean.TRUE) : Optional.empty(), Style.EMPTY).orElse(false);
    }

    private static boolean containsStar(String value, String symbols) {
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            for (int j = 0; j < symbols.length(); ) {
                int symbol = symbols.codePointAt(j);
                if (codePoint == symbol) return true;
                j += Character.charCount(symbol);
            }
            i += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean isStaffStarColor(TextColor color) {
        if (color == null) return false; // An uncolored symbol is not a green or purple staff marker.
        int rgb = color.getRgb();
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return (green >= 90 && green > red * 1.2 && green > blue * 1.1)
                || (blue >= 90 && red >= 55 && blue > green * 1.2 && red > green * 1.2);
    }

    private static void drawPanel(DrawContext context, int width, int height) {
        GuiDraw.surface(context, 0, 0, width, height, 5, Theme.PRIMARY, 1);
    }
}
