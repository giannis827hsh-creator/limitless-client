package com.example.mod.gui;

import com.example.mod.module.BlockESP;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Searchable, explicit Block ESP selection. No blocks are enabled by default. */
public final class BlockPickerScreen extends Screen {
    /** Keep the full searchable grid, but render it as a compact centered window. */
    private static final float PICKER_SCALE = 0.62f;
    private static final int CARD = 43;
    private static final int ROW = 51;
    private static final int[] COLORS = {0xFFAA55FF, 0xFF26D9FF, 0xFFFFD160, 0xFFFF5555,
            0xFF55E890, 0xFFFFFFFF};
    private final LimitlessClickGUI parent;
    private final BlockESP module;
    private final List<Block> all = new ArrayList<>();
    private final Set<Block> selected = new LinkedHashSet<>();
    private TextFieldWidget search;
    private boolean selectedOnly;
    private int scroll;

    public BlockPickerScreen(LimitlessClickGUI parent, BlockESP module) {
        super(Text.literal("Block ESP blocks"));
        this.parent = parent;
        this.module = module;
        Set<String> savedIds = new java.util.HashSet<>(java.util.Arrays.asList(module.selectedIds().split(",")));
        for (Block block : Registries.BLOCK) {
            if (block.asItem() != Items.AIR) {
                all.add(block);
                if (savedIds.contains(Registries.BLOCK.getId(block).toString())) selected.add(block);
            }
        }
    }

