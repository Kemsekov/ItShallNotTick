package dev.wuffs.itshallnottick.mixin;


import dev.wuffs.itshallnottick.Config;
import dev.wuffs.itshallnottick.EntityCpuTimeOptimizer;
import dev.wuffs.itshallnottick.TickOptimizer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Random;
import java.util.function.Consumer;


@Mixin(value = Level.class)
public abstract class EntityTickMixin {
    @Shadow
    @Final
    public Random random;
    EntityCpuTimeOptimizer entityCpuTimeOptimizer;

    public EntityTickMixin(){
        entityCpuTimeOptimizer = new EntityCpuTimeOptimizer();
        
        entityCpuTimeOptimizer.INTERVALS=Config.intervals.get();
        entityCpuTimeOptimizer.MAX_CPU_USAGE_PER_ENTITY_TYPE=Config.maxCpuUsagePerEntityType.get();
        entityCpuTimeOptimizer.TIME_INTERVAL_MS=Config.timeIntervalsMs.get();
        entityCpuTimeOptimizer.TPS_THRESHOLD=Config.tpsThreshold.get();

        entityCpuTimeOptimizer.startBackgroundTask();
    }
    /**
     * @reason tps
     * @author Team Deus Vult
     */
    @Overwrite
    public void guardEntityTick(Consumer<Entity> consumer, Entity entity) {
        
        Level level = ((Level) (Object) this);
        if(this.entityCpuTimeOptimizer.canTick(consumer, entity, level)){
            entityCpuTimeOptimizer.passTick(
                entity,
                ()->TickOptimizer.entityTicking(consumer, entity, level, random)
            );
        }
    }
}