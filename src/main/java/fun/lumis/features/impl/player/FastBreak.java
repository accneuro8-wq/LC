package fun.lumis.features.impl.player;

import fun.lumis.events.player.TickEvent;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.SliderSettings;
import fun.lumis.utils.client.managers.event.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class FastBreak extends Module {

    private final SliderSettings speed = new SliderSettings("Скорость", "Множитель")
            .setValue(1.4F).range(1.0F, 3.0F);

    private BlockPos lastPos = null;

    public FastBreak() {
        super("FastBreak", ModuleCategory.PLAYER);
        setup(speed);
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (event == null) return;

        if (mc.options.attackKey.isPressed() && mc.crosshairTarget instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            Direction side = blockHit.getSide();

            if (lastPos != null && !lastPos.equals(pos)) {
                mc.interactionManager.currentBreakingProgress = 0;
            }

            lastPos = pos;

            float multiplier = speed.getValue();

            for (int i = 0; i < (int) multiplier; i++) {
                if (mc.interactionManager.currentBreakingProgress >= 0.85F) {
                    if (mc.getNetworkHandler() != null) {
                        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, side));
                    }
                    mc.interactionManager.currentBreakingProgress = 1.0F;
                    break;
                }

                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, side));
                }

                mc.interactionManager.currentBreakingProgress += 0.03F;
            }

            mc.player.swingHand(Hand.MAIN_HAND);
        } else {
            lastPos = null;
        }
    }

    @Override
    public void deactivate() {
        lastPos = null;
        super.deactivate();
    }
}