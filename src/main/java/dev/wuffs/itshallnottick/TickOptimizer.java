package dev.wuffs.itshallnottick;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.timings.TimeTracker;

import java.util.function.Consumer;

public class TickOptimizer {

    public static void entityTicking(Consumer<Entity> consumer, Entity entity, Level level, RandomSource random){

        if (!Utils.enoughPlayers(level)){
            handleGuardEntityTick(consumer, entity);
            return;
        }

        if (Utils.isIgnoredEntity(entity)) {
            handleGuardEntityTick(consumer, entity);
            return;
        }
        BlockPos entityPos = entity.blockPosition();

        boolean isInClaimedChunk = Utils.isInClaimedChunk(level, entityPos);

        if (!(entity instanceof LivingEntity) || entity instanceof Player) {
            if (!isInClaimedChunk && entity instanceof ItemEntity && random.nextInt(4) == 0) {
                return;
            }

            handleGuardEntityTick(consumer, entity);
            return;
        }

        int maxHeight = Config.maxEntityTickDistanceVertical.get();
        int maxDistanceSquare = Config.maxEntityTickDistanceHorizontal.get();

        if (Utils.isNearPlayer(level, entityPos, maxHeight, maxDistanceSquare)) {
            handleGuardEntityTick(consumer, entity);
            return;
        }

        if (isInClaimedChunk || ((LivingEntity) entity).isDeadOrDying()) {
            handleGuardEntityTick(consumer, entity);
        }
    }

    public static void handleGuardEntityTick(Consumer<Entity> consumer, Entity entity) {
        try {
            TimeTracker.ENTITY_UPDATE.trackStart(entity);
            consumer.accept(entity);
        }
        catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Ticking entity");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Entity being ticked");
            entity.fillCrashReportCategory(crashreportcategory);
            throw new ReportedException(crashreport);
        }
        finally {
            TimeTracker.ENTITY_UPDATE.trackEnd(entity);
        }
    }
}
