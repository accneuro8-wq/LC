package fun.lumis.features.impl.movement;

import fun.lumis.events.player.TickEvent;
import fun.lumis.features.impl.combat.Aura;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.BooleanSetting;
import fun.lumis.features.module.setting.implement.SelectSetting;
import fun.lumis.features.module.setting.implement.SliderSettings;
import fun.lumis.utils.client.Instance;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.utils.interactions.interact.PlayerInteractionHelper;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class ElytraTarget extends Module {

    public SelectSetting mode = new SelectSetting("Режим", "Обход")
            .value("Matrix", "Grim", "HvH").selected("Matrix");

    public SliderSettings speed = new SliderSettings("Скорость", "Тяга")
            .setValue(1.2f).range(0.1f, 3.0f);

    public SliderSettings distance = new SliderSettings("Дистанция", "Радиус")
            .setValue(3.5f).range(1.0f, 7.0f);

    public SliderSettings hvhJitter = new SliderSettings("HvH Рванка", "Рывок")
            .setValue(2.0f).range(0.5f, 5.0f).visible(() -> mode.isSelected("HvH"));

    public BooleanSetting flightProtection = new BooleanSetting("Защита Полета", "Взлет")
            .setValue(true);

    public BooleanSetting autoRocket = new BooleanSetting("Auto Rocket", "Ракеты")
            .setValue(true);

    long lastRocketTime = 0;
    int tickCounter = 0;
    int glideCooldown = 0;
    int jumpTicks = 0;

    public ElytraTarget() {
        super("ElytraTarget", "ElytraTarget   §e§l[BETA]", ModuleCategory.MOVEMENT);
        setup(mode, speed, distance, hvhJitter, flightProtection, autoRocket);
    }

    public static ElytraTarget getInstance() {
        return Instance.get(ElytraTarget.class);
    }

    @Override
    public void activate() {
        tickCounter = 0;
        glideCooldown = 0;
        lastRocketTime = 0;
        jumpTicks = 0;
    }

    @Override
    public void deactivate() {
        tickCounter = 0;
        glideCooldown = 0;
        jumpTicks = 0;
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) return;

        LivingEntity target = Aura.getInstance().getTarget();

        if (glideCooldown > 0) glideCooldown--;
        if (jumpTicks > 0) jumpTicks--;

        if (mc.player.isOnGround()) {
            if (target != null && target.isAlive() && flightProtection.isValue()) {
                if (jumpTicks <= 0) {
                    mc.player.jump();
                    jumpTicks = 5;
                }
            }
            return;
        }

        if (!mc.player.isGliding()) {
            if (glideCooldown <= 0 && mc.player.getVelocity().y < -0.1) {
                PlayerInteractionHelper.startFallFlying();
                glideCooldown = 10;
            }
            return;
        }

        if (target == null || !target.isAlive()) {
            holdAltitude();
            return;
        }

        tickCounter++;

        Vec3d playerPos = mc.player.getPos();
        Vec3d targetPos = predictTarget(target);
        Vec3d dir = targetPos.subtract(playerPos);
        double dist = dir.length();
        double horizontalDist = Math.sqrt(dir.x * dir.x + dir.z * dir.z);

        Vec3d motion;

        String selected = mode.getSelected();
        if (selected.equals("Matrix")) {
            motion = handleMatrix(dir, dist, horizontalDist, target);
        } else if (selected.equals("Grim")) {
            motion = handleGrim(dir, dist, horizontalDist, target);
        } else {
            motion = handleHvH(dir, dist, horizontalDist, target);
        }

        double heightDiff = mc.player.getY() - target.getY();
        if (heightDiff < 1.5) {
            double liftForce = MathHelper.clamp((1.5 - heightDiff) * 0.25, 0, 0.4);
            motion = motion.add(0, liftForce, 0);
        } else if (heightDiff > 5.0) {
            double dropForce = MathHelper.clamp((heightDiff - 5.0) * 0.1, 0, 0.3);
            motion = motion.add(0, -dropForce, 0);
        }

        applyMotion(motion);

        if (autoRocket.isValue()) {
            handleRocket(horizontalDist);
        }
    }

    private Vec3d predictTarget(LivingEntity target) {
        Vec3d vel = target.getVelocity();
        double horizSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);

        double predictionTicks;
        double playerDist = mc.player.distanceTo(target);

        if (target.isGliding()) {
            predictionTicks = MathHelper.clamp(playerDist * 0.4, 3.0, 10.0);
        } else {
            predictionTicks = MathHelper.clamp(playerDist * 0.3, 2.0, 6.0);
        }

        return target.getPos().add(
                vel.x * predictionTicks,
                2.0 + vel.y * predictionTicks * 0.3,
                vel.z * predictionTicks
        );
    }

    private Vec3d handleMatrix(Vec3d dir, double dist, double horizontalDist, LivingEntity target) {
        double spd = speed.getValue();
        double targetDist = distance.getValue();

        if (horizontalDist > targetDist * 3) {
            return dir.normalize().multiply(spd * 0.4);
        } else if (horizontalDist > targetDist * 1.5) {
            double factor = horizontalDist / (targetDist * 3);
            return dir.normalize().multiply(spd * 0.3 * factor);
        } else if (horizontalDist > targetDist) {
            double factor = (horizontalDist - targetDist) / (targetDist * 0.5);
            return dir.normalize().multiply(spd * 0.15 * factor);
        } else {
            Vec3d orbit = getOrbitVector(target, targetDist * 0.8);
            return orbit.multiply(spd * 0.12);
        }
    }

    private Vec3d handleGrim(Vec3d dir, double dist, double horizontalDist, LivingEntity target) {
        double spd = speed.getValue();
        double targetDist = distance.getValue();

        if (horizontalDist > targetDist * 2) {
            return dir.normalize().multiply(spd * 0.22);
        } else if (horizontalDist > targetDist) {
            double factor = (horizontalDist - targetDist) / targetDist;
            return dir.normalize().multiply(spd * 0.12 * factor);
        } else {
            Vec3d orbit = getOrbitVector(target, targetDist * 0.7);
            return orbit.multiply(spd * 0.08);
        }
    }

    private Vec3d handleHvH(Vec3d dir, double dist, double horizontalDist, LivingEntity target) {
        double spd = speed.getValue();
        double targetDist = distance.getValue();
        Vec3d base = dir.normalize();

        if (horizontalDist > targetDist * 2) {
            if (tickCounter % 3 == 0) {
                return base.multiply(spd * hvhJitter.getValue() * 0.4);
            }
            return base.multiply(spd * 0.35);
        }

        double angle = tickCounter * 1.2;
        double spiralStrength = MathHelper.clamp(horizontalDist * 0.2, 0.3, 1.5);
        Vec3d spiral = new Vec3d(
                Math.cos(angle) * spiralStrength,
                Math.sin(angle * 0.5) * 0.15,
                Math.sin(angle) * spiralStrength
        );

        if (tickCounter % 4 == 0) {
            return base.add(spiral).normalize().multiply(spd * hvhJitter.getValue() * 0.35);
        }

        return base.add(spiral).normalize().multiply(spd * 0.3);
    }

    private Vec3d getOrbitVector(LivingEntity target, double radius) {
        Vec3d toTarget = target.getPos().subtract(mc.player.getPos());
        double currentDist = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);

        double orbitAngle = tickCounter * 0.12;
        double orbitX = target.getX() + Math.cos(orbitAngle) * radius;
        double orbitZ = target.getZ() + Math.sin(orbitAngle) * radius;

        Vec3d orbitPoint = new Vec3d(orbitX, target.getY() + 2.0, orbitZ);
        Vec3d toOrbit = orbitPoint.subtract(mc.player.getPos());

        if (currentDist < radius * 0.5) {
            Vec3d awayFromTarget = mc.player.getPos().subtract(target.getPos()).normalize();
            return toOrbit.normalize().add(awayFromTarget.multiply(0.3)).normalize();
        }

        return toOrbit.normalize();
    }

    private void holdAltitude() {
        Vec3d vel = mc.player.getVelocity();
        if (vel.y < -0.05) {
            mc.player.setVelocity(vel.x * 0.98, vel.y * 0.5, vel.z * 0.98);
        }
    }

    private void applyMotion(Vec3d motion) {
        if (motion.lengthSquared() < 0.0001) return;

        Vec3d vel = mc.player.getVelocity();

        double lerp;
        String selected = mode.getSelected();
        if (selected.equals("Grim")) {
            lerp = 0.12;
        } else if (selected.equals("HvH")) {
            lerp = 0.28;
        } else {
            lerp = 0.2;
        }

        double newX = MathHelper.lerp(lerp, vel.x, motion.x);
        double newZ = MathHelper.lerp(lerp, vel.z, motion.z);
        double newY = MathHelper.lerp(lerp * 0.6, vel.y, motion.y);

        mc.player.setVelocity(newX, newY, newZ);
    }

    private void handleRocket(double horizontalDist) {
        if (System.currentTimeMillis() - lastRocketTime < 2000) return;

        double currentSpeed = mc.player.getVelocity().horizontalLength();
        if (currentSpeed > 1.2) return;
        if (horizontalDist < distance.getValue() * 1.5) return;

        if (mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
            PlayerInteractionHelper.interactItem(Hand.OFF_HAND);
            lastRocketTime = System.currentTimeMillis();
            return;
        }

        int rocketSlot = findRocketSlot();
        if (rocketSlot == -1) return;

        int previousSlot = mc.player.getInventory().selectedSlot;
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(rocketSlot));
        PlayerInteractionHelper.interactItem(Hand.MAIN_HAND);
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
        lastRocketTime = System.currentTimeMillis();
    }

    private int findRocketSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) {
                return i;
            }
        }
        return -1;
    }
}