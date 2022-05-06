package dev.wuffs.itshallnottick.network;

import com.google.common.collect.Lists;
import dev.wuffs.itshallnottick.client.ClaimedChunksClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public class SendClaimedChunksPacket {

    private List<ChunkDimPos> claimedChunks;

    public SendClaimedChunksPacket(List<ChunkDimPos> claimedChunks){
        this.claimedChunks = claimedChunks;
    }

    public SendClaimedChunksPacket(FriendlyByteBuf buf) {
        this.claimedChunks = buf.readCollection(Lists::newArrayListWithCapacity, ChunkDimPos::decode);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(this.claimedChunks, (buffer, e) -> e.encode(buffer));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx){
        // Something something need to update a cache I guess?
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
                return;
            }

            ClaimedChunksClient.getInstance().updateChunks(this.claimedChunks);
        });
        ctx.get().setPacketHandled(true);
    }

}
