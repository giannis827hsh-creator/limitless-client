package com.example.mod.mixin;

import com.example.mod.LimitlessClient;
import com.example.mod.license.LicenseManager;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses attack, use, drop, and other in-world key actions while the camera is detached. */
@Mixin(MinecraftClient.class)
abstract class MinecraftClientMixin {
    @Inject(method = "handleInputEvents", at = @At("HEAD"), cancellable = true)
    private void limitless$suppressFreecamActions(CallbackInfo ci) {
        if (LicenseManager.isLicensed() && LimitlessClient.FREECAM.isActive()) {
            LimitlessClient.FREECAM.suppressActions();
            ci.cancel();
        }
    }
}

