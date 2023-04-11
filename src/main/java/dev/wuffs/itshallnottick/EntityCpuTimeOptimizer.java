package dev.wuffs.itshallnottick;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;


// Abstract:
// we accept Zombie entity for ticking, we check if server can't keep up,
// if it is, we get percent of cpu usage for all Zombie-type entities and compare it to
// cpu-load from last N time-intervals.
// if this percent of cpu usage is bigger than some threshold, we skip this entity ticking.
// Idea is to skip ticks for entities that loads server the most

public class EntityCpuTimeOptimizer {

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

    Thread backgroundTasks;

    boolean runningBackgroundTask = false;

    MinecraftServer server;

    Level level;

    Thread logThread;

    LoadBalancer<Object> loadBalancer;

    public EntityCpuTimeOptimizer(Level level) {
        this.loadBalancer = new LoadBalancer<Object>(Config.intervals.get(),Config.maxCpuUsagePerEntityType.get().floatValue(),5);
        this.TIME_INTERVAL_MS = Config.timeIntervalsMs.get();
        this.TPS_THRESHOLD = Config.tpsThreshold.get();
        this.level = level;
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
                    loadBalancer.shiftStatisticsCollection();
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
        return loadBalancer.TOTAL_CPU_TIME;
    }

    void broadcastToAllPlayers(String message) {
        Utils.broadcastToAllPlayers(message, server);
    }

    void logDebugInfo() {
        var divisor = 1000000;
        var entityCpuUsage = loadBalancer.entityCpuUsage;
        var TOTAL_CPU_TIME = loadBalancer.TOTAL_CPU_TIME;
        broadcastToAllPlayers("Level is " + level);
        broadcastToAllPlayers("TPS is " + Utils.getTps(server, level));
        broadcastToAllPlayers("Is optimizer running " + SERVER_OVERLOADED);
        broadcastToAllPlayers("Total cpu time " + TOTAL_CPU_TIME / divisor);
        broadcastToAllPlayers("Keys " + loadBalancer.entityCpuUsage.keySet().size());
        Object maxLoadEntity = null;
        LoadBalancer<Object>.EntityCpuUsageData cpuUsage = null;
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
        broadcastToAllPlayers("Takes " + cpuUsage.TickCpuTime / divisor);
        broadcastToAllPlayers("Total cpuUsage in percents " + String.format("%.2f", totalCpuUsagePercents));
    }

    /**
     * @return true if server can't keep up -> server tps is below than
     *         TPS_THRESHOLD
     */
    public boolean isServerOverloaded() {
        // server is overloaded if in any of the world's levels tps goes below specified
        // THRESHOLD
        return Utils.getTps(server, level) <= TPS_THRESHOLD;
    }

    /**
     * Determines if given entity can tick by checking used cpu-time statistics.
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
        return loadBalancer.canTick(entity.getType());
    }

    public interface Tick {
        void call(Entity entity);
    }

    /**
     * We wrap tick passes and measure time entity spent on ticking
     */
    public void passTick(Entity entity, Tick tick) {
        if (!canTick(entity))
            return;
        loadBalancer.passTick(entity.getType(),entityType->tick.call(entity) );
    }
    
}
