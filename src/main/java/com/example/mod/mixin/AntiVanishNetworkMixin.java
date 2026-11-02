package com.example.mod.mixin;

import com.example.mod.LimitlessClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla schedules these handlers on the client thread before their TAIL hooks run. */
@Mixin(ClientPlayNetworkHandler.class)
abstract class AntiVanishNetworkMixin {
    @Inject(method = "onPlayerList", at = @At("TAIL"))
    private void limitless$staffTabUpdate(PlayerListS2CPacket packet, CallbackInfo ci) {
        if (!LimitlessClient.DONUT_DETECTOR.isEnabled()) return;
        long hidden = packet.getActions().contains(PlayerListS2CPacket.Action.UPDATE_LISTED)
                ? packet.getEntries().stream().filter(entry -> !entry.listed()).count() : 0;
        LimitlessClient.DONUT_DETECTOR.onTabChanged(hidden >= 4);
    }
    @Inject(method = "onPlayerRemove", at = @At("TAIL"))
    private void limitless$staffTabRemove(PlayerRemoveS2CPacket packet, CallbackInfo ci) {
        LimitlessClient.DONUT_DETECTOR.onTabChanged(packet.profileIds().size() >= 4);
    }
    @Inject(method = "onGameMessage", at = @At("TAIL"))
    private void limitless$staffDepartures(GameMessageS2CPacket packet, CallbackInfo ci) {
        if (!packet.overlay()) LimitlessClient.DONUT_DETECTOR.onSystemMessage(packet.content());
    }
    @Inject(method = {"onGameJoin", "onPlayerRespawn", "onEnterReconfiguration"}, at = @At("TAIL"))
    private void limitless$staffSessionChanged(CallbackInfo ci) { LimitlessClient.DONUT_DETECTOR.onServerTransfer(); }
    @Inject(method = "onPlaySound", at = @At("TAIL"))
    private void limitless$staffSound(PlaySoundS2CPacket packet, CallbackInfo ci) { LimitlessClient.DONUT_DETECTOR.onSound(packet); }
    @Inject(method = "onPlaySoundFromEntity", at = @At("TAIL"))
    private void limitless$staffEntitySound(PlaySoundFromEntityS2CPacket packet, CallbackInfo ci) { LimitlessClient.DONUT_DETECTOR.onEntitySound(packet); }
    @Inject(method = "onParticle", at = @At("TAIL"))
    private void limitless$staffParticle(ParticleS2CPacket packet, CallbackInfo ci) { LimitlessClient.DONUT_DETECTOR.onParticle(packet); }
    @Inject(method = "onExplosion", at = @At("TAIL"))
    private void limitless$staffExplosion(ExplosionS2CPacket packet, CallbackInfo ci) { LimitlessClient.DONUT_DETECTOR.onExplosion(packet); }
    @Inject(method = "onCommandSuggestions", at = @At("HEAD"), cancellable = true)
    private void limitless$staffCompletionReply(CommandSuggestionsS2CPacket packet, CallbackInfo ci) {
        // The detector ignores the first Netty invocation and consumes only its own client-thread replies.
        if (LimitlessClient.DONUT_DETECTOR.onSuggestions(packet)) ci.cancel();
    }
}
