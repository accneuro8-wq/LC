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
import fun.lumis.utils.interactions.simulate.Simulations;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Speed extends Module {

    private static final double EPSILON = 0.001;
    private static final double GRIM_BASE_FORCE = 0.08;
    private static final double ENTITY_SPEED_FACTOR = 0.01;
    private static final double NORMAL_SPEED_DIVISOR = 3.0;
    private static final float DEG_TO_RAD = 0.017453292f;

    public static Speed getInstance() {
        return Instance.get(Speed.class);
    }

    SelectSetting mode = new SelectSetting("Режим", "Выберите режим скорости")
            .value("Grim", "Entity", "Normal")
            .selected("Grim");

    SliderSettings speed = new SliderSettings("Скорость", "Настройка скорости передвижения")
            .range(1.0f, 20.0f)
            .setValue(8.0f);

    SliderSettings range = new SliderSettings("Дальность", "Дальность поиска ближайшей цели")
            .range(0.5f, 10.0f)
            .setValue(3.0f)
            .visible(() -> mode.isSelected("Entity"));

    SliderSettings expand = new SliderSettings("Расширение", "Расширение хитбокса для проверки коллизий")
            .range(0.1f, 2.0f)
            .setValue(0.5f);

    BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", "Учитывать только игроков")
            .setValue(true);

    BooleanSetting requireMoving = new BooleanSetting("Требуется движение", "Работает только при движении")
            .setValue(true);

    BooleanSetting auraBoost = new BooleanSetting("Усиление Aura", "Увеличивает дистанцию ускорения до цели в Aura")
            .setValue(true)
            .visible(() -> mode.isSelected("Grim"));

    SliderSettings auraStrength = new SliderSettings("Сила усиления", "Фактор умножения до цели")
            .range(1.0f, 6.0f)
            .setValue(1.5f)
            .visible(() -> mode.isSelected("Grim") && auraBoost.isValue());

    SliderSettings maxBoost = new SliderSettings("Макс. буст", "Максимальная добавочная скорость за тик")
            .range(0.01f, 0.5f)
            .setValue(0.15f)
            .visible(() -> !mode.isSelected("Normal"));

    public Speed() {
        super("Speed", "Speed", ModuleCategory.MOVEMENT);
        setup(mode, speed, range, expand, maxBoost, onlyPlayers, requireMoving, auraBoost, auraStrength);
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isGliding()) return;
        if (mc.player.isTouchingWater() || mc.player.isInLava()) return;
        if (requireMoving.isValue() && !Simulations.hasPlayerMovement()) return;

        String selected = mode.getSelected();
        if (selected.equals("Grim")) {
            handleGrimMode();
        } else if (selected.equals("Entity")) {
            handleEntityMode();
        } else if (selected.equals("Normal")) {
            handleNormalMode();
        }
    }

    private void handleGrimMode() {
        float effectiveExpand = expand.getValue();

        LivingEntity auraTarget = getAuraTarget();
        boolean auraActive = auraBoost.isValue() && auraTarget != null;

        if (auraActive && auraTarget.isSprinting() && mc.player.isSprinting()) {
            effectiveExpand = auraStrength.getValue();
        }

        List<Entity> nearby = collectNearbyEntities(effectiveExpand);
        if (nearby.isEmpty()) return;

        double totalWeight = 0;
        Vec3d playerPos = mc.player.getPos();
        double maxDistSq = effectiveExpand * effectiveExpand * 4;

        for (Entity entity : nearby) {
            double distSq = playerPos.squaredDistanceTo(entity.getPos());
            double proximity = 1.0 - MathHelper.clamp(distSq / maxDistSq, 0, 0.8);

            double entityWeight = GRIM_BASE_FORCE * proximity;

            if (entity instanceof PlayerEntity) {
                entityWeight *= 1.2;
            }

            if (auraActive && entity == auraTarget) {
                entityWeight *= 1.5;
            }

            totalWeight += entityWeight;
        }

        double clampedForce = Math.min(totalWeight, maxBoost.getValue());
        if (clampedForce < EPSILON) return;

        if (auraActive) {
            Vec3d toTarget = directionTo(playerPos, auraTarget.getPos());
            Vec3d moveDir = getMoveDirection();

            double blendFactor = 0.3;
            double finalX = MathHelper.lerp(blendFactor, moveDir.x, toTarget.x);
            double finalZ = MathHelper.lerp(blendFactor, moveDir.z, toTarget.z);

            double len = Math.sqrt(finalX * finalX + finalZ * finalZ);
            if (len > EPSILON) {
                finalX = finalX / len * clampedForce;
                finalZ = finalZ / len * clampedForce;
            }

            mc.player.addVelocity(finalX, 0, finalZ);
        } else {
            double[] motion = Simulations.forward(clampedForce);
            mc.player.addVelocity(motion[0], 0, motion[1]);
        }
    }

    private void handleEntityMode() {
        List<Entity> nearby = collectNearbyEntities(expand.getValue());
        if (nearby.isEmpty()) return;

        Entity nearest = findNearest(nearby);
        if (nearest == null) return;

        double distSq = mc.player.squaredDistanceTo(nearest);
        double maxRangeSq = range.getValue() * range.getValue();
        if (distSq > maxRangeSq) return;

        double dist = Math.sqrt(distSq);
        double distanceFactor = 1.0 - dist / range.getValue();
        distanceFactor = MathHelper.clamp(distanceFactor, 0.2, 1.0);
        distanceFactor = distanceFactor * distanceFactor;

        double collisionWeight = Math.min(nearby.size(), 5) * ENTITY_SPEED_FACTOR;
        double finalSpeed = speed.getValue() * collisionWeight * distanceFactor;
        finalSpeed = Math.min(finalSpeed, maxBoost.getValue());

        if (finalSpeed < EPSILON) return;

        Vec3d toTarget = directionTo(mc.player.getPos(), nearest.getPos());

        Vec3d targetVel = nearest.getVelocity();
        if (targetVel != null && targetVel.horizontalLength() > 0.05) {
            Vec3d predictedPos = nearest.getPos().add(targetVel.x * 3, 0, targetVel.z * 3);
            toTarget = directionTo(mc.player.getPos(), predictedPos);
        }

        mc.player.addVelocity(toTarget.x * finalSpeed, 0, toTarget.z * finalSpeed);
    }

    private void handleNormalMode() {
        double targetSpeed = speed.getValue() / NORMAL_SPEED_DIVISOR;
        double currentSpeed = mc.player.getVelocity().horizontalLength();

        if (currentSpeed >= targetSpeed) return;

        double deficit = targetSpeed - currentSpeed;
        double acceleration = Math.min(deficit, targetSpeed * 0.3);

        Simulations.setVelocity(currentSpeed + acceleration);
    }

    private List<Entity> collectNearbyEntities(float expandRange) {
        if (mc.player == null || mc.world == null) return new ArrayList<>();

        Box expandedBox = mc.player.getBoundingBox().expand(expandRange);
        List<Entity> result = new ArrayList<>();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == null) continue;
            if (!isValidEntity(entity)) continue;
            if (!expandedBox.intersects(entity.getBoundingBox())) continue;
            result.add(entity);
        }

        return result;
    }

    private boolean isValidEntity(Entity entity) {
        if (entity == mc.player) return false;
        if (!entity.isAlive()) return false;
        if (entity instanceof ArmorStandEntity) return false;
        if (entity instanceof SlimeEntity) return false;
        if (entity.isSpectator()) return false;

        if (onlyPlayers.isValue()) {
            return entity instanceof PlayerEntity;
        }

        return entity instanceof LivingEntity || entity instanceof BoatEntity;
    }

    private Entity findNearest(List<Entity> entities) {
        if (mc.player == null) return null;

        Entity nearest = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Entity entity : entities) {
            double distSq = mc.player.squaredDistanceTo(entity);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                nearest = entity;
            }
        }

        return nearest;
    }

    private Vec3d directionTo(Vec3d from, Vec3d to) {
        if (from == null || to == null) return Vec3d.ZERO;
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < EPSILON) return Vec3d.ZERO;
        return new Vec3d(dx / length, 0, dz / length);
    }

    private Vec3d getMoveDirection() {
        if (mc.player == null) return Vec3d.ZERO;

        float yaw = mc.player.getYaw();
        float forward = mc.player.input.movementForward;
        float strafe = mc.player.input.movementSideways;

        if (Math.abs(forward) < EPSILON && Math.abs(strafe) < EPSILON) {
            float rad = yaw * DEG_TO_RAD;
            return new Vec3d(-Math.sin(rad), 0, Math.cos(rad));
        }

        float moveAngle = yaw;
        if (forward != 0) {
            if (strafe > 0) moveAngle -= forward > 0 ? 45 : -45;
            else if (strafe < 0) moveAngle += forward > 0 ? 45 : -45;
            if (forward < 0) moveAngle += 180;
        } else {
            if (strafe > 0) moveAngle -= 90;
            else if (strafe < 0) moveAngle += 90;
        }

        float rad = moveAngle * DEG_TO_RAD;
        return new Vec3d(-Math.sin(rad), 0, Math.cos(rad));
    }

    private LivingEntity getAuraTarget() {
        Aura aura = Aura.getInstance();
        if (aura == null) return null;
        if (!aura.isState()) return null;
        LivingEntity target = aura.getTarget();
        if (target == null) return null;
        if (!target.isAlive()) return null;
        return target;
    }
}