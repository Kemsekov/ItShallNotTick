package dev.wuffs.itshallnottick.Client;

import dev.wuffs.itshallnottick.Config;
import dev.wuffs.itshallnottick.Utils;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class EntityRenderDispatcher {

    public static boolean shouldDoRender(Entity entity, Frustum clippingHelper, double cameraX, double cameraY, double cameraZ) {
        if (!(entity instanceof Player)) {
            System.out.printf("Is within distance: %b%n", Utils.isEntityWithinDistance(entity, cameraX, cameraY, cameraZ, Config.maxEntityTickDistanceVertical.get(), Config.maxEntityTickDistanceHorizontal.get()));
            System.out.printf("Is ignored: %b%n", Utils.isIgnoredEntity(entity));
            System.out.printf("Is in claim: %b%n", Utils.isInClaimedChunk(entity.level, entity.blockPosition()));
            //System.out.printf("Should I Render: %s%n", entity.getType().getRegistryName());


            if (Utils.isIgnoredEntity(entity) || Utils.isEntityWithinDistance(entity, cameraX, cameraY, cameraZ, Config.maxEntityTickDistanceVertical.get(), Config.maxEntityTickDistanceHorizontal.get()) || Utils.isInClaimedChunk(entity.level, entity.blockPosition())) {
                System.out.println("Should render");
                return true;
            }
            System.out.println("Should not render");
        }
        return false;
    }
}
