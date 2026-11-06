package com.example.mod.module;

import com.example.mod.Theme;
import com.example.mod.gui.GuiDraw;
import com.example.mod.LimitlessClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.Comparator;
import java.util.List;

/** HUD list of active modules. */
public final class ArrayListModule extends Module {
    public ArrayListModule() { super("Array List", false, Category.HUD); }
    public void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        List<Module> shown = LimitlessClient.MODULES.enabled().stream().filter(module -> module != this)
                .sorted(Comparator.comparingInt((Module m) -> client.textRenderer.getWidth(m.name())).reversed())
                .toList();
        int y = 8;
        for (Module module : shown) {
            String label = module.name();
            int textWidth = client.textRenderer.getWidth(label);
            int x = context.getScaledWindowWidth() - textWidth - 8;
            GuiDraw.surface(context, x - 5, y - 3, textWidth + 10, 15, 0, Theme.PRIMARY, 1);
            context.fill(x + textWidth + 3, y - 1, x + textWidth + 5, y + 10, Theme.PRIMARY);
            GuiDraw.text(context, client.textRenderer, label, x, y, Theme.TEXT);
            y += 17;
        }
    }
}
