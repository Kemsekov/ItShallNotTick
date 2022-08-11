package dev.wuffs.itshallnottick.mixin;


import net.minecraft.entity.TickOptimizer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Consumer;


@Mixin(value = Level.class)
public abstract class EntityTickMixin {
    @Shadow
    @Final
    public RandomSource random;

    /**
     * @reason tps
     * @author Team Deus Vult
     */
    @Overwrite
    public void guardEntityTick(Consumer<Entity> consumer, Entity entity) {
        Level level = ((Level) (Object) this);
        TickOptimizer.entityTicking(consumer, entity, level, random);
    }
}