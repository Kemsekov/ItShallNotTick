package dev.wuffs.itshallnottick.mixin;


import dev.wuffs.itshallnottick.Config;
import dev.wuffs.itshallnottick.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.Random;

@Mixin(NaturalSpawner.class)
public class WorldEntitySpawnerOptimizationMixin {
    @Inject(at = @At("HEAD"), method = "getRandomSpawnMobAt", cancellable = true)
    private static void getRandomSpawnMobAt(ServerLevel level, StructureFeatureManager arg2, ChunkGenerator arg3, MobCategory arg4, Random random, BlockPos blockPos, CallbackInfoReturnable<Optional<MobSpawnSettings.SpawnerData>> cir) {
        if (!Utils.isInClaimedChunk(level, blockPos) && Utils.enoughPlayers(level)) {
            int maxHeight = Config.maxEntitySpawnDistanceVertical.get();
            int maxDistanceSquare = Config.maxEntitySpawnDistanceHorizontal.get();

            if (!Utils.isNearPlayer(level, blockPos, maxHeight, maxDistanceSquare)) {
                cir.setReturnValue(Optional.empty());
            }
        }
    }
}
