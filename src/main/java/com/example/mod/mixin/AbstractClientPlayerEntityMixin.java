package com.example.mod.mixin;

import com.example.mod.LimitlessClient;
import com.example.mod.license.LicenseManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client-only visual override for local and remote player entities. */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void limitless$useTargetSkin(CallbackInfoReturnable<SkinTextures> cir) {
        if (!LicenseManager.isLicensed() || !LimitlessClient.SKIN_OVERRIDE.isEnabled()) return;

        SkinTextures replacement = LimitlessClient.SKIN_OVERRIDE.replacement(MinecraftClient.getInstance());
        if (replacement != null) cir.setReturnValue(replacement);
    }
}
