package fun.lumis.features.impl.player;

import antidaunleak.api.annotation.Native;
import fun.lumis.utils.interactions.inv.InventoryTask;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.utils.interactions.interact.PlayerInteractionHelper;
import fun.lumis.utils.math.time.StopWatch;
import fun.lumis.utils.math.script.Script;
import fun.lumis.events.block.BlockBreakingEvent;
import fun.lumis.events.keyboard.HotBarScrollEvent;
import fun.lumis.events.player.HotBarUpdateEvent;
import fun.lumis.events.player.TickEvent;
import fun.lumis.events.render.ItemRendererEvent;

import java.util.Comparator;
import java.util.Objects;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class AutoTool extends Module {
    private final StopWatch swap = new StopWatch(), breaking = new StopWatch();
    private final Script script = new Script(), swapBackScript = new Script();
    private ItemStack renderStack;
    private BlockPos lastBreakPos;

    public AutoTool() {
        super("AutoTool", "Auto Tool", ModuleCategory.PLAYER);
    }

    @EventHandler
    public void onItemRenderer(ItemRendererEvent e) {
        if (renderStack != null && e.getHand().equals(Hand.MAIN_HAND) && Objects.equals(mc.player, e.getPlayer())) {
            e.setStack(renderStack);
        }
    }

    @EventHandler
    public void onHotBarUpdate(HotBarUpdateEvent e) {
        if (!swapBackScript.isFinished()) e.cancel();
    }

    @EventHandler
    public void onHotBarScroll(HotBarScrollEvent e) {
        if (!swapBackScript.isFinished()) e.cancel();
    }

    @EventHandler
    @Native(type = Native.Type.VMProtectBeginMutation)
    public void onBlockBreaking(BlockBreakingEvent e) {
        breaking.reset();
        lastBreakPos = e.blockPos();
        if (!mc.player.isCreative() && swapBackScript.isFinished() && swap.finished(350)) {
            Slot currentBestSlot = findBestTool(lastBreakPos);
            if (currentBestSlot != null && currentBestSlot != InventoryTask.mainHandSlot()) {
                renderStack = mc.player.getMainHandStack();
                InventoryTask.swapHand(currentBestSlot, Hand.MAIN_HAND, true);
                swapBackScript.cleanup().addTickStep(0, () -> InventoryTask.swapHand(currentBestSlot, Hand.MAIN_HAND, true, true));
                swap.reset();
            }
        }
    }

    @EventHandler
    public void onTick(TickEvent e) {
        script.update();
        if (!swapBackScript.isFinished() && swap.finished(350)) {
            Slot currentBestSlot = findBestTool(lastBreakPos);
            if (currentBestSlot != InventoryTask.mainHandSlot() || breaking.finished(100)) {
                script.cleanup().addTickStep(4, () -> renderStack = null);
                swapBackScript.update();
                swap.reset();
            }
        }
    }

    private Slot findBestTool(BlockPos blockPos) {
        BlockState state = mc.world.getBlockState(blockPos);
        if (PlayerInteractionHelper.isAir(state)) return InventoryTask.mainHandSlot();
        return InventoryTask.slots().sorted(Comparator.comparing(slot -> slot.equals(InventoryTask.mainHandSlot())))
                .filter(s -> s.getStack().getMiningSpeedMultiplier(state) != 1).max(Comparator.comparingDouble(s -> s.getStack().getMiningSpeedMultiplier(state))).orElse(null);
    }
}
