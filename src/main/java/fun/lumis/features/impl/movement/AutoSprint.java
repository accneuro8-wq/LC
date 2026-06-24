package fun.lumis.features.impl.movement;

import fun.lumis.events.player.TickEvent;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.utils.client.Instance;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class AutoSprint extends Module {
    public static AutoSprint getInstance() {
        return Instance.get(AutoSprint.class);
    }

    // Возвращаем tickStop, чтобы киллаура или другие модули могли временно стопать бег
    public static int tickStop = 0;

    public AutoSprint() {
        super("AutoSprint", ModuleCategory.MOVEMENT);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null) return;

        if (tickStop > 0) {
            tickStop--;
            return;
        }

        boolean canSprint = mc.player.getHungerManager().getFoodLevel() > 6 || mc.player.getAbilities().allowFlying;
        boolean moving = mc.player.input.movementForward > 0 || mc.player.input.movementSideways != 0;

        if (moving && canSprint && !mc.player.isSneaking() && !mc.player.horizontalCollision) {
            mc.player.setSprinting(true);
        }
    }

    @Override
    public void deactivate() {
        if (mc.player != null) {
            mc.player.setSprinting(false);
        }
        super.deactivate();
    }
}