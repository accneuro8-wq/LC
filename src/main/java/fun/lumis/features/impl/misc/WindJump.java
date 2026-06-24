package fun.lumis.features.impl.misc;

import antidaunleak.api.annotation.Native;
import fun.lumis.utils.features.aura.warp.Turns;
import fun.lumis.utils.features.aura.warp.TurnsConfig;
import fun.lumis.utils.features.aura.warp.TurnsConnection;
import fun.lumis.utils.math.task.TaskPriority;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.BindSetting;
import fun.lumis.events.keyboard.HotBarScrollEvent;
import fun.lumis.events.keyboard.KeyEvent;
import fun.lumis.events.player.HotBarUpdateEvent;
import fun.lumis.events.player.TickEvent;
import fun.lumis.events.render.WorldRenderEvent;
import fun.lumis.utils.interactions.interact.PlayerInteractionHelper;
import fun.lumis.utils.interactions.inv.InventoryTask;
import fun.lumis.utils.math.time.StopWatch;
import fun.lumis.utils.math.script.Script;
import fun.lumis.utils.features.aura.utils.MathAngle;
import fun.lumis.features.impl.render.ProjectilePrediction;

import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WindJump extends Module {
    private final Turns rot = new Turns(0, 0);
    BindSetting windChargeBind = new BindSetting("Заряд ветра", "Бросить заряд ветра");
    StopWatch stopWatch = new StopWatch();
    Script script = new Script();

    public WindJump() {
        super("WindJump", "Wind Jump", ModuleCategory.MISC);
        setup(windChargeBind);
    }

    @EventHandler
    public void onHotBarUpdate(HotBarUpdateEvent e) {
        if (!script.isFinished()) e.cancel();
    }

    @EventHandler
    public void onHotBarScroll(HotBarScrollEvent e) {
        if (!script.isFinished()) e.cancel();
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        if (e.isKeyReleased(windChargeBind.getKey())) {
            if (stopWatch.finished(0)) {
                InventoryTask.swapAndUse(Items.WIND_CHARGE);
            }
        }
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (PlayerInteractionHelper.isKey(windChargeBind)) {
            rot.setYaw(mc.player.getYaw());
            rot.setPitch(90);
            TurnsConnection.INSTANCE.rotateTo(rot, TurnsConfig.DEFAULT, TaskPriority.LOW_PRIORITY, this);
            ItemStack stack = Items.WIND_CHARGE.getDefaultStack();
            ProjectilePrediction.getInstance().drawPredictionInHand(e.getStack(), List.of(stack), MathAngle.cameraAngle());
        }
    }

    @EventHandler

    public void onTick(TickEvent e) {
        if (!script.isFinished() && stopWatch.every(250)) {
            script.update();
        }
    }
}
