package dev.wuffs.itshallnottick.Entity;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
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

    public static boolean noCollision(Level level, Entity entity, AABB aabb){
        try {
            return itemCollisionCache.get(entity.getUUID(), () -> {
//                System.out.printf("Item collision cache%n");
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
            });
        } catch (ExecutionException e) {
            itemCollisionCache.invalidate(entity.getUUID());
            return false;
        }
    }
}