    private int panelWidth() { return Math.min(width - 24, 490); }
    private int panelHeight() { return Math.min(height - 24, 400); }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return (height - panelHeight()) / 2; }
    private int gridTop() { return top() + 79; }
    private int gridBottom() { return top() + panelHeight() - 40; }
    private int columns() { return Math.max(1, (panelWidth() - 24) / CARD); }
    private double localX(double screenX) { return width / 2.0 + (screenX - width / 2.0) / PICKER_SCALE; }
    private double localY(double screenY) { return height / 2.0 + (screenY - height / 2.0) / PICKER_SCALE; }
    private Click localClick(Click click) {
        return new Click(localX(click.x()), localY(click.y()), click.buttonInfo());
    }

    @Override protected void init() {
        int x = left(), y = top(), w = panelWidth();
        search = addDrawableChild(new TextFieldWidget(textRenderer, x + 12, y + 38,
                w - 142, 20, Text.literal("Search blocks")));
        search.setMaxLength(64);
        search.setPlaceholder(Text.literal("Search blocks..."));
        setInitialFocus(search);
    }

    private List<Block> shown() {
        String query = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        List<Block> result = new ArrayList<>();
        for (Block block : all) {
            if (selectedOnly && !selected.contains(block)) continue;
            if (!query.isEmpty() && !block.getName().getString().toLowerCase(Locale.ROOT).contains(query)
                    && !Registries.BLOCK.getId(block).toString().contains(query)) continue;
            result.add(block);
        }
        return result;
    }

    private void save() { module.setSelected(selected); parent.saveStateNow(); }

    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xD9080810);
        int localMouseX = (int) Math.round(localX(mouseX));
        int localMouseY = (int) Math.round(localY(mouseY));
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(width / 2.0f, height / 2.0f);
        ctx.getMatrices().scale(PICKER_SCALE, PICKER_SCALE);
        ctx.getMatrices().translate(-width / 2.0f, -height / 2.0f);
        try {
        int x = left(), y = top(), w = panelWidth(), h = panelHeight();
        GuiDraw.rounded(ctx, x, y, w, h, 11, 0xF21C1922);
        GuiDraw.outline(ctx, x, y, w, h, 11, ClickGuiPalette.BORDER);
        ctx.drawCenteredTextWithShadow(textRenderer, "Blocks", width / 2, y + 12, ClickGuiPalette.TEXT);
        ctx.drawTextWithShadow(textRenderer, "Cancel", x + 12, y + 12, ClickGuiPalette.PRIMARY);
        ctx.drawTextWithShadow(textRenderer, "Options", x + w - 85, y + 12, ClickGuiPalette.MUTED);
        ctx.drawTextWithShadow(textRenderer, "Done", x + w - 32, y + 12, ClickGuiPalette.PRIMARY);
        GuiDraw.rounded(ctx, x + w - 119, y + 38, 107, 20, 5, ClickGuiPalette.INPUT);
        ctx.drawTextWithShadow(textRenderer, "All", x + w - 105, y + 44,
                selectedOnly ? ClickGuiPalette.MUTED : ClickGuiPalette.PRIMARY);
        ctx.drawTextWithShadow(textRenderer, "Selected", x + w - 75, y + 44,
                selectedOnly ? ClickGuiPalette.PRIMARY : ClickGuiPalette.MUTED);
        List<Block> visible = shown();
        int cols = columns();
        int rows = Math.max(1, (gridBottom() - gridTop()) / ROW);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, (visible.size() + cols - 1) / cols - rows)));
        ctx.enableScissor(x + 8, gridTop(), x + w - 8, gridBottom());
        try {
            for (int row = scroll; row < Math.min((visible.size() + cols - 1) / cols, scroll + rows + 1); row++) {
                for (int col = 0; col < cols; col++) {
                    int index = row * cols + col;
                    if (index >= visible.size()) break;
                    Block block = visible.get(index);
                    int bx = x + 12 + col * CARD, by = gridTop() + (row - scroll) * ROW;
                    boolean active = selected.contains(block);
                    boolean hovered = GuiDraw.inside(localMouseX, localMouseY, bx, by, CARD - 4, ROW - 3);
                    GuiDraw.rounded(ctx, bx, by, CARD - 4, 32, 6,
                            active ? 0xFF49305F : hovered ? ClickGuiPalette.HOVER : 0xFF302D34);
                    if (active) GuiDraw.outline(ctx, bx, by, CARD - 4, 32, 6, module.displayColor(block));
                    ctx.drawItem(block.asItem().getDefaultStack(), bx + 11, by + 7);
                    if (active) ctx.fill(bx + 31, by + 23, bx + 37, by + 29, module.displayColor(block));
                    String label = GuiDraw.fit(textRenderer, block.getName().getString(), CARD - 4);
                    ctx.drawCenteredTextWithShadow(textRenderer, label, bx + (CARD - 4) / 2, by + 36,
                            active ? ClickGuiPalette.TEXT : ClickGuiPalette.MUTED);
                }
            }
        } finally { ctx.disableScissor(); }
        ctx.fill(x + 1, gridBottom(), x + w - 1, gridBottom() + 1, ClickGuiPalette.DIVIDER);
        ctx.drawTextWithShadow(textRenderer, "Clear All", x + 12, y + h - 26, 0xFFFF6666);
        ctx.drawCenteredTextWithShadow(textRenderer, selected.size() + " Selected", width / 2,
                y + h - 30, ClickGuiPalette.TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, "Right click a block to change color", width / 2,
                y + h - 17, ClickGuiPalette.MUTED);
        ctx.drawTextWithShadow(textRenderer, "Select All", x + w - 61, y + h - 26, ClickGuiPalette.PRIMARY);
        super.render(ctx, localMouseX, localMouseY, delta);
        } finally { ctx.getMatrices().popMatrix(); }
    }

    @Override public boolean mouseClicked(Click click, boolean doubled) {
        click = localClick(click);
        int mx = (int) click.x(), my = (int) click.y();
        int x = left(), y = top(), w = panelWidth(), h = panelHeight();
        if (GuiDraw.inside(mx, my, x + 7, y + 5, 50, 24)) { close(); return true; }
        if (GuiDraw.inside(mx, my, x + w - 90, y + 5, 48, 24)) {
            save(); parent.showBlockOptions(module); return true;
        }
        if (GuiDraw.inside(mx, my, x + w - 43, y + 5, 40, 24)) { close(); return true; }
        if (GuiDraw.inside(mx, my, x + w - 119, y + 38, 107, 20)) {
            selectedOnly = mx >= x + w - 80; scroll = 0; return true;
        }
        if (GuiDraw.inside(mx, my, x + 8, y + h - 35, 78, 32)) {
            selected.clear(); save(); return true;
        }
        if (GuiDraw.inside(mx, my, x + w - 80, y + h - 35, 76, 32)) {
            selected.addAll(shown()); save(); return true;
        }
        if (my >= gridTop() && my < gridBottom()) {
            int col = (mx - x - 12) / CARD, row = (my - gridTop()) / ROW + scroll;
            int index = row * columns() + col;
            List<Block> visible = shown();
            if (mx >= x + 12 && col >= 0 && col < columns() && index >= 0 && index < visible.size()) {
                Block block = visible.get(index);
                if (click.button() == 0) {
                    if (!selected.add(block)) selected.remove(block);
                    save();
                } else if (click.button() == 1) {
                    int current = module.displayColor(block), palette = -1;
                    for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == current) palette = i;
                    module.setDisplayColor(block, COLORS[(palette + 1) % COLORS.length]);
                    save();
                }
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override public boolean mouseReleased(Click click) {
        return super.mouseReleased(localClick(click));
    }

    @Override public boolean mouseDragged(Click click, double dx, double dy) {
        return super.mouseDragged(localClick(click), dx / PICKER_SCALE, dy / PICKER_SCALE);
    }

    @Override public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(localX(mouseX), localY(mouseY));
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        mouseX = localX(mouseX);
        mouseY = localY(mouseY);
        if (mouseY >= gridTop() && mouseY < gridBottom()) {
            scroll = Math.max(0, scroll - (int) Math.signum(vertical)); return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return super.keyPressed(input);
    }

    @Override public void close() {
        save();
        if (client != null) client.setScreen(parent);
    }

    @Override public boolean shouldPause() { return false; }
}
