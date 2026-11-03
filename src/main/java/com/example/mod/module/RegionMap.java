package com.example.mod.module;

import com.example.mod.gui.GuiDraw;
import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.ColorSetting;
import com.example.mod.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.world.World;

/** DonutSMP Goliath region map. */
public final class RegionMap extends Module {
    private static final int[] REGION_COLORS = {
            -10236257, -10246656, -1012536, -4559313, -1397425, -12401931, -16545540
    };
    private static final int[] LEGEND_ORDER = {0, 1, 2, 4, 3, 5, 6};

    private final NumberSetting cellSize = addSetting(new NumberSetting("Cell Size", 6, 3, 16, 0.5));
    private final NumberSetting playerScale = addSetting(new NumberSetting("Player Scale", 1, 0.5, 3, 0.1));
    private final NumberSetting textScale = addSetting(new NumberSetting("Text Scale", 1, 0.1, 2, 0.1));
    private final NumberSetting opacity = addSetting(new NumberSetting("Opacity", 1, 0.1, 1, 0.05));
    private final BooleanSetting shadow = addSetting(new BooleanSetting("Shadow", true));
    private final BooleanSetting regionIds = addSetting(new BooleanSetting("Show Goliath Numbers", true));
    private final BooleanSetting legend = addSetting(new BooleanSetting("Show Legend", true));
    private final BooleanSetting coordinates = addSetting(new BooleanSetting("Show Coordinates", true));
    private final ColorSetting playerIndicator = addSetting(new ColorSetting("Player Indicator", -52686));

    public RegionMap() {
        super("Region Map", false, Category.DONUT);
        cellSize.describe("Size of each DonutSMP map square.");
        playerScale.describe("Size of your marker on the map.");
        textScale.describe("Size of Goliath numbers.");
        opacity.describe("Map transparency.");
        regionIds.describe("Show each Goliath's number.");
    }

    public void renderHud(DrawContext context, RenderTickCounter ticks) {
        if (!isEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.currentScreen != null) return;

        int maxCell = Math.max(3, (context.getScaledWindowHeight() - (legend.get() ? 146 : 62)) / DonutGoliaths.GRID);
        int square = Math.max(3, Math.min((int) Math.round(cellSize.get()), maxCell));
        int mapSize = square * DonutGoliaths.GRID;
        int panelWidth = mapSize + 20;
        int infoHeight = 14 + (coordinates.get() ? 12 : 0) + (legend.get() ? 88 : 0);
        int panelHeight = 31 + mapSize + infoHeight;
        int panelX = Math.max(8, context.getScaledWindowWidth() - panelWidth - 12);
        int panelY = 8;
        int mapX = panelX + 10;
        int mapY = panelY + 22;
        float alpha = (float) opacity.get();

        if (shadow.get()) {
            GuiDraw.rounded(context, panelX + 3, panelY + 4, panelWidth, panelHeight, 7,
                    GuiDraw.fade(0x99000000, alpha));
        }
        GuiDraw.rounded(context, panelX, panelY, panelWidth, panelHeight, 7,
                GuiDraw.fade(0xED100C19, alpha));
        GuiDraw.outline(context, panelX, panelY, panelWidth, panelHeight, 7,
                GuiDraw.fade(0xFF8B5CF6, alpha));
        context.drawText(client.textRenderer, "DONUT REGION MAP", mapX, panelY + 8,
                GuiDraw.fade(0xFFD9C6FF, alpha), false);

        for (int region = 0; region < DonutGoliaths.CELLS.length; region++) {
            int color = GuiDraw.fade(REGION_COLORS[region], alpha);
            for (int[] tile : DonutGoliaths.CELLS[region]) {
                context.fill(mapX + tile[0] * square, mapY + tile[1] * square,
                        mapX + tile[2] * square, mapY + tile[3] * square, color);
            }
        }
        drawDividers(context, mapX, mapY, square, alpha);
        if (regionIds.get()) {
            float labelScale = Math.max(0.1f, (float) textScale.get());
            for (int[][] region : DonutGoliaths.CELLS) {
                for (int i = 0; i < region.length; i++) {
                    int[] tile = region[i];
                    int width = (tile[2] - tile[0]) * square;
                    int height = (tile[3] - tile[1]) * square;
                    String label = Integer.toString(i + 1);
                    if (client.textRenderer.getWidth(label) * labelScale > width - 2
                            || client.textRenderer.fontHeight * labelScale > height - 1) continue;
                    float x = mapX + tile[0] * square + (width - client.textRenderer.getWidth(label) * labelScale) / 2;
                    float y = mapY + tile[1] * square + (height - client.textRenderer.fontHeight * labelScale) / 2;
                    context.getMatrices().pushMatrix();
                    context.getMatrices().translate(x, y);
                    context.getMatrices().scale(labelScale, labelScale);
                    context.drawText(client.textRenderer, label, 0, 0,
                            GuiDraw.fade(0xFFF5F0FF, alpha), true);
                    context.getMatrices().popMatrix();
                }
            }
        }

        int here = client.world.getRegistryKey() == World.OVERWORLD
                ? DonutGoliaths.at(client.player.getX(), client.player.getZ()) : -1;
        if (client.world.getRegistryKey() == World.OVERWORLD
                && client.player.getX() >= -DonutGoliaths.HALF && client.player.getX() < DonutGoliaths.HALF
                && client.player.getZ() >= -DonutGoliaths.HALF && client.player.getZ() < DonutGoliaths.HALF) {
            int pinX = mapX + (int) ((client.player.getX() + DonutGoliaths.HALF)
                    * mapSize / (DonutGoliaths.HALF * 2.0));
            int pinY = mapY + (int) ((client.player.getZ() + DonutGoliaths.HALF)
                    * mapSize / (DonutGoliaths.HALF * 2.0));
            int radius = Math.max(3, (int) Math.round(5 * playerScale.get()));
            double angle = Math.toRadians(-client.player.getYaw() - 90.0);
            drawArrow(context, pinX, pinY, radius + 1, angle, GuiDraw.fade(0xFF120E1B, alpha));
            drawArrow(context, pinX, pinY, radius, angle, GuiDraw.fade(playerIndicator.get(), alpha));
        }

        int infoY = mapY + mapSize + 7;
        if (coordinates.get()) {
            context.drawText(client.textRenderer, "X " + client.player.getBlockX() + "   Z " + client.player.getBlockZ(),
                    mapX, infoY, GuiDraw.fade(0xFFC9BDDB, alpha), false);
            infoY += 12;
        }
        String name = here < 0 ? "Outside regions"
                : (here % 1000 + 1) + "  ·  " + DonutGoliaths.REGIONS[here / 1000];
        context.drawText(client.textRenderer, "Region  " + name, mapX, infoY,
                GuiDraw.fade(0xFFF3EAFB, alpha), false);
        if (legend.get()) {
            int legendY = infoY + 16;
            for (int i = 0; i < LEGEND_ORDER.length; i++) {
                int region = LEGEND_ORDER[i];
                int y = legendY + i * 12;
                context.fill(mapX, y + 1, mapX + 8, y + 9, GuiDraw.fade(REGION_COLORS[region], alpha));
                context.drawText(client.textRenderer, DonutGoliaths.REGIONS[region], mapX + 13, y,
                        GuiDraw.fade(0xFFD6CBE4, alpha), false);
            }
        }
    }

