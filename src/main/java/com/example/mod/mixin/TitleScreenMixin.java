package com.example.mod.mixin;

import com.example.mod.Brand;
import com.example.mod.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void limitless$brand(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Brand.drawIcon(context, 9, 9, 26);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Brand.NAME, 40, 18, Theme.PRIMARY);
    }
}
