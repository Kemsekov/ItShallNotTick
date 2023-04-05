package dev.wuffs.itshallnottick;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
// Abstract:
// we accept Zombie entity for ticking, we check if server can't keep up,
// if it is, we get percent of cpu usage for Zombie-type entities from last N time-intervals.
// if this percent of cpu usage is bigger than some threshold, we just skip this entity ticking.

// create a map for each entity type, which contains a cpu-time for last N
// compute time intervals. In this explanation compute time interval is second.

// For example there is callings for entities to tick, we record time spent for
// ticking for each entity type and once a second, we shifts a table of previous records
// of entity type cpu-time usage with new value.

// Example: in table we have pair: Zombie:100001
// so in last second total cpu-time of ticking for all zombies is 100001 nanoseconds.

// As second passed we update another table, where stored values of cpu-time of
// each entity type from previous time-intervals of
// server running.


// If we had a restriction to remember only last three seconds, then:
// If we had Zombie:[100000,200000,300000] in last three seconds
// We update this list to following:
// Zombie:[200000,300000,100001] <- so we basically shifted old values 
// and added new one, created statistics of cpu-time usage 
// for each entity type for some period of time!
// If we would like to know TotalTimeFromLastIntervals we would then sub removed value from it
// We removed 100000 from Zombie cpu-time list, so TotalTimeFromLastIntervals-=100000
// and as we added 100001 to list, TotalTimeFromLastIntervals+=100001

// So if we keep total time spent for all entities ticking for last N
// time-intervals, we can easily compute percentage of cpu-time usage for each entity type.

// SumOfLastIntervals(entityType)/TotalTimeFromLastIntervals = percent of cpu usage for entity
// And if our server is under heavy load and can't keep up, we can balance out
// how many ticks for each entity we need to keep in order to reduce lag.

// P.S
// If in the last second there were no calling over entity(for example entity Zombie)
// then it's value in table of cpu-time usage is gonna be Zombie:0
// in that case we just remove this key-value from table to reduce ram usage.


public class EntityCpuTimeOptimizer {
    class EntityCpuUsageData{
        Long[] LastNTimeIntervals;
        Long TickCpuTime;
        float CpuUsagePercentage;
    }
    public interface Action{
        void call();
    }
    /**
     * keeps total cpu time in last intervals
     */
    AtomicLong TOTAL_TIME_IN_LAST_INTERVALS=new AtomicLong(0);

    boolean SERVER_OVERLOADED=false;

    /**
     * After server TPS goes below this value EntityCpuTimeOptimizer start to work
     */
    public float TPS_THRESHOLD=15;

    /**
     * How long to collect statistics for single time interval. By default is one second.
     */
    public long TIME_INTERVAL_MS=1000;

    /**
     * How many time intervals to store for statistics
     */
    public int INTERVALS=4;

    /**
     * How much cpu-time each entity of some type can use when server is overloaded. In percents [0;1]
     */
    public float MAX_CPU_USAGE_PER_ENTITY_TYPE=0.2f;

    ConcurrentHashMap<Object, EntityCpuUsageData> entityCpuUsage;

    Thread backgroundTasks;
    
    public EntityCpuTimeOptimizer(){
        this.entityCpuUsage = new ConcurrentHashMap<Object,EntityCpuUsageData>();

        this.backgroundTasks = new Thread(()->{
            while(true)
            try{
                Thread.sleep(TIME_INTERVAL_MS);
                shiftTimeIntervals();
                SERVER_OVERLOADED=serverOverloaded(ServerLifecycleHooks.getCurrentServer());
            }
            catch(Exception e){
                
            }
        });
    }

    public void startBackgroundTask(){
        backgroundTasks.start();
    }

    void shiftTimeIntervals(){
        for(var key : entityCpuUsage.keySet()){
            var currentEntityCpuUsage = entityCpuUsage.get(key);
            var cpuTime = currentEntityCpuUsage.TickCpuTime;
            if(cpuTime==0){
                entityCpuUsage.remove(key);
                continue;
            }
            currentEntityCpuUsage.TickCpuTime=0l;
            var intervals=currentEntityCpuUsage.LastNTimeIntervals;
            var toShift=intervals[0];
            
            System.arraycopy(intervals, 1, intervals, 0, intervals.length-1);
            intervals[intervals.length-1]=cpuTime;

            TOTAL_TIME_IN_LAST_INTERVALS.addAndGet(cpuTime-toShift);
        }
        for(var key : entityCpuUsage.keySet()){
            var currentEntityCpuUsage = entityCpuUsage.get(key);
            currentEntityCpuUsage.CpuUsagePercentage = computePercentOfCpuUsage(currentEntityCpuUsage);
        }
    }

    /**
     * @return true if server can't keep up -> server tps is bigger than TPS_THRESHOLD
     */
    public boolean serverOverloaded(MinecraftServer server) {
        if(server == null) return false;
        double tickTime = 0;
        //if in any of the world levels tickrate goes above specified THRESHOLD
        for (var key : server.getAllLevels()) {
            var maxTickTime=Arrays.stream(server.getTickTime(key.dimension())).max().getAsLong();
            tickTime = Math.max(tickTime,maxTickTime);
        }
        var tps = 1000000000.0 / tickTime;
        return tps<=TPS_THRESHOLD;
    }

    float computePercentOfCpuUsage(EntityCpuUsageData cpuUsageData){
        var cpuUsageOverLastIntervals = Arrays.stream(cpuUsageData.LastNTimeIntervals).reduce(0L, (a,b)->a+b);
        return cpuUsageOverLastIntervals*1.0f/TOTAL_TIME_IN_LAST_INTERVALS.get();
    }

    /**
     * Determines if given entity can tick by storing used cpu-time statistics.
     * Simply it passes all entities which does not overload server and stop those entities which
     * causes a lot of lag.
     */
    public boolean canTick(Consumer<Entity> consumer, Entity entity, Level level) {
        if(!SERVER_OVERLOADED) return true;

        var entityType = entity.getType();
        var cpuUsage=entityCpuUsage.get(entityType);
        return cpuUsage.CpuUsagePercentage<=MAX_CPU_USAGE_PER_ENTITY_TYPE;
    }
    EntityCpuUsageData _default(){
        var value = new EntityCpuUsageData();
        value.CpuUsagePercentage=0;
        value.LastNTimeIntervals=new Long[this.INTERVALS];
        value.TickCpuTime=0l;
        return value;
    }
    /**
     * We wrap tick passes and measure time entity spent on ticking
     */
    public void passTick(Entity entity,Action tick) {
        var entityType = entity.getType();
        long startTime = System.nanoTime();
        tick.call();
        var currentEntityCpuUsage = entityCpuUsage.getOrDefault(entityType, _default());
        long oldTime = currentEntityCpuUsage.TickCpuTime;
        
        currentEntityCpuUsage.TickCpuTime=oldTime+startTime-System.nanoTime();
    }

}