    private static void drawDividers(DrawContext context, int x, int y, int square, float alpha) {
        for (int line = 1; line < DonutGoliaths.GRID; line++) {
            for (int along = 0; along < DonutGoliaths.GRID; along++) {
                int left = DonutGoliaths.square(line - 1, along);
                int right = DonutGoliaths.square(line, along);
                if (left != right) {
                    boolean regionEdge = left < 0 || right < 0 || left / 1000 != right / 1000;
                    context.fill(x + line * square, y + along * square,
                            x + line * square + 1, y + (along + 1) * square,
                            GuiDraw.fade(regionEdge ? 0xAA07040D : 0x6607040D, alpha));
                }
                int above = DonutGoliaths.square(along, line - 1);
                int below = DonutGoliaths.square(along, line);
                if (above != below) {
                    boolean regionEdge = above < 0 || below < 0 || above / 1000 != below / 1000;
                    context.fill(x + along * square, y + line * square,
                            x + (along + 1) * square, y + line * square + 1,
                            GuiDraw.fade(regionEdge ? 0xAA07040D : 0x6607040D, alpha));
                }
            }
        }
    }

    private static void drawArrow(DrawContext context, int x, int y, int radius, double angle, int color) {
        double[] px = new double[3];
        double[] py = new double[3];
        for (int i = 0; i < 3; i++) {
            double a = angle + (i == 0 ? 0 : i == 1 ? Math.toRadians(135) : -Math.toRadians(135));
            px[i] = x + Math.cos(a) * radius;
            py[i] = y - Math.sin(a) * radius;
        }
        int minX = (int) Math.floor(Math.min(px[0], Math.min(px[1], px[2])));
        int maxX = (int) Math.ceil(Math.max(px[0], Math.max(px[1], px[2])));
        int minY = (int) Math.floor(Math.min(py[0], Math.min(py[1], py[2])));
        int maxY = (int) Math.ceil(Math.max(py[0], Math.max(py[1], py[2])));
        for (int yy = minY; yy <= maxY; yy++) {
            for (int xx = minX; xx <= maxX; xx++) {
                double a = (px[1] - px[0]) * (yy + 0.5 - py[0]) - (py[1] - py[0]) * (xx + 0.5 - px[0]);
                double b = (px[2] - px[1]) * (yy + 0.5 - py[1]) - (py[2] - py[1]) * (xx + 0.5 - px[1]);
                double c = (px[0] - px[2]) * (yy + 0.5 - py[2]) - (py[0] - py[2]) * (xx + 0.5 - px[2]);
                if ((a >= 0 && b >= 0 && c >= 0) || (a <= 0 && b <= 0 && c <= 0)) {
                    context.fill(xx, yy, xx + 1, yy + 1, color);
                }
            }
        }
    }
}
