package dev.wuffs.itshallnottick.mixin;

import dev.wuffs.itshallnottick.EntityCpuTimeOptimizer;
import dev.wuffs.itshallnottick.TickOptimizer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.HashMap;
import java.util.Random;
import java.util.function.Consumer;

@Mixin(value = Level.class)
public abstract class EntityTickMixin {
    @Shadow
    @Final
    public Random random;
    HashMap<Level,EntityCpuTimeOptimizer> entityCpuTimeOptimizers;
    public EntityTickMixin() {
        Init();
    }

    void Init() {
        if (entityCpuTimeOptimizers != null)
            return;
        entityCpuTimeOptimizers = new HashMap<>();
    }
    EntityCpuTimeOptimizer getOptimizer(Level level){
        var optimizer = entityCpuTimeOptimizers.get(level);
        if(optimizer==null){
            optimizer = new EntityCpuTimeOptimizer(level);
            entityCpuTimeOptimizers.put(level, optimizer);
        }
        return optimizer;
    }
    
    /**
     * @reason tps
     * @author Team Deus Vult
     */
    @Overwrite
    public void guardEntityTick(Consumer<Entity> consumer, Entity entity) {
        Init();
        Level level = ((Level) (Object) this);
        getOptimizer(level).passTick(
                entity,
                (e) -> TickOptimizer.entityTicking(consumer, entity, level, random));
    }
}