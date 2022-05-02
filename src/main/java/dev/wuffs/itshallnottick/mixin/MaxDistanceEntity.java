package dev.wuffs.itshallnottick.mixin;


import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class MaxDistanceEntity {
    @Inject(at = @At("HEAD"), method = "shouldRender", cancellable = true)
    public <E extends Entity> void shouldDoRender(E entity, Frustum clippingHelper, double cameraX, double cameraY, double cameraZ, CallbackInfoReturnable<Boolean> cir) {
//        if (!Utils.isEntityWithinDistance(entity, cameraX, cameraY, cameraZ, Config.maxEntityTickDistanceVertical.get(), Config.maxEntityTickDistanceHorizontal.get()) && !Utils.isInClaimedChunk(entity.level, entity.blockPosition()) && !Utils.isIgnoredEntity(entity)) {
//            cir.cancel();
//        }

        if (!dev.wuffs.itshallnottick.Client.EntityRenderDispatcher.shouldDoRender(entity, clippingHelper, cameraX, cameraY,cameraZ)){
            cir.cancel();
        }
    }
}

