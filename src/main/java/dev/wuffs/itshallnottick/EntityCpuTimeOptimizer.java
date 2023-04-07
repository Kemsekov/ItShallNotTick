package dev.wuffs.itshallnottick;

import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;

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
    class EntityCpuUsageData{
        Long[] LastNTimeIntervals;
        Long TickCpuTime = 0l;
        float CpuUsagePercentage = 0;
    }
    public interface Action{
        void call();
    }
    public static EntityCpuTimeOptimizer Create(Level level){
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
    Long TOTAL_TIME_IN_LAST_INTERVALS= 0l;

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
    public int INTERVALS=6;

    /**
     * How much cpu-time each entity of some type can use when server is overloaded. In percents [0;1]
     */
    public float MAX_CPU_USAGE_PER_ENTITY_TYPE=0.2f;
    public Random Rand;

    ConcurrentHashMap<Object, EntityCpuUsageData> entityCpuUsage;

    Thread backgroundTasks;

    boolean runningBackgroundTask;

    MinecraftServer server;

    Level level;

    
    public EntityCpuTimeOptimizer(Level level){
        this.level=level;
        this.Rand= new Random();
        this.entityCpuUsage = new ConcurrentHashMap<Object,EntityCpuUsageData>();
        this.server=ServerLifecycleHooks.getCurrentServer();
        this.backgroundTasks = new Thread(()->{
        while(true){
                try{
                    Thread.sleep(TIME_INTERVAL_MS);
                    logDebugInfo();
                    SERVER_OVERLOADED=isServerOverloaded();
                    if(!SERVER_OVERLOADED) continue;
                    this.runningBackgroundTask=true;
                    shiftTimeIntervals();
                    this.runningBackgroundTask=false;
                }
                catch(Exception e){

                }
        }
        });
    }
    void broadcastToAllPlayers(String message) {
        var playerList = server.getPlayerList();
        TextComponent chatMessage = new TextComponent(message);
        playerList.broadcastMessage(chatMessage, ChatType.SYSTEM, null);
    }
    void logDebugInfo() {
        broadcastToAllPlayers("Level is "+level);
        broadcastToAllPlayers("TPS is "+getTps());
        broadcastToAllPlayers("Is optimizer running "+SERVER_OVERLOADED);
        broadcastToAllPlayers("Total time "+TOTAL_TIME_IN_LAST_INTERVALS);
        broadcastToAllPlayers("Keys "+entityCpuUsage.keySet().size());
        Object maxLoadEntity=null;
        EntityCpuUsageData cpuUsage=null;
        for(var key : entityCpuUsage.keySet()){
            var load = entityCpuUsage.get(key);
            if(cpuUsage==null || cpuUsage.TickCpuTime<load.TickCpuTime){
                cpuUsage=load;
                maxLoadEntity=key;
            }
        }
        if(cpuUsage==null) return;
        broadcastToAllPlayers("Max load entity is "+maxLoadEntity);
        broadcastToAllPlayers("Cpu % "+cpuUsage.CpuUsagePercentage);
        broadcastToAllPlayers("Takes "+cpuUsage.TickCpuTime);
        var intervals="[";
        for(var i : cpuUsage.LastNTimeIntervals){
            intervals+=" "+i;
        }
        intervals+="]";
        broadcastToAllPlayers("Intervals : "+intervals);
    }

    public void startBackgroundTask(){
        backgroundTasks.start();
    }

    void shiftTimeIntervals(){
        var newTotalTime=0l;
        for(var key : entityCpuUsage.keySet()){
            var currentEntityCpuUsage = entityCpuUsage.get(key);
            var cpuTime = currentEntityCpuUsage.TickCpuTime;

            var intervals=currentEntityCpuUsage.LastNTimeIntervals;
            System.arraycopy(intervals, 1, intervals, 0, intervals.length-1);
            intervals[intervals.length-1]=cpuTime;
            currentEntityCpuUsage.TickCpuTime=0l;
            newTotalTime += Arrays.stream(intervals).reduce((a,b)->a+b).get();
        }
        TOTAL_TIME_IN_LAST_INTERVALS+=newTotalTime;
        TOTAL_TIME_IN_LAST_INTERVALS/=2;
        for(var key : entityCpuUsage.keySet()){
            var currentEntityCpuUsage = entityCpuUsage.get(key);
            currentEntityCpuUsage.CpuUsagePercentage += computePercentageOfCpuUsage(currentEntityCpuUsage);
            currentEntityCpuUsage.CpuUsagePercentage /=2;
        }
    }
    public double getTps(){
        var maxTickTime=Arrays.stream(server.getTickTime(level.dimension())).max().getAsLong();
        return 1000000000.0 / maxTickTime;
    }
    /**
     * @return true if server can't keep up -> server tps is bigger than TPS_THRESHOLD
     */
    public boolean isServerOverloaded() {
        //server is overloaded if in any of the world's levels tps goes below specified THRESHOLD
        return getTps()<=TPS_THRESHOLD;
    }

    float computePercentageOfCpuUsage(EntityCpuUsageData cpuUsageData){
        if(TOTAL_TIME_IN_LAST_INTERVALS==0) return cpuUsageData.CpuUsagePercentage;
        var cpuUsageOverLastIntervals = Arrays.stream(cpuUsageData.LastNTimeIntervals).reduce((a,b)->a+b).get();
        return cpuUsageOverLastIntervals*1.0f/TOTAL_TIME_IN_LAST_INTERVALS;
    }

    /**
     * Determines if given entity can tick by storing used cpu-time statistics.
     * Simply it passes all entities which does not overload server and stop those entities which
     * causes a lot of lag.
     */
    public boolean canTick(Entity entity) {
        if(!SERVER_OVERLOADED) return true;
        if(entity instanceof Player) return true;
        var entityType = entity.getType();
        var currentEntityCpuUsage = entityCpuUsage.get(entityType);
        if(currentEntityCpuUsage == null){
            currentEntityCpuUsage = _default();
            entityCpuUsage.put(entityType,currentEntityCpuUsage);
        }
        // as lover-bound of cpu usage we skip ticks for some entity iff it's takes 
        // more resources than it should be taking
        if(currentEntityCpuUsage.CpuUsagePercentage>MAX_CPU_USAGE_PER_ENTITY_TYPE)
            return false;
        return true;
    }
    
    EntityCpuUsageData _default(){
        var value = new EntityCpuUsageData();
        value.CpuUsagePercentage=0;
        value.LastNTimeIntervals=new Long[this.INTERVALS];
        for(int i = 0;i<INTERVALS;i++)
            value.LastNTimeIntervals[i]=0l;
        value.TickCpuTime=0l;
        return value;
    }
    /**
     * We wrap tick passes and measure time entity spent on ticking
     */
    public void passTick(Entity entity,Action tick) {
        if(!canTick(entity)) return;
        var entityType = entity.getType();
        var currentEntityCpuUsage = entityCpuUsage.get(entityType);
        if(currentEntityCpuUsage == null){
            currentEntityCpuUsage = _default();
            entityCpuUsage.put(entityType, currentEntityCpuUsage);
        }
        long startTime = System.nanoTime();
        tick.call();
        currentEntityCpuUsage.TickCpuTime+=System.nanoTime()-startTime;
    }

}
