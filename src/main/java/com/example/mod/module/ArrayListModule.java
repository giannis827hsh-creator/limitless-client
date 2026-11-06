package com.example.mod.module;

import com.example.mod.Theme;
import com.example.mod.setting.NumberSetting;
import com.example.mod.gui.GuiDraw;
import com.example.mod.gui.LimitlessClickGUI;
import com.example.mod.gui.BlockPickerScreen;
import com.example.mod.LimitlessClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.Comparator;
import java.util.List;

/** HUD list of active modules. */
public final class ArrayListModule extends Module {
    private final NumberSetting size = addSetting(new NumberSetting("Size", 0.8, 0.4, 1.2, 0.05));

    public ArrayListModule() {
        super("Array List", false, Category.HUD);
        size.describe("Resize the active module list, including text and spacing.");
    }
    public void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof LimitlessClickGUI || client.currentScreen instanceof BlockPickerScreen) return;
        float drawScale = (float) Math.min(size.get(), 2.25 * (size.get() / 0.8) / client.getWindow().getScaleFactor());
        List<Module> shown = LimitlessClient.MODULES.enabled().stream().filter(module -> module != this)
                .sorted(Comparator.comparingInt((Module m) -> client.textRenderer.getWidth(m.name())).reversed())
                .toList();
        context.getMatrices().pushMatrix();
        try {
            context.getMatrices().translate(context.getScaledWindowWidth() - 6, 6);
            context.getMatrices().scale(drawScale, drawScale);
            int y = 3;
            for (Module module : shown) {
                String label = module.name();
                int textWidth = client.textRenderer.getWidth(label);
                int x = -textWidth - 5;
                GuiDraw.surface(context, x - 5, y - 3, textWidth + 10, 15, 0, Theme.PRIMARY, 1);
                context.fill(x + textWidth + 3, y - 1, x + textWidth + 5, y + 10, Theme.PRIMARY);
                GuiDraw.text(context, client.textRenderer, label, x, y, Theme.TEXT);
                y += 16;
            }
        } finally { context.getMatrices().popMatrix(); }
    }
}
