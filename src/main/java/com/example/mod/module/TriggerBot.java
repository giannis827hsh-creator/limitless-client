package com.example.mod.module;

import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.IntegerSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;

/** Attacks only a valid target under the player's real crosshair. */
public final class TriggerBot extends Module {
    private final IntegerSetting reaction = addSetting(new IntegerSetting("Reaction ticks", 3, 0, 10));
    private final BooleanSetting players = addSetting(new BooleanSetting("Players", true));
    private final BooleanSetting hostiles = addSetting(new BooleanSetting("Hostile mobs", false));
    private final BooleanSetting weaponsOnly = addSetting(new BooleanSetting("Weapons only", true));
    private final BooleanSetting includeInvisible = addSetting(new BooleanSetting("Include invisible", false));
    private int seenId = -1, ticksSeen;

    public TriggerBot() {
        super("Trigger Bot", false, Category.COMBAT);
        reaction.describe("Ticks to wait after the crosshair reaches a new target.");
        players.describe("Attack players under the crosshair.");
        hostiles.describe("Attack hostile mobs under the crosshair.");
        weaponsOnly.describe("Require a sword or axe in the main hand.");
        includeInvisible.describe("Allow attacks on invisible entities sent by the server.");
    }

    @Override public void onWorldLeave() { seenId = -1; ticksSeen = 0; }

    public void tick(MinecraftClient client) {
        if (!isEnabled() || !ActionSupport.ready(client)
                || !(client.crosshairTarget instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity target) || !target.isAlive()
                || target == client.player || !target.isAttackable() || client.player.isUsingItem()
                || target.isInvisible() && !includeInvisible.get()
                || target instanceof PlayerEntity && !players.get()
                || !(target instanceof PlayerEntity) && (!(target instanceof HostileEntity) || !hostiles.get())
                || weaponsOnly.get() && !client.player.getMainHandStack().isIn(ItemTags.SWORDS)
                && !client.player.getMainHandStack().isIn(ItemTags.AXES)) {
            seenId = -1; ticksSeen = 0; return;
        }
        if (target.getId() != seenId) { seenId = target.getId(); ticksSeen = 0; }
        if (++ticksSeen <= reaction.get() || client.player.getAttackCooldownProgress(0.0f) < 0.99f) return;
        client.interactionManager.attackEntity(client.player, target);
        client.player.swingHand(Hand.MAIN_HAND);
        ticksSeen = 0;
    }
}
