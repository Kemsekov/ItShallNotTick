package dev.wuffs.itshallnottick.integration;

import dev.ftb.mods.ftbchunks.data.ClaimedChunk;
import dev.ftb.mods.ftbchunks.data.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class FTBChunks {
    public static boolean isInClaimedChunk(Level level, BlockPos blockPos) {
        if (!FTBChunksAPI.isManagerLoaded()) {
            return false;
        }

        ClaimedChunk chunk = FTBChunksAPI.getManager().getChunk(new ChunkDimPos(level, blockPos));
        return chunk != null;
    }
}
