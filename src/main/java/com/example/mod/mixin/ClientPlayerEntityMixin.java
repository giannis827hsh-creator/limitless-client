package com.example.mod.mixin;

import com.example.mod.LimitlessClient;
import com.example.mod.license.LicenseManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.input.Input;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clear any previous movement input before the physical player ticks. */
@Mixin(ClientPlayerEntity.class)
abstract class ClientPlayerEntityMixin {
    @Shadow @Final private Input input;
    @Shadow private int ticksToNextAutoJump;

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void limitless$clearFreecamMovement(CallbackInfo ci) {
        if (!LicenseManager.isLicensed() || !LimitlessClient.FREECAM.isActive()) return;
        input.playerInput = LimitlessClient.FREECAM.playerInput();
        // A queued vanilla auto-jump must not jump the body after KeyboardInput.tick was cleared.
        ticksToNextAutoJump = 0;
    }
}
