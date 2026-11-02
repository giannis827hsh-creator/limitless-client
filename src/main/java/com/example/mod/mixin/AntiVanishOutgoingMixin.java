package com.example.mod.mixin;

import com.example.mod.LimitlessClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe local interactions without cancelling or modifying outgoing packets. */
@Mixin(ClientCommonNetworkHandler.class)
abstract class AntiVanishOutgoingMixin {
    @Inject(method = "sendPacket", at = @At("HEAD"))
    private void limitless$staffOwnAction(Packet<?> packet, CallbackInfo ci) {
        LimitlessClient.DONUT_DETECTOR.onOutgoing(packet);
    }
}
