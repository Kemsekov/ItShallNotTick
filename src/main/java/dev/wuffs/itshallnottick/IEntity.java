package dev.wuffs.itshallnottick;

import net.minecraft.core.BlockPos;

public interface IEntity {

    BlockPos getLastPosition();
    boolean getIsIdle();
}
