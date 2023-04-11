package dev.wuffs.itshallnottick;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Arrays;
import java.util.Random;
/**
 * Tool that allows to track cpu-usage of any given task for some entity and balance 
 * that task execution so no entity takes more resources than it should
 */
public class LoadBalancer<TEntity> {
    class EntityCpuUsageData {
        Long TickCpuTime = 0l;
        Long TotalCpuTimeInLastIntervals = 0l;
    }

    public interface Tick<TEntity> {
        void call(TEntity entity);
    }
    /**
     * How many shift intervals store for statistics collection
     */
    public final int INTERVALS;

    /**
     * What is upped bound for cpu-time each entity can use to do some task.
     * In percents [0;1]
     */
    public final float MAX_CPU_USAGE_PER_ENTITY_TYPE;
    
    /**
     * Random that used to random-numbers generation that required for load-balancing
     */
    public Random Rand;

    protected ConcurrentHashMap<TEntity, EntityCpuUsageData> entityCpuUsage;

    protected Thread backgroundTasks;

    protected Long TOTAL_CPU_TIME = 1l;

    protected int MIN_TICK_TIME_PER_ENTITY_MS;
    /**
     * keeps total cpu time in last intervals
     */
    protected Long[] LAST_INTERVALS_CPU_TIME;
    /**
     * @param intervals How many intervals to 
     * @param maxCpuUsagePerEntity
     * @param minTickTimePerEntityMs
     */
    public LoadBalancer(int intervals, float maxCpuUsagePerEntity, int minTickTimePerEntityMs) {

        this.INTERVALS = intervals;
        this.MAX_CPU_USAGE_PER_ENTITY_TYPE = maxCpuUsagePerEntity;
        this.MIN_TICK_TIME_PER_ENTITY_MS=minTickTimePerEntityMs;
        this.LAST_INTERVALS_CPU_TIME = new Long[INTERVALS];
        Arrays.fill(LAST_INTERVALS_CPU_TIME, 0l);
        this.Rand = new Random();
        this.entityCpuUsage = new ConcurrentHashMap<TEntity, EntityCpuUsageData>();
        
    }
    /**
     * Because this balance loader keep statistics for last N intervals we need to do intervals shifts,
     * so we can keep track of average cpu-usage from last N intervals. This method does this shift.
     */
    public void shiftStatisticsCollection(){
        shiftTimeIntervals();
        updateCpuUsage();
    }
    /**
     * @return total average cpu time all entities used in last intervals
     */
    public Long getTotalCpuTime() {
        return TOTAL_CPU_TIME;
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

    void updateCpuUsage() {
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

    float computePercentageOfCpuUsage(EntityCpuUsageData cpuUsageData) {
        if (TOTAL_CPU_TIME == 0)
            return 0;
        return cpuUsageData.TickCpuTime * 1.0f / TOTAL_CPU_TIME;
    }

    EntityCpuUsageData getOrCreateEntityCpuUsageData(TEntity entity) {
        var currentEntityCpuUsage = entityCpuUsage.get(entity);
        if (currentEntityCpuUsage == null) {
            currentEntityCpuUsage = new EntityCpuUsageData();
            entityCpuUsage.put(entity, currentEntityCpuUsage);
        }
        return currentEntityCpuUsage;
    }

    /**
     * Determines if given entity can tick.
     * Simply it returns false for entities which did not took a lot of cpu-time already and true for
     * entities that didn't hit upper bound of MAX_CPU_USAGE_PER_ENTITY_TYPE.
     */
    public boolean canTick(TEntity entity) {

        var currentEntityCpuUsage = getOrCreateEntityCpuUsageData(entity);
        var percentage = currentEntityCpuUsage.TickCpuTime*1.0f/TOTAL_CPU_TIME;

        // as lover-bound of cpu usage we skip ticks for some entity iff it's takes
        // more resources than it should be taking
        if (percentage > MAX_CPU_USAGE_PER_ENTITY_TYPE)
            //if overloaded entity is not working at all it breaks game experience =(
            if(currentEntityCpuUsage.TickCpuTime<MIN_TICK_TIME_PER_ENTITY_MS*1000000) 
            return true;
        else
            return false;
        return Rand.nextFloat() <= 1 - percentage;
    }
    /**
     * Execute task for given entity, measure it's time and add to statistics
     */
    public void passTick(TEntity entity, Tick<TEntity> tick) {
        var currentEntityCpuUsage = getOrCreateEntityCpuUsageData(entity);
        long startTime = System.nanoTime();
        tick.call(entity);
        currentEntityCpuUsage.TickCpuTime += System.nanoTime() - startTime;
    }
    
}
