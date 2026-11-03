package com.example.mod.mixin;

import net.minecraft.block.spawner.MobSpawnerEntry;
import net.minecraft.block.spawner.MobSpawnerLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MobSpawnerLogic.class)
public interface SpawnerLogicAccessor {
    @Accessor("spawnEntry")
    MobSpawnerEntry limitless$getSpawnEntry();

    @Accessor("requiredPlayerRange")
    int limitless$getRequiredPlayerRange();
}
