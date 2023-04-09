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
        Long[] LastNTimeIntervals;
        Long TickCpuTime = 0l;
        float CpuUsagePercentage = 0;

        public EntityCpuUsageData(int INTERVALS) {
            CpuUsagePercentage = 0;
            LastNTimeIntervals = new Long[INTERVALS];
            Arrays.fill(LastNTimeIntervals, 0l);
            TickCpuTime = 0l;
        }

    }

    public interface Action {
        void call();
    }

    public static EntityCpuTimeOptimizer Create(Level level) {
        var entityCpuTimeOptimizer = new EntityCpuTimeOptimizer(level);

        entityCpuTimeOptimizer.INTERVALS = Config.intervals.get();
        entityCpuTimeOptimizer.MAX_CPU_USAGE_PER_ENTITY_TYPE = Config.maxCpuUsagePerEntityType.get();
        entityCpuTimeOptimizer.TIME_INTERVAL_MS = Config.timeIntervalsMs.get();
        entityCpuTimeOptimizer.TPS_THRESHOLD = Config.tpsThreshold.get();

        entityCpuTimeOptimizer.startBackgroundTask();
        return entityCpuTimeOptimizer;
    }

    /**
     * keeps total cpu time in last intervals
     */
    Long AVERAGE_TOTAL_TIME_IN_LAST_INTERVALS = 1l;

    boolean SERVER_OVERLOADED = false;

    /**
     * After server TPS goes below this value EntityCpuTimeOptimizer start to work
     */
    public float TPS_THRESHOLD = 15;

    /**
     * How long to collect statistics for single time interval. By default is one
     * second.
     */
    public long TIME_INTERVAL_MS = 1000;

    /**
     * How many time intervals to store for statistics
     */
    public int INTERVALS = 6;

    /**
     * How much cpu-time each entity of some type can use when server is overloaded.
     * In percents [0;1]
     */
    public float MAX_CPU_USAGE_PER_ENTITY_TYPE = 0.2f;
    public Random Rand;

    ConcurrentHashMap<Object, EntityCpuUsageData> entityCpuUsage;

    Thread backgroundTasks;

    boolean runningBackgroundTask;

    MinecraftServer server;

    Level level;

    Long[] LastNTotalTimes;

    public EntityCpuTimeOptimizer(Level level) {
        this.level = level;
        LastNTotalTimes = new Long[INTERVALS];
        Arrays.fill(LastNTotalTimes, 0l);

        this.Rand = new Random();
        this.entityCpuUsage = new ConcurrentHashMap<Object, EntityCpuUsageData>();
        this.server = ServerLifecycleHooks.getCurrentServer();
        this.backgroundTasks = new Thread(() -> {
            while (true) {
                try {
                    SERVER_OVERLOADED = isServerOverloaded();
                    Thread.sleep(TIME_INTERVAL_MS);
                    if (!SERVER_OVERLOADED)
                        continue;
                    this.runningBackgroundTask = true;
                    shiftTimeIntervals();
                    this.runningBackgroundTask = false;
                } catch (Exception e) {

                }
            }
        });
        startLoggingDebugInfo();
    }

    void startLoggingDebugInfo() {
        new Thread(() -> {
            while (true) {
                try {
                    logDebugInfo();
                    Thread.sleep(2000);
                } catch (Exception e) {

                }
            }
        }).start();
        ;
    }

    /**
     * @return averaged total cpu time entities used on the level this optimizer is
     *         created on for the last N time intervals
     */
    public Long getAverageTotalCpuTime() {
        return AVERAGE_TOTAL_TIME_IN_LAST_INTERVALS;
    }

    void broadcastToAllPlayers(String message) {
        Utils.broadcastToAllPlayers(message, server);
    }

    void logDebugInfo() {
        broadcastToAllPlayers("Level is " + level);
        broadcastToAllPlayers("TPS is " + Utils.getTps(server, level));
        broadcastToAllPlayers("Is optimizer running " + SERVER_OVERLOADED);
        broadcastToAllPlayers("Average total time " + AVERAGE_TOTAL_TIME_IN_LAST_INTERVALS);
        broadcastToAllPlayers("Keys " + entityCpuUsage.keySet().size());
        Object maxLoadEntity = null;
        EntityCpuUsageData cpuUsage = null;
        for (var key : entityCpuUsage.keySet()) {
            var load = entityCpuUsage.get(key);
            if (cpuUsage == null || cpuUsage.TickCpuTime < load.TickCpuTime) {
                cpuUsage = load;
                maxLoadEntity = key;
            }
        }
        if (cpuUsage == null)
            return;
        broadcastToAllPlayers("Max load entity is " + maxLoadEntity);
        broadcastToAllPlayers("Cpu % " + cpuUsage.CpuUsagePercentage);
        broadcastToAllPlayers("Takes " + cpuUsage.TickCpuTime);
        var intervals = "[";
        for (var i : cpuUsage.LastNTimeIntervals) {
            intervals += " " + i;
        }
        intervals += "]";
        broadcastToAllPlayers("Intervals : " + intervals);
    }

    public void startBackgroundTask() {
        backgroundTasks.start();
    }

    void shiftTimeIntervals() {
        var newTotalTime = 0l;
        for (var key : entityCpuUsage.keySet()) {
            var currentEntityCpuUsage = entityCpuUsage.get(key);
            var cpuTime = currentEntityCpuUsage.TickCpuTime;

            var intervals = currentEntityCpuUsage.LastNTimeIntervals;
            System.arraycopy(intervals, 1, intervals, 0, intervals.length - 1);
            intervals[intervals.length - 1] = cpuTime;
            currentEntityCpuUsage.TickCpuTime = 0l;
            newTotalTime += Arrays.stream(intervals).reduce((a, b) -> a + b).get();
        }
        System.arraycopy(LastNTotalTimes, 1, LastNTotalTimes, 0, LastNTotalTimes.length - 1);
        LastNTotalTimes[LastNTotalTimes.length - 1] = newTotalTime;

        AVERAGE_TOTAL_TIME_IN_LAST_INTERVALS += Arrays.stream(LastNTotalTimes).reduce((a, b) -> a + b).get()
                / INTERVALS;
        AVERAGE_TOTAL_TIME_IN_LAST_INTERVALS /= 2;

        for (var key : entityCpuUsage.keySet()) {
            
            var currentEntityCpuUsage = entityCpuUsage.get(key);
            var computedCpuUsage = computePercentageOfCpuUsage(currentEntityCpuUsage);
                if (computedCpuUsage == 0) {
                    entityCpuUsage.remove(key);
                }
                // for the sake of more fluent cpu-usage transition for each entity I average
                // their change
                currentEntityCpuUsage.CpuUsagePercentage += computePercentageOfCpuUsage(currentEntityCpuUsage);
                currentEntityCpuUsage.CpuUsagePercentage /= 2;
        }
    }

    /**
     * @return true if server can't keep up -> server tps is bigger than
     * TPS_THRESHOLD
     */
    public boolean isServerOverloaded() {
        // server is overloaded if in any of the world's levels tps goes below specified
        // THRESHOLD
        return Utils.getTps(server, level) <= TPS_THRESHOLD;
    }

    float computePercentageOfCpuUsage(EntityCpuUsageData cpuUsageData) {
        if (AVERAGE_TOTAL_TIME_IN_LAST_INTERVALS == 0)
            return cpuUsageData.CpuUsagePercentage;
        var cpuUsageOverLastIntervals = Arrays.stream(cpuUsageData.LastNTimeIntervals).reduce((a, b) -> a + b).get();
        return cpuUsageOverLastIntervals * 1.0f / AVERAGE_TOTAL_TIME_IN_LAST_INTERVALS;
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

            // as lover-bound of cpu usage we skip ticks for some entity iff it's takes
            // more resources than it should be taking
            if (currentEntityCpuUsage.CpuUsagePercentage > MAX_CPU_USAGE_PER_ENTITY_TYPE)
                return false;
            return Rand.nextFloat() <= 1 - currentEntityCpuUsage.CpuUsagePercentage;
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
    }

}
