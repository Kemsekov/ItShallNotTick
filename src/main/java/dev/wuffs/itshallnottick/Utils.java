package dev.wuffs.itshallnottick;

import dev.wuffs.itshallnottick.integration.FTBChunks;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public class Utils {

    public static boolean isInClaimedChunk(Level level, BlockPos blockPos) {
        if (ItShallNotTick.isFTBChunksLoaded) {
            return FTBChunks.isInClaimedChunk(level, blockPos);
        }

        return false;
    }

    public static boolean enoughPlayers(Level level) {
        if (level.isClientSide){
            return false;
        }
        MinecraftServer server = level.getServer();
        if (server != null) {
            return server.getPlayerList().getPlayerCount() >= Config.minPlayers.get();
        } else {
            return false;
        }
    }

    public static boolean isNearPlayer(Level level, BlockPos blockPos, int maxHeight, int maxDistanceSquare) {
        return isNearPlayerInternal(level, blockPos.getX(), blockPos.getY(), blockPos.getZ(), maxHeight, maxDistanceSquare, false);
    }

    private static boolean isNearPlayerInternal(Level world, double posx, double posy, double posz, int maxHeight, int maxDistanceSquare, boolean allowNullPlayers) {
        List<? extends Player> closest = world.players();

        for (Player player : closest) {
            if (player == null)
                return allowNullPlayers;

            if (Math.abs(player.getY() - posy) < maxHeight) {
                double x = player.getX() - posx;
                double z = player.getZ() - posz;


                boolean nearPlayer = x * x + z * z < maxDistanceSquare;
//                System.out.printf("D: %d, XZ: %d %n",maxDistanceSquare, Math.round(x * x + z * z));

                if (nearPlayer) {
//                    System.out.printf("X: %d, Z: %d,XX: %d, ZZ %d, D: %d, XZ: %d %n", Math.round(x), Math.round(z),Math.round(x * x), Math.round(z * z),maxDistanceSquare, Math.round(x * x + z * z));
                    return true;
                }
            }
        }

        return false;

    }


    public static boolean isEntityWithinDistance(Entity player, Entity entity, int maxHeight, int maxDistanceSquare) {
        if (Math.abs(player.getY() - entity.getY()) < maxHeight) {
            double x = player.getX() - entity.getX();
            double z = player.getZ() - entity.getZ();

            return x * x + z * z < maxDistanceSquare;
        }

        return false;
    }

    public static boolean isEntityWithinDistance(BlockPos player, Vec3 entity, int maxHeight, int maxDistanceSquare) {
        if (Math.abs(player.getY() - entity.y) < maxHeight) {
            double x = player.getX() - entity.x;
            double z = player.getZ() - entity.z;

            return x * x + z * z < maxDistanceSquare;
        }

        return false;
    }

    public static boolean isEntityWithinDistance(Entity player, double cameraX, double cameraY, double cameraZ, int maxHeight, int maxDistanceSquare) {
        if (Math.abs(player.getY() - cameraY) < maxHeight) {
            double x = player.getX() - cameraX;
            double z = player.getZ() - cameraZ;

//            System.out.printf("D: %d, XZ: %d %n",maxDistanceSquare, Math.round(x * x + z * z));
            return x * x + z * z < maxDistanceSquare;
        }

        return false;
    }

    public static Object2BooleanMap<EntityType<?>> isIgnored = new Object2BooleanOpenHashMap<>();

    public static boolean isIgnoredEntity(Entity entity) {
        if (Config.entityIgnoreList.get().isEmpty()) {
            return false;
        }

        var entityType = entity.getType();
        return isIgnored.computeIfAbsent(entityType, (et) -> {
            var entityRegName = ForgeRegistries.ENTITY_TYPES.getKey(entityType).toString();
            if (entityRegName == null) {
                return false;
            }

            var ignored = false;
            if (!Config.entityResources.isEmpty()) {
                ignored = Config.entityResources.contains(entityRegName);
            }

            if (!Config.entityWildcards.isEmpty() && !ignored) {
                ignored = Config.entityWildcards.stream().anyMatch(e -> entityRegName.toString().startsWith(e));
            }

            if (!Config.entityTagKeys.isEmpty() && !ignored) {
                ignored = Config.entityTagKeys.stream().anyMatch(entityType::is);
            }

            return ignored;
        });
    }
}
