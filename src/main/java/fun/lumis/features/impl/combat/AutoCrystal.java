package fun.lumis.features.impl.combat;

import fun.lumis.events.player.TickEvent;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.SliderSettings;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.utils.features.aura.utils.MathAngle;
import fun.lumis.utils.features.aura.warp.Turns;
import fun.lumis.utils.features.aura.warp.TurnsConfig;
import fun.lumis.utils.features.aura.warp.TurnsConnection;
import fun.lumis.utils.interactions.interact.PlayerInteractionHelper;
import fun.lumis.utils.math.task.TaskPriority;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.stream.StreamSupport;

public class AutoCrystal extends Module {

    final SliderSettings range = new SliderSettings("Дистанция", "Радиус").range(1.0f, 6.0f).setValue(4.5f);
    final SliderSettings delay = new SliderSettings("Задержка MS", "Задержка между действиями").range(0, 500).setValue(50);

    long lastActionTime;

    public AutoCrystal() {
        super("AutoCrystal", ModuleCategory.COMBAT);
        setup(range, delay);
    }

    @EventHandler
    public void onTick(TickEvent ignored) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (System.currentTimeMillis() - lastActionTime < delay.getValue()) return;
        EndCrystalEntity crystal = StreamSupport.stream(mc.world.getEntities().spliterator(), false)
                .filter(e -> e instanceof EndCrystalEntity)
                .map(e -> (EndCrystalEntity) e)
                .filter(c -> mc.player.distanceTo(c) <= range.getValue())
                .min(Comparator.comparingDouble(c -> mc.player.distanceTo(c)))
                .orElse(null);

        if (crystal != null) {
            rotateTo(crystal.getPos());
            mc.interactionManager.attackEntity(mc.player, crystal);
            lastActionTime = System.currentTimeMillis();
            return;
        }

        if (mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) {
            double r = range.getValue();
            BlockPos bestPos = BlockPos.stream(mc.player.getBoundingBox().expand(r))
                    .map(BlockPos::toImmutable)
                    .filter(pos -> mc.world.getBlockState(pos).isOf(Blocks.OBSIDIAN) && mc.world.isAir(pos.up()))
                    .min(Comparator.comparingDouble(pos -> mc.player.getPos().distanceTo(pos.toCenterPos())))
                    .orElse(null);

            if (bestPos != null) {
                rotateTo(bestPos.toCenterPos());
                PlayerInteractionHelper.sendSequencedPacket(i -> new PlayerInteractBlockC2SPacket(
                        Hand.MAIN_HAND, new BlockHitResult(bestPos.toCenterPos(), Direction.UP, bestPos, false), i));
                lastActionTime = System.currentTimeMillis();
            }
        }
    }

    private void rotateTo(Vec3d target) {
        Turns angle = MathAngle.fromVec3d(target.subtract(mc.player.getEyePos()));
        TurnsConnection.INSTANCE.rotateTo(angle, TurnsConfig.DEFAULT, TaskPriority.STANDARD, this);
    }
}