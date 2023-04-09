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
        // RunUnusedOptimizerRemoval();
    }
    EntityCpuTimeOptimizer getOptimizer(Level level){
        var optimizer = entityCpuTimeOptimizers.get(level);
        if(optimizer==null){
            optimizer = EntityCpuTimeOptimizer.Create(level);
            entityCpuTimeOptimizers.put(level, optimizer);
        }
        return optimizer;
    }

    /**
     * If some of optimizers does nothing, we need to remove them
     */
    void RunUnusedOptimizerRemoval(){
        var backgroundTask = new Thread(()->{
            while(true)
            try{
                Thread.sleep(Config.timeIntervalsMs.get()*4);
                for(var key : entityCpuTimeOptimizers.keySet()){
                    var currentOptimizer = entityCpuTimeOptimizers.get(key);
                    if(currentOptimizer.getAverageTotalCpuTime()==0)
                        entityCpuTimeOptimizers.remove(key);
                }
            }
            catch(Exception e){

            }
        });
        backgroundTask.start();
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
                () -> TickOptimizer.entityTicking(consumer, entity, level, random));
    }
}