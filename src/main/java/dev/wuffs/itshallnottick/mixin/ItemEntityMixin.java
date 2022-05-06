package dev.wuffs.itshallnottick.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemEntity.class)
public class ItemEntityMixin {

    @Redirect(method = "Lnet/minecraft/world/entity/item/ItemEntity;tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean collision(Level level, Entity entity, AABB aabb) {
      return dev.wuffs.itshallnottick.entity.ItemEntity.noCollision(level, ((Entity) (Object)this), ((Entity) (Object)this).getBoundingBox().deflate(1.0E-7));
    }
}
