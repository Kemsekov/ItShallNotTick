package dev.wuffs.itshallnottick.integration;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ftb.mods.ftbchunks.data.ClaimedChunk;
import dev.ftb.mods.ftbchunks.data.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.event.ClaimedChunkEvent;
import dev.ftb.mods.ftbteams.event.PlayerLoggedInAfterTeamEvent;
import dev.ftb.mods.ftbteams.event.TeamEvent;
import dev.wuffs.itshallnottick.client.ClaimedChunksClient;
import dev.wuffs.itshallnottick.network.ChunkDimPos;
import dev.wuffs.itshallnottick.network.PacketHandler;
import dev.wuffs.itshallnottick.network.SendClaimedChunksPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

public class FTBChunks {
    public static void setup() {
        ClaimedChunkEvent.AFTER_CLAIM.register(FTBChunks::onClaimChange);
        ClaimedChunkEvent.AFTER_UNCLAIM.register(FTBChunks::onClaimChange);

        TeamEvent.PLAYER_LOGGED_IN.register(FTBChunks::playerLogged);
    }

    private static void playerLogged(PlayerLoggedInAfterTeamEvent event) {
        PacketHandler.sendToClient(new SendClaimedChunksPacket(FTBChunksAPI.getManager().getAllClaimedChunks().stream().map(ClaimedChunk::getPos).map(ChunkDimPos::fromLatsPos).toList()), event.getPlayer());
    }

    private static void onClaimChange(CommandSourceStack commandSourceStack, ClaimedChunk claimedChunk) {
        PacketHandler.HANDLER.send(PacketDistributor.PLAYER.with(() -> {
            try {
                return commandSourceStack.getPlayerOrException();
            } catch (CommandSyntaxException e) {
                throw new RuntimeException(e);
            }
        }), new SendClaimedChunksPacket(FTBChunksAPI.getManager().getAllClaimedChunks().stream().map(ClaimedChunk::getPos).map(ChunkDimPos::fromLatsPos).toList()));
    }

    public static boolean isInClaimedChunk(Level level, BlockPos blockPos) {
        if (level.isClientSide) {
            return ClaimedChunksClient.getInstance().isChunkClaimed(new ChunkDimPos(blockPos.getX() >> 4, blockPos.getZ() >> 4, level.dimension()));
        }

        if (!FTBChunksAPI.isManagerLoaded()) {
            return false;
        }

        ClaimedChunk chunk = FTBChunksAPI.getManager().getChunk(new dev.ftb.mods.ftblibrary.math.ChunkDimPos(level, blockPos));
        return chunk != null;
    }
}
