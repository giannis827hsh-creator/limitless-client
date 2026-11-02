package com.example.mod.mixin;

import com.example.mod.LimitlessClient;
import com.example.mod.license.LicenseManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Honor the existing Show player option for the physical Freecam body. */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
    @Inject(method = "shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z",
            at = @At("HEAD"), cancellable = true)
    private void limitless$hideFreecamBody(Entity entity, Frustum frustum, double x, double y, double z,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (LicenseManager.isLicensed() && LimitlessClient.FREECAM.isActive()
                && !LimitlessClient.FREECAM.shouldRenderPlayer()
                && entity == MinecraftClient.getInstance().player) {
            cir.setReturnValue(false);
        }
    }
}
