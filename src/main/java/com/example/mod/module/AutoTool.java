package com.example.mod.module;

import com.example.mod.setting.BooleanSetting;
import java.text.Normalizer;
import java.util.Locale;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

public final class AutoTool extends Module {
    private final BooleanSetting switchBack = addSetting(new BooleanSetting("Switch back", true));
    private final BooleanSetting drillInstabreak = addSetting(new BooleanSetting("Drill Instabreak", true));
    private final BooleanSetting delaySwitch = addSetting(new BooleanSetting("Delay", true));

    private BlockPos target;
    private int pending = -1;
    private int delay;
    private int original = -1;
    private int chosen = -1;
    private int returnDelay;

    public AutoTool() {
        super("Auto Tool", false, Category.MISC);
        switchBack.describe("Return to your previous hotbar slot when mining stops.");
        drillInstabreak.describe("Use a netherite pickaxe when it can break the block in one hit.");
        delaySwitch.describe("Wait two ticks before changing to a better tool or switching back.");
    }

    @Override protected void onEnable() { reset(); }

    @Override protected void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (switchBack.get() && client.player != null && original >= 0
                && client.player.getInventory().getSelectedSlot() == chosen)
            client.player.getInventory().setSelectedSlot(original);
        reset();
    }

    @Override public void onWorldLeave() { reset(); }

    public void tick(MinecraftClient client) {
        if (!isEnabled() || client.player == null || client.world == null) {
            reset();
            return;
        }

        int selected = client.player.getInventory().getSelectedSlot();
        if (original >= 0 && selected != chosen) {
            original = -1;
            chosen = -1;
        }

        BlockPos aimed = ActionSupport.ready(client)
                && ActionSupport.physicallyDown(client, client.options.attackKey)
                && client.crosshairTarget instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
        BlockState state = aimed == null ? null : client.world.getBlockState(aimed);
        if (state == null || state.isAir() || state.getHardness(client.world, aimed) < 0) {
            target = null;
            pending = -1;
            delay = 0;
            giveBack(client);
            return;
        }

        if (!aimed.equals(target)) {
            target = aimed.toImmutable();
            pending = -1;
            delay = 0;
        }

        int shard = shardSlot(client, state, selected);
        if (drillInstabreak.get()) {
            int drill = drillSlot(client, state, aimed, shard);
            if (drill >= 0) {
                if (drill != selected) select(client, drill);
                pending = -1;
                return;
            }
        }

        int best = bestSlot(client, state, aimed, selected, shard);
        if (best == selected) {
            pending = -1;
            return;
        }
        if (pending != best) {
            pending = best;
            delay = switchDelay();
        }
        if (delay > 0) delay--;
        else {
            select(client, best);
            pending = -1;
        }
    }

    private static int drillSlot(MinecraftClient client, BlockState state, BlockPos pos, int shard) {
        if (shard >= 0 && breakingDelta(client, shard, state, pos) >= 1.0f) return shard;
        for (int slot = 0; slot < 9; slot++) {
            if (isDrill(client.player.getInventory().getStack(slot))
                    && breakingDelta(client, slot, state, pos) >= 1.0f) return slot;
        }
        return -1;
    }

    private static boolean isDrill(ItemStack stack) {
        return stack.isOf(Items.NETHERITE_PICKAXE) && !stack.willBreakNextUse();
    }

    private int bestSlot(MinecraftClient client, BlockState state, BlockPos pos, int selected, int shard) {
        float bestDelta = selected == chosen && original >= 0
                && client.player.getInventory().getStack(selected).willBreakNextUse()
                ? -1.0f : breakingDelta(client, selected, state, pos);
        int best = selected;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            if (slot == selected || stack.isEmpty() || stack.willBreakNextUse()) continue;
            float delta = breakingDelta(client, slot, state, pos);
            if (delta > bestDelta) {
                bestDelta = delta;
                best = slot;
            }
        }
        if (shard >= 0 && client.player.getInventory().getStack(best).isIn(ItemTags.PICKAXES)) return shard;
        return best;
    }

    private static int shardSlot(MinecraftClient client, BlockState state, int selected) {
        if (isShardPickaxe(client.player.getInventory().getStack(selected), state)) return selected;
        for (int slot = 0; slot < 9; slot++) {
            if (slot != selected && isShardPickaxe(client.player.getInventory().getStack(slot), state)) return slot;
        }
        return -1;
    }

    private static boolean isShardPickaxe(ItemStack stack, BlockState state) {
        if (stack.isEmpty() || stack.willBreakNextUse() || !stack.isSuitableFor(state)
                || !stack.isIn(ItemTags.PICKAXES)) return false;
        if (normalize(stack.getName().getString()).contains("shard pickaxe")) return true;
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return false;
        return lore.lines().stream().anyMatch(line -> normalize(line.getString()).contains("breaks 9 blocks at once"));
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT)
                .replaceAll("\\p{M}+", "").replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static float breakingDelta(MinecraftClient client, int slot, BlockState state, BlockPos pos) {
        float hardness = state.getHardness(client.world, pos);
        if (hardness <= 0.0f) return 0.0f;

        ClientPlayerEntity player = client.player;
        ItemStack stack = player.getInventory().getStack(slot);
        float speed = stack.getMiningSpeedMultiplier(state);
        if (speed > 1.0f) {
            double[] efficiency = {0.0};
            stack.applyAttributeModifier(AttributeModifierSlot.MAINHAND, (attribute, modifier, display) -> {
                if (attribute == EntityAttributes.MINING_EFFICIENCY) efficiency[0] += modifier.value();
            });
            speed += (float) efficiency[0];
        }
        if (StatusEffectUtil.hasHaste(player))
            speed *= 1.0f + (StatusEffectUtil.getHasteAmplifier(player) + 1) * 0.2f;
        StatusEffectInstance fatigue = player.getStatusEffect(StatusEffects.MINING_FATIGUE);
        if (fatigue != null) speed *= switch (fatigue.getAmplifier()) {
            case 0 -> 0.3f;
            case 1 -> 0.09f;
            case 2 -> 0.0027f;
            default -> 0.00081f;
        };
        speed *= (float) player.getAttributeValue(EntityAttributes.BLOCK_BREAK_SPEED);
        if (player.isSubmergedIn(FluidTags.WATER))
            speed *= (float) player.getAttributeValue(EntityAttributes.SUBMERGED_MINING_SPEED);
        if (!player.isOnGround()) speed /= 5.0f;
        boolean suitable = !state.isToolRequired() || stack.isSuitableFor(state);
        return speed / hardness / (suitable ? 30.0f : 100.0f);
    }

    private void select(MinecraftClient client, int slot) {
        if (original < 0) original = client.player.getInventory().getSelectedSlot();
        client.player.getInventory().setSelectedSlot(slot);
        chosen = slot;
        returnDelay = switchDelay();
    }

    private int switchDelay() { return delaySwitch.get() ? 2 : 0; }

    private void giveBack(MinecraftClient client) {
        if (original < 0) return;
        if (!switchBack.get()) {
            original = chosen = -1;
            return;
        }
        if (returnDelay > 0) {
            returnDelay--;
            return;
        }
        if (client.player.getInventory().getSelectedSlot() == chosen)
            client.player.getInventory().setSelectedSlot(original);
        original = chosen = -1;
    }

    private void reset() {
        target = null;
        pending = -1;
        delay = 0;
        original = chosen = -1;
        returnDelay = 0;
    }
}
