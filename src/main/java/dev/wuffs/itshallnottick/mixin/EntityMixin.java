package dev.wuffs.itshallnottick.mixin;

import dev.wuffs.itshallnottick.IEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin implements IEntity {
    @Shadow public abstract BlockPos blockPosition();

    private BlockPos  lastPos = BlockPos.ZERO;
    private boolean isIdle = false;
    private int idleTime = 0;

    @Inject(method = "Lnet/minecraft/world/entity/Entity;setPosRaw(DDD)V", at = @At("HEAD"))
    public void setPosRaw(double d, double e, double f, CallbackInfo ci){
        this.lastPos = this.blockPosition();
        BlockPos blockPos = new BlockPos(d,e,f);
        if (lastPos.equals(blockPos)){
            idleTime++;
        }else {
            idleTime = 0;
        }

        if(idleTime > 200) {
            isIdle = true;
        }else if (isIdle){
            isIdle = false;
            idleTime = 0;
        }

        if (!(((Entity)(Object)this) instanceof Player)){
            System.out.println(idleTime);
        }
    }

    @Override
    public BlockPos getLastPosition(){
        return this.lastPos;
    }

    @Override
    public boolean getIsIdle() {
        return isIdle;
    }
}
