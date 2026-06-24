package fun.lumis.features.impl.movement;

import fun.lumis.events.player.TickEvent;
import fun.lumis.features.impl.combat.Aura;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.SelectSetting;
import fun.lumis.features.module.setting.implement.SliderSettings;
import fun.lumis.utils.client.Instance;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.utils.features.aura.warp.Turns;
import fun.lumis.utils.features.aura.warp.TurnsConfig;
import fun.lumis.utils.features.aura.warp.TurnsConnection;
import fun.lumis.utils.interactions.simulate.Simulations;
import fun.lumis.utils.math.task.TaskPriority;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class Strafe extends Module {

    public static Strafe getInstance() {
        return Instance.get(Strafe.class);
    }

    final SelectSetting mode = new SelectSetting("Режим", "Выберите тип стрейфов")
            .value("Matrix", "Grim")
            .selected("Matrix");

    final SliderSettings speed = new SliderSettings("Скорость", "Скорость стрейфа")
            .setValue(0.28f).range(0.1f, 1f)
            .visible(() -> mode.isSelected("Matrix"));

    float lastYaw;
    float lastPitch;
    final Turns rot = new Turns(0, 0);

    public Strafe() {
        super("Strafe", "Strafe", ModuleCategory.MOVEMENT);
        setup(mode, speed);
    }

    @Override
    public void activate() {
        if (mc.player != null) {
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
        } else {
            lastYaw = 0;
            lastPitch = 0;
        }
    }

    @Override
    public void deactivate() {
        if (mode.isSelected("Grim")) {
            TurnsConfig.freeCorrection = false;
        }
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (isTargetStrafeActive()) return;

        boolean moving = Simulations.hasPlayerMovement();

        String selected = mode.getSelected();
        if (selected.equals("Matrix")) {
            handleMatrix(moving);
        } else if (selected.equals("Grim")) {
            handleGrim(moving);
        }
    }

    private void handleMatrix(boolean moving) {
        if (!moving) {
            Simulations.setVelocity(0);
            return;
        }

        float moveYaw = Simulations.moveYaw(mc.player.getYaw());
        float yawRad = moveYaw * 0.017453292f;

        Vec3d currentVel = mc.player.getVelocity();
        double currentSpeed = Math.sqrt(currentVel.x * currentVel.x + currentVel.z * currentVel.z);
        double targetSpeed = speed.getValue();
        double appliedSpeed = Math.max(currentSpeed, targetSpeed);

        double motionX = -MathHelper.sin(yawRad) * appliedSpeed;
        double motionZ = MathHelper.cos(yawRad) * appliedSpeed;

        mc.player.setVelocity(motionX, currentVel.y, motionZ);

        lastYaw = moveYaw;
        lastPitch = mc.player.getPitch();
    }

    private void handleGrim(boolean moving) {
        if (!moving) return;

        TurnsConfig.freeCorrection = true;
        float moveYaw = Simulations.moveYaw(mc.player.getYaw());

        rot.setYaw(moveYaw);
        rot.setPitch(mc.player.getPitch());

        if (Aura.getInstance().getTarget() == null) {
            TurnsConnection.INSTANCE.rotateTo(rot, TurnsConfig.DEFAULT, TaskPriority.LOW_PRIORITY, this);
        }

        lastYaw = moveYaw;
        lastPitch = mc.player.getPitch();
    }

    private boolean isTargetStrafeActive() {
        return TargetStrafe.getInstance().isState()
                && Aura.getInstance().getTarget() != null;
    }
}