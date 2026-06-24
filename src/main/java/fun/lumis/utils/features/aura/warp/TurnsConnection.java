package fun.lumis.utils.features.aura.warp;

import fun.lumis.utils.features.aura.utils.MathAngle;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.utils.client.managers.event.EventManager;
import fun.lumis.utils.client.managers.event.types.EventType;
import fun.lumis.features.module.Module;
import fun.lumis.utils.display.interfaces.QuickImports;
import fun.lumis.utils.math.task.TaskPriority;
import fun.lumis.utils.math.task.TaskProcessor;
import fun.lumis.lumis;
import fun.lumis.events.packet.PacketEvent;
import fun.lumis.events.player.*;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TurnsConnection implements QuickImports {
    public static TurnsConnection INSTANCE = new TurnsConnection();

    TurnsConstructor lastRotationPlan;
    final TaskProcessor<TurnsConstructor> rotationPlanTaskProcessor = new TaskProcessor<>();
    public Turns currentAngle;
    Turns previousAngle;
    Turns serverAngle = Turns.DEFAULT;
    Turns fakeAngle;

    public TurnsConnection() {
        lumis.getInstance().getEventManager().register(this);
    }

    public void setRotation(Turns value) {
        if (value == null) {
            this.previousAngle = this.currentAngle != null ? this.currentAngle : MathAngle.cameraAngle();
        } else {
            this.previousAngle = this.currentAngle;
        }
        this.currentAngle = value;
    }

    public Turns getRotation() {
        return currentAngle != null ? currentAngle : MathAngle.cameraAngle();
    }

    public Turns getFakeRotation() {
        if (fakeAngle != null) return fakeAngle;
        return currentAngle != null ? currentAngle : previousAngle != null ? previousAngle : MathAngle.cameraAngle();
    }

    public void setFakeRotation(Turns angle) {
        this.fakeAngle = angle;
    }

    public Turns getPreviousRotation() {
        if (currentAngle != null && previousAngle != null) return previousAngle;
        if (mc.player != null) return new Turns(mc.player.prevYaw, mc.player.prevPitch);
        return Turns.DEFAULT;
    }

    public Turns getMoveRotation() {
        TurnsConstructor rotationPlan = getCurrentRotationPlan();
        return currentAngle != null && rotationPlan != null && rotationPlan.isMoveCorrection() ? currentAngle : MathAngle.cameraAngle();
    }

    public TurnsConstructor getCurrentRotationPlan() {
        return rotationPlanTaskProcessor.fetchActiveTaskValue() != null
                ? rotationPlanTaskProcessor.fetchActiveTaskValue() : lastRotationPlan;
    }

    // --- Методы наводки ---

    public void rotateTo(Turns.VecRotation vecRotation, LivingEntity entity, int reset, TurnsConfig configurable, TaskPriority taskPriority, Module provider) {
        rotateTo(configurable.createRotationPlan(vecRotation.getAngle(), vecRotation.getVec(), entity, reset), taskPriority, provider);
    }

    public void rotateTo(Turns angle, int reset, TurnsConfig configurable, TaskPriority taskPriority, Module provider) {
        rotateTo(configurable.createRotationPlan(angle, angle.toVector(), null, reset), taskPriority, provider);
    }

    public void rotateTo(Turns angle, TurnsConfig configurable, TaskPriority taskPriority, Module provider) {
        rotateTo(configurable.createRotationPlan(angle, angle.toVector(), null, 1), taskPriority, provider);
    }

    public void rotateTo(TurnsConstructor plan, TaskPriority taskPriority, Module provider) {
        rotationPlanTaskProcessor.addTask(new TaskProcessor.Task<>(1, taskPriority.getPriority(), provider, plan));
    }

    // --- Логика обновления ---

    public void update() {
        if (mc.player == null) return;

        TurnsConstructor activePlan = getCurrentRotationPlan();
        if (activePlan == null) {
            // Если плана нет, постепенно обнуляем углы, чтобы не было резкого прыжка
            if (currentAngle != null) setRotation(null);
            return;
        }

        Turns clientAngle = MathAngle.cameraAngle();

        // Проверка на завершение плана (Reset Threshold)
        if (lastRotationPlan != null) {
            double differenceFromCurrentToPlayer = computeRotationDifference(serverAngle, clientAngle);
            if (activePlan.getTicksUntilReset() <= rotationPlanTaskProcessor.tickCounter && differenceFromCurrentToPlayer < activePlan.getResetThreshold()) {
                releaseTarget();
                return;
            }
        }

        Turns nextAngle = activePlan.nextRotation(currentAngle != null ? currentAngle : clientAngle, rotationPlanTaskProcessor.fetchActiveTaskValue() == null).adjustSensitivity();
        setRotation(nextAngle);

        lastRotationPlan = activePlan;
        rotationPlanTaskProcessor.tick(1);
    }

    // --- Очистка (Критически важно для Ауры) ---

    public void clear() {
        rotationPlanTaskProcessor.activeTasks.clear();
        this.fakeAngle = null;
    }

    public void releaseTarget() {
        rotationPlanTaskProcessor.activeTasks.clear();
        this.lastRotationPlan = null;
        this.rotationPlanTaskProcessor.tickCounter = 0;
        this.currentAngle = null;
        this.fakeAngle = null;
    }

    // --- Обработка движения и событий ---

    private Vec3d fixVelocity(Vec3d currVelocity, Vec3d movementInput, float speed) {
        if (currentAngle != null) {
            float yaw = currentAngle.getYaw();
            double d = movementInput.lengthSquared();

            if (d < 1.0E-7) return Vec3d.ZERO;

            Vec3d vec3d = (d > 1.0 ? movementInput.normalize() : movementInput).multiply(speed);
            float f = MathHelper.sin(yaw * 0.017453292f);
            float g = MathHelper.cos(yaw * 0.017453292f);

            return new Vec3d(vec3d.getX() * g - vec3d.getZ() * f, vec3d.getY(), vec3d.getZ() * g + vec3d.getX() * f);
        }
        return currVelocity;
    }

    @EventHandler
    public void onPlayerVelocityStrafe(PlayerVelocityStrafeEvent e) {
        TurnsConstructor currentRotationPlan = getCurrentRotationPlan();
        if (currentRotationPlan != null && currentRotationPlan.isMoveCorrection()) {
            e.setVelocity(fixVelocity(e.getVelocity(), e.getMovementInput(), e.getSpeed()));
        }
    }

    @EventHandler
    public void onTick(TickEvent e) {
        EventManager.callEvent(new RotationUpdateEvent(mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround(), EventType.PRE));

        update();

        EventManager.callEvent(new RotationUpdateEvent(mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround(), EventType.POST));
    }

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (event.isCancelled()) return;

        if (event.getPacket() instanceof PlayerMoveC2SPacket player && player.changesLook()) {
            serverAngle = new Turns(player.getYaw(mc.player.getYaw()), player.getPitch(mc.player.getPitch()));
        } else if (event.getPacket() instanceof PlayerPositionLookS2CPacket s2c) {
            float yaw = s2c.change().yaw();
            float pitch = s2c.change().pitch();

            serverAngle = new Turns(yaw, pitch);
            if (currentAngle != null) {
                releaseTarget();
            }
        }
    }

    public static double computeRotationDifference(Turns a, Turns b) {
        return Math.hypot(Math.abs(computeAngleDifference(a.getYaw(), b.getYaw())), Math.abs(a.getPitch() - b.getPitch()));
    }

    public static float computeAngleDifference(float a, float b) {
        return MathHelper.wrapDegrees(a - b);
    }
}