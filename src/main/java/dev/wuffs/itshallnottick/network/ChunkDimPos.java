package dev.wuffs.itshallnottick.network;

import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record ChunkDimPos(int x, int z, ResourceKey<Level> dim) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(x);
        buffer.writeInt(z);
        buffer.writeResourceLocation(dim.location());
    }

    public static ChunkDimPos decode(FriendlyByteBuf buffer) {
        return new ChunkDimPos(buffer.readInt(), buffer.readInt(), ResourceKey.create(Registry.DIMENSION_REGISTRY, buffer.readResourceLocation()));
    }

    public static ChunkDimPos fromLatsPos(dev.ftb.mods.ftblibrary.math.ChunkDimPos lats) {
        return new ChunkDimPos(lats.x, lats.z, lats.dimension);
    }
}
