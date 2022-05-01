package dev.wuffs.itshallnottick.mixin;


import dev.wuffs.itshallnottick.Config;
import dev.wuffs.itshallnottick.Utils;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.timings.TimeTracker;
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

    /**
     * @reason tps
     * @author Team Deus Vult
     */
    @Overwrite
    public void guardEntityTick(Consumer<Entity> consumer, Entity entity) {
        if (Utils.isIgnoredEntity(entity)) {
            handleGuardEntityTick(consumer, entity);
            return;
        }

        Level world = ((Level) (Object) this);
        BlockPos entityPos = entity.blockPosition();

        boolean isInClaimedChunk = Utils.isInClaimedChunk(world, entityPos);

        if (!(entity instanceof LivingEntity) || entity instanceof Player) {
            if (!isInClaimedChunk && entity instanceof ItemEntity && random.nextInt(4) == 0) {
                return;
            }

            handleGuardEntityTick(consumer, entity);
            return;
        }

        int maxHeight = Config.maxEntityTickDistanceVertical.get();
        int maxDistanceSquare = Config.maxEntityTickDistanceHorizontal.get();

        if (Utils.isNearPlayer(world, entityPos, maxHeight, maxDistanceSquare)) {
            handleGuardEntityTick(consumer, entity);
            return;
        }

        if (isInClaimedChunk && ((LivingEntity) entity).isDeadOrDying()) {
            handleGuardEntityTick(consumer, entity);
        }
    }

    public void handleGuardEntityTick(Consumer<Entity> consumer, Entity entity) {
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