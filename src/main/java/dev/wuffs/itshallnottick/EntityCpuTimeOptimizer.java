package dev.wuffs.itshallnottick;

import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.world.entity.LivingEntity;

import java.util.Arrays;
import java.util.Random;

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

public class EntityCpuTimeOptimizer {
    class EntityCpuUsageData {
        Long TickCpuTime = 0l;
        Long TotalCpuTimeInLastIntervals = 0l;

        public EntityCpuUsageData(int INTERVALS) {
        }

    }

    public interface Action {
        void call();
    }

    /**
     * keeps total cpu time in last intervals
     */
    Long[] LAST_INTERVALS_CPU_TIME;

    boolean SERVER_OVERLOADED = false;

    /**
     * After server TPS goes below this value EntityCpuTimeOptimizer start to work
     */
    public final Integer TPS_THRESHOLD;

    /**
     * How long to collect statistics for single time interval. By default is one
     * second.
     */
    public final long TIME_INTERVAL_MS;

    /**
     * How many time intervals to store for statistics
     */
    public final int INTERVALS;

    /**
     * How much cpu-time each entity of some type can use when server is overloaded.
     * In percents [0;1]
     */
    public float MAX_CPU_USAGE_PER_ENTITY_TYPE = 0.2f;
    public Random Rand;

    ConcurrentHashMap<Object, EntityCpuUsageData> entityCpuUsage;

    Thread backgroundTasks;

    boolean runningBackgroundTask = false;

    MinecraftServer server;

    Level level;

    Long TOTAL_CPU_TIME = 1l;

    Thread logThread;

    public EntityCpuTimeOptimizer(Level level) {

        this.INTERVALS = Config.intervals.get();
        this.MAX_CPU_USAGE_PER_ENTITY_TYPE = Config.maxCpuUsagePerEntityType.get();
        this.TIME_INTERVAL_MS = Config.timeIntervalsMs.get();
        this.TPS_THRESHOLD = Config.tpsThreshold.get();
        
        this.level = level;
        this.LAST_INTERVALS_CPU_TIME = new Long[INTERVALS];
        Arrays.fill(LAST_INTERVALS_CPU_TIME, 0l);
        this.Rand = new Random();
        this.entityCpuUsage = new ConcurrentHashMap<Object, EntityCpuUsageData>();
        this.server = level.getServer();
        startBackgroundTasks();
        
    }
    public void startBackgroundTasks(){
        startStatisticsCollection();
        startLoggingDebugInfo();
    }
    void startStatisticsCollection(){
        if(this.backgroundTasks!=null && this.backgroundTasks.isAlive()) return;
        this.backgroundTasks = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(TIME_INTERVAL_MS);
                    SERVER_OVERLOADED = isServerOverloaded();
                    if (!SERVER_OVERLOADED)
                        continue;
                    this.runningBackgroundTask = true;
                    shiftTimeIntervals();
                    updateCpuUsage();
                    this.runningBackgroundTask = false;
                } catch (Exception e) {
                }
            }
        });
        this.backgroundTasks.start();
    }
    void startLoggingDebugInfo() {
        if(!Config.logDebugInfo.get()) return;
        if(this.logThread!=null && this.logThread.isAlive()) return;
        this.logThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(3000);
                    logDebugInfo();
                } catch (Exception e) {

                }
            }
        });
        this.logThread.start();
    }

    /**
     * @return total cpu time entities used on the level this optimizer is
     *         created on for the last N time intervals
     */
    public Long getTotalCpuTime() {
        return TOTAL_CPU_TIME;
    }

    void broadcastToAllPlayers(String message) {
        Utils.broadcastToAllPlayers(message, server);
    }

    void logDebugInfo() {
        var divisor = 1000000;
        broadcastToAllPlayers("Level is " + level);
        broadcastToAllPlayers("TPS is " + Utils.getTps(server, level));
        broadcastToAllPlayers("Is optimizer running " + SERVER_OVERLOADED);
        broadcastToAllPlayers("Total cpu time " + TOTAL_CPU_TIME / divisor);
        broadcastToAllPlayers("Keys " + entityCpuUsage.keySet().size());
        Object maxLoadEntity = null;
        EntityCpuUsageData cpuUsage = null;
        var totalCpuUsagePercents = 0.0;
        for (var key : entityCpuUsage.keySet()) {
            var load = entityCpuUsage.get(key);
            if (cpuUsage != null)
                totalCpuUsagePercents += cpuUsage.TickCpuTime;
            if (cpuUsage == null || cpuUsage.TickCpuTime < load.TickCpuTime) {
                cpuUsage = load;
                maxLoadEntity = key;
            }
        }
        totalCpuUsagePercents/=TOTAL_CPU_TIME;
        if (cpuUsage == null)
            return;
        broadcastToAllPlayers("Max load entity is " + maxLoadEntity);
        // broadcastToAllPlayers("Cpu % " + cpuUsage.CpuUsagePercentage);
        broadcastToAllPlayers("Takes " + cpuUsage.TickCpuTime / divisor);
        broadcastToAllPlayers("Total cpuUsage in percents " + String.format("%.2f", totalCpuUsagePercents));
        // var intervals = "[";
        // for (var i : cpuUsage.LastNTimeIntervals) {
        //     intervals += " " + i / divisor;
        // }
        // intervals += "]";
        // broadcastToAllPlayers("Intervals : " + intervals);
    }

    void shiftTimeIntervals() {
        var newTotalTime = 0l;
        for (var key : entityCpuUsage.keySet()) {
            var currentEntityCpuUsage = entityCpuUsage.get(key);
            var cpuTime = currentEntityCpuUsage.TickCpuTime;
            newTotalTime += cpuTime;
        }

        System.arraycopy(LAST_INTERVALS_CPU_TIME, 1, LAST_INTERVALS_CPU_TIME, 0, LAST_INTERVALS_CPU_TIME.length - 1);
        LAST_INTERVALS_CPU_TIME[LAST_INTERVALS_CPU_TIME.length - 1] = newTotalTime;

        TOTAL_CPU_TIME = Arrays.stream(LAST_INTERVALS_CPU_TIME).reduce((a, b) -> a + b).get()/INTERVALS;
    }

    private void updateCpuUsage() {
        for (var key : entityCpuUsage.keySet()) {

            var currentEntityCpuUsage = entityCpuUsage.get(key);
            var computedCpuUsage = computePercentageOfCpuUsage(currentEntityCpuUsage);
            if (computedCpuUsage == 0) {
                entityCpuUsage.remove(key);
            }
            // for the sake of more fluent cpu-usage transition for each entity I average
            // their change
            currentEntityCpuUsage.TickCpuTime = 0l;
        }
    }

    /**
     * @return true if server can't keep up -> server tps is bigger than
     *         TPS_THRESHOLD
     */
    public boolean isServerOverloaded() {
        // server is overloaded if in any of the world's levels tps goes below specified
        // THRESHOLD
        return Utils.getTps(server, level) <= TPS_THRESHOLD;
    }

    float computePercentageOfCpuUsage(EntityCpuUsageData cpuUsageData) {
        if (TOTAL_CPU_TIME == 0)
            return 0;
        return cpuUsageData.TickCpuTime * 1.0f / TOTAL_CPU_TIME;
    }

    EntityCpuUsageData getOrCreateEntityCpuUsageData(Object entityType) {
        var currentEntityCpuUsage = entityCpuUsage.get(entityType);
        if (currentEntityCpuUsage == null) {
            currentEntityCpuUsage = new EntityCpuUsageData(INTERVALS);
            entityCpuUsage.put(entityType, currentEntityCpuUsage);
        }
        return currentEntityCpuUsage;
    }

    /**
     * Determines if given entity can tick by storing used cpu-time statistics.
     * Simply it passes all entities which does not overload server and stop those
     * entities which
     * causes a lot of lag.
     */
    public boolean canTick(Entity entity) {
        if (!SERVER_OVERLOADED)
            return true;
        if (entity instanceof Player)
            return true;
        if (entity instanceof LivingEntity) {
            var mob = (LivingEntity) entity;
            if (mob.isDeadOrDying())
                return true;
            if (mob.getCombatTracker().isInCombat())
                return true;
        }
        var entityType = entity.getType();

        var currentEntityCpuUsage = getOrCreateEntityCpuUsageData(entityType);
        
        var percentage = currentEntityCpuUsage.TickCpuTime*1.0f/TOTAL_CPU_TIME;

        // as lover-bound of cpu usage we skip ticks for some entity iff it's takes
        // more resources than it should be taking
        if (percentage > MAX_CPU_USAGE_PER_ENTITY_TYPE)
            //if overloaded entity is not working at all it breaks game experience =(
            if(currentEntityCpuUsage.TickCpuTime<5000000) return true;
        else
            return false;
        return Rand.nextFloat() <= 1 - percentage;
    }
    /**
     * We wrap tick passes and measure time entity spent on ticking
     */
    public void passTick(Entity entity, Action tick) {
        if (!canTick(entity))
            return;
        var entityType = entity.getType();
        var currentEntityCpuUsage = getOrCreateEntityCpuUsageData(entityType);
        long startTime = System.nanoTime();
        tick.call();
        currentEntityCpuUsage.TickCpuTime += System.nanoTime() - startTime;
        
        //there is a chance that current entity cpu time optimizer is not working at all, we then
    }
    
}
