package com.example.mod.mixin;

import com.example.mod.LimitlessClient;
import com.example.mod.license.LicenseManager;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keyboard input belongs to the camera while Freecam is active, not to player movement. */
@Mixin(KeyboardInput.class)
abstract class KeyboardInputMixin extends Input {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void limitless$blockPlayerMovementKeys(CallbackInfo ci) {
        if (!LicenseManager.isLicensed() || !LimitlessClient.FREECAM.isActive()) return;
        playerInput = LimitlessClient.FREECAM.playerInput();
        movementVector = Vec2f.ZERO;
        ci.cancel();
    }
}
