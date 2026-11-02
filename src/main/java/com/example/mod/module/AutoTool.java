package com.example.mod.module;

import com.example.mod.setting.BooleanSetting;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;

/** Picks the fastest suitable hotbar tool while the player is mining. */
public final class AutoTool extends Module {
    private final BooleanSetting switchBack = addSetting(new BooleanSetting("Switch back", true));
    private int original = -1, chosen = -1;
    private net.minecraft.util.math.BlockPos target;
    private int targetTicks;

    public AutoTool() {
        super("Auto Tool", false, Category.COMBAT);
        switchBack.describe("Return to your previous hotbar slot when mining stops.");
    }

    @Override protected void onDisable() { release(MinecraftClient.getInstance()); }
    @Override public void onWorldLeave() { release(MinecraftClient.getInstance()); }

    public void tick(MinecraftClient client) {
        if (!isEnabled() || !ActionSupport.ready(client)
                || !ActionSupport.physicallyDown(client, client.options.attackKey)
                || !(client.crosshairTarget instanceof BlockHitResult hit)) {
            release(client); return;
        }
        BlockState state = client.world.getBlockState(hit.getBlockPos());
        if (state.isAir() || state.getHardness(client.world, hit.getBlockPos()) < 0) { release(client); return; }
        if (!hit.getBlockPos().equals(target)) { target = hit.getBlockPos().toImmutable(); targetTicks = 0; }
        if (++targetTicks < 2) return;
        int selected = client.player.getInventory().getSelectedSlot();
        if (original >= 0 && selected != chosen) original = chosen = -1;
        int best = selected;
        float bestSpeed = miningScore(client.player.getInventory().getStack(selected), state);
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            float speed = miningScore(stack, state);
            if (speed > bestSpeed) { bestSpeed = speed; best = i; }
        }
        if (best != selected && bestSpeed > 1.0f) {
            if (original < 0) original = selected;
            client.player.getInventory().setSelectedSlot(best);
            chosen = best;
        }
    }

    private static float miningScore(ItemStack stack, BlockState state) {
        if (stack.isEmpty() || stack.isDamageable() && stack.getDamage() >= stack.getMaxDamage() - 1)
            return 1.0f;
        float speed = stack.getMiningSpeedMultiplier(state);
        return stack.isSuitableFor(state) ? speed + 0.01f : speed;
    }

    private void release(MinecraftClient client) {
        if (switchBack.get() && original >= 0 && client.player != null
                && client.player.getInventory().getSelectedSlot() == chosen)
            client.player.getInventory().setSelectedSlot(original);
        original = chosen = -1; target = null; targetTicks = 0;
    }
}
