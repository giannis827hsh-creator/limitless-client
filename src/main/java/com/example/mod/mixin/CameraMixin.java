package com.example.mod.mixin;

import com.example.mod.LimitlessClient;
import com.example.mod.license.LicenseManager;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies Freecam's detached pose after vanilla updates its normal player camera. */
@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow private boolean thirdPerson;

    @ModifyArgs(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
    private void limitless$freelookRotation(Args args) {
        if (LicenseManager.isLicensed() && LimitlessClient.FREELOOK != null
                && LimitlessClient.FREELOOK.isActive()) {
            args.set(0, LimitlessClient.FREELOOK.yaw());
            args.set(1, LimitlessClient.FREELOOK.pitch());
        }
    }

    @Inject(method = "clipToSpace", at = @At("HEAD"), cancellable = true)
    private void limitless$freelookThroughWalls(float distance, CallbackInfoReturnable<Float> cir) {
        if (LicenseManager.isLicensed() && LimitlessClient.FREELOOK != null
                && LimitlessClient.FREELOOK.isActive() && LimitlessClient.FREELOOK.throughWalls()) {
            cir.setReturnValue(distance);
        }
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void limitless$applyFreecam(World area, Entity focusedEntity, boolean thirdPerson,
                                      boolean inverseView, float tickProgress, CallbackInfo ci) {
        if (LicenseManager.isLicensed() && LimitlessClient.FREECAM.isActive()) {
            LimitlessClient.FREECAM.applyCamera((Camera) (Object) this);
            // Detached view allows the real local player to render when Show player is enabled.
            this.thirdPerson = true;
        }
    }
}

