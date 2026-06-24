package fun.lumis.mixins.game.world;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fun.lumis.utils.client.managers.event.EventManager;
import fun.lumis.utils.display.interfaces.QuickImports;
import fun.lumis.events.block.BlockUpdateEvent;

@Mixin(World.class)
public abstract class WorldMixin implements QuickImports {

    @Inject(method = "onBlockChanged", at = @At("RETURN"))
    private void onBlockChangedHook(BlockPos pos, BlockState oldBlock, BlockState newBlock, CallbackInfo ci) {
        if (mc.world != (Object) this) return;
        EventManager.callEvent(new BlockUpdateEvent(newBlock, pos.toImmutable(), BlockUpdateEvent.Type.UPDATE));
    }
}
