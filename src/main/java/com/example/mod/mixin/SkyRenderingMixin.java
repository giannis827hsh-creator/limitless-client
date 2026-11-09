package com.example.mod.mixin;

import com.example.mod.LimitlessClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRendering.class)
abstract class SkyRenderingMixin {
    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void limitless$customSky(ClientWorld world, float tickProgress, Camera camera,
                                     SkyRenderState state, CallbackInfo ci) {
        if (LimitlessClient.CUSTOM_SKY != null) LimitlessClient.CUSTOM_SKY.apply(state);
    }
}
