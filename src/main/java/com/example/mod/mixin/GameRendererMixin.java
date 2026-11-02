package com.example.mod.mixin;

import com.example.mod.LimitlessClient;
import com.example.mod.license.LicenseManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.LivingEntity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Supplies full night-vision brightness locally without adding a status effect. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void limitless$hideFreecamHand(float tickProgress, boolean sleep, Matrix4f matrix,
                                      CallbackInfo ci) {
        if (LicenseManager.isLicensed() && LimitlessClient.FREECAM.isActive()) ci.cancel();
    }

    @Inject(method = "getNightVisionStrength", at = @At("HEAD"), cancellable = true)
    private static void limitless$fullbrightNightVision(LivingEntity entity, float tickDelta,
                                                       CallbackInfoReturnable<Float> cir) {
        if (LicenseManager.isLicensed()
                && entity == net.minecraft.client.MinecraftClient.getInstance().player
                && LimitlessClient.FULLBRIGHT.usesNightVision()) {
            cir.setReturnValue(1.0F);
        }
    }
}

