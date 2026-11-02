package com.example.mod.mixin;

import com.example.mod.LimitlessClient;
import com.example.mod.license.LicenseManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sends sensitivity-adjusted local mouse look to the detached camera instead of the player body. */
@Mixin(Entity.class)
abstract class EntityLookMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void limitless$detachedLook(double dx, double dy, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if ((Object) this != client.player || !LicenseManager.isLicensed()) return;
        if (LimitlessClient.FREECAM != null && LimitlessClient.FREECAM.isActive()) {
            LimitlessClient.FREECAM.look(dx, dy);
            ci.cancel();
        } else if (LimitlessClient.FREELOOK != null && LimitlessClient.FREELOOK.isActive()) {
            LimitlessClient.FREELOOK.look(dx, dy);
            ci.cancel();
        }
    }
}
