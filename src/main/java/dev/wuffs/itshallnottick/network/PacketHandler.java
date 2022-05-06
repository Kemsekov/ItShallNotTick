package dev.wuffs.itshallnottick.network;

import dev.wuffs.itshallnottick.ItShallNotTick;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {

    private static final String PROTOCOL_VERSION = Integer.toString(1);
    private static short index = 0;

    public static final SimpleChannel HANDLER = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ItShallNotTick.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void sendToClient(Object msg, ServerPlayer player) {
        if (!(player instanceof FakePlayer))
            HANDLER.sendTo(msg, player.connection.getConnection(), NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void regsiter() {
        int id = 0;
        HANDLER.registerMessage(id, SendClaimedChunksPacket.class, SendClaimedChunksPacket::encode, SendClaimedChunksPacket::new, SendClaimedChunksPacket::handle);
    }
}
