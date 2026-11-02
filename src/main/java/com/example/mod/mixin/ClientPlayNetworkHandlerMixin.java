package com.example.mod.mixin;

import com.example.mod.LimitlessClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.query.PingResultS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe vanilla packet handlers only after Minecraft has applied each packet. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onPingResult", at = @At("HEAD"))
    private void limitless$watermarkPing(PingResultS2CPacket packet, CallbackInfo ci) {
        // Capture arrival before client scheduling/rendering can inflate the measurement.
        LimitlessClient.WATERMARK.onPingResult((ClientPlayNetworkHandler) (Object) this,
                packet.startTime(), System.nanoTime());
    }

    @Inject(method = "onPlayerPositionLook", at = @At("TAIL"))
    private void limitless$acceptServerCorrection(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        LimitlessClient.FREECAM.stopForServerCorrection();
        LimitlessClient.DONUT_DETECTOR.onServerCorrection();
    }

    @Inject(method = "onChunkData", at = @At("TAIL"))
    private void limitless$onChunkData(ChunkDataS2CPacket packet, CallbackInfo ci) {
        LimitlessClient.SUS_CHUNK.onChunkData(packet);
    }

    @Inject(method = "onBlockUpdate", at = @At("TAIL"))
    private void limitless$onBlockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        LimitlessClient.SUS_CHUNK.onBlockUpdate(packet.getPos(), packet.getState());
    }

    @Inject(method = "onChunkDeltaUpdate", at = @At("TAIL"))
    private void limitless$onChunkDeltaUpdate(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
        packet.visitUpdates(LimitlessClient.SUS_CHUNK::onBlockUpdate);
    }

}
