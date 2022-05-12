package dev.wuffs.itshallnottick.entity;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.wuffs.itshallnottick.Utils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class ItemEntity {

        private static final Cache<UUID, Boolean> itemCollisionCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(1))
            .build();

    public static boolean isGrounded(Level level, Entity entity, AABB aabb){
        if (entity.isOnGround() && Utils.enoughPlayers(level)){
            try {
                return itemCollisionCache.get(entity.getUUID(), () -> noCollision(level, entity, aabb));
            } catch (ExecutionException e) {
                itemCollisionCache.invalidate(entity.getUUID());
                return false;
            }
        }else{
            return noCollision(level, entity, aabb);
        }
    }

    private static boolean noCollision(Level level, Entity entity, AABB aabb){
        for (VoxelShape voxelShape : level.getBlockCollisions(entity, aabb)) {
            if (voxelShape.isEmpty()) continue;
            return false;
        }
        if (!level.getEntityCollisions(entity, aabb).isEmpty()) {
            return false;
        }
        if (entity != null) {
            WorldBorder worldBorder = level.getWorldBorder();
            VoxelShape borderCollision = worldBorder.isInsideCloseToBorder(entity, aabb) ? worldBorder.getCollisionShape() : null;
            return borderCollision == null || !Shapes.joinIsNotEmpty(borderCollision, Shapes.create(aabb), BooleanOp.AND);
        }
        return true;
    }
}
