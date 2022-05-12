package dev.wuffs.itshallnottick.client;

import dev.wuffs.itshallnottick.Config;
import dev.wuffs.itshallnottick.Utils;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;

public class EntityRenderDispatcher {

    public static boolean shouldDoRender(Entity entity, Frustum clippingHelper, double cameraX, double cameraY, double cameraZ) {
        if (!Utils.enoughPlayers(entity.getLevel()) ||  Utils.isIgnoredEntity(entity) || Utils.isEntityWithinDistance(entity, cameraX, cameraY, cameraZ, Config.maxEntityTickDistanceVertical.get(), Config.maxEntityTickDistanceHorizontal.get()) || Utils.isInClaimedChunk(entity.level, entity.blockPosition())) {
            return true;
        }
        return false;
    }
}