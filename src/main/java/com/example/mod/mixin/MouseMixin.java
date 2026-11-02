package com.example.mod.mixin;

import com.example.mod.LimitlessClient;
import com.example.mod.license.LicenseManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Handles Freecam scrolling while leaving mouse look available to other mods. */
@Mixin(Mouse.class)
abstract class MouseMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void limitless$freecamWheel(long window, double horizontal, double vertical, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!LicenseManager.isLicensed() || !LimitlessClient.FREECAM.isActive()
                || client.currentScreen != null || window != client.getWindow().getHandle()) return;
        LimitlessClient.FREECAM.scroll(vertical);
        ci.cancel();
    }
}
