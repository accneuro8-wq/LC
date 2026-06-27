package fun.lumis.utils.features.aura.rotations.impl;

import fun.lumis.utils.features.aura.rotations.constructor.RotateConstructor;
import fun.lumis.utils.features.aura.warp.Turns;
import fun.lumis.utils.math.calc.Calculate;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * SpookyTime (full) — универсальная ротация под Grim на SpookyTime 1.21.
 *
 * В отличие от SpookytimeDuel (только ближний бой 1v1), эта версия покрывает
 * весь бой: ближний/средний/дальний, быстрые цели, кб-абузы, смену целей.
 *
 * Обходы Grim:
 *  - GCD-выравнивание дельт (AimA / GCDCheck).
 *  - Ease-in-out: плавный разгон и торможение, без мгновенных снапов (RotationSpeed/AimB).
 *  - Адаптивное упреждение по горизонтальной скорости цели и дистанции (PredictionBypass).
 *  - Человеческий sway + редкие микро-твичи + мягкий дрейф точки прицеливания внутри хитбокса (AimC/Aim heuristics).
 *  - Жёсткий кламп питча и скорости; плавный перехват при смене цели (без телепортов угла).
 */
public class SpookyFullAngle extends RotateConstructor {
    private final Random random = new Random();

    private float lastYaw, lastPitch;
    private float yawVel, pitchVel;
    private long lastTargetId = -1;
    private int ticksOnTarget = 0;
    private float swayPhase = 0f;
    private long lastTwitch = 0;

    // мягкий дрейф точки прицеливания внутри хитбокса
    private double offX, offY, offZ;
    private double tOffX, tOffY, tOffZ;
    private long lastOffsetTime = 0;

    public SpookyFullAngle() {
        super("SpookyTime");
    }

    @Override
    public Turns limitAngleChange(Turns current, Turns target, Vec3d vec, Entity entity) {
        if (mc.player == null || entity == null || !entity.isAlive()) return current;

        if (entity.getId() != lastTargetId) {
            lastYaw = current.getYaw();
            lastPitch = current.getPitch();
            lastTargetId = entity.getId();
            ticksOnTarget = 0;
            yawVel *= 0.2f;
            pitchVel *= 0.2f;
            swayPhase = random.nextFloat() * 6.28f;
            newOffset(entity);
        }
        ticksOnTarget++;
        swayPhase += 0.17f + random.nextFloat() * 0.05f;

        // скорость цели
        double dxV = entity.getX() - entity.prevX;
        double dyV = entity.getY() - entity.prevY;
        double dzV = entity.getZ() - entity.prevZ;

        double distance = mc.player.distanceTo(entity);
        double horizSpeed = Math.hypot(dxV, dzV);

        // адаптивное упреждение: дальше цель/быстрее движется -> больше предикт
        double predict = MathHelper.clamp(1.0 + horizSpeed * (1.4 + distance * 0.15), 1.0, 2.6);

        updateOffset(entity);

        Vec3d eye = mc.player.getEyePos();
        double aimHeight = entity.getEyeHeight(entity.getPose()) * (0.80 + random.nextDouble() * 0.10);

        double px = entity.getX() + dxV * predict + offX;
        double py = entity.getY() + aimHeight + dyV * 0.5 + offY;
        double pz = entity.getZ() + dzV * predict + offZ;

        double dx = px - eye.x;
        double dy = py - eye.y;
        double dz = pz - eye.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);

        float idealYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float idealPitch = MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, hDist)), -89f, 89f);

        float yawDiff = MathHelper.wrapDegrees(idealYaw - lastYaw);
        float pitchDiff = idealPitch - lastPitch;
        float angleDist = (float) Math.hypot(yawDiff, pitchDiff);

        // ease-in-out: разгон по мере удаления угла, плавное торможение у цели
        float ease = (float) (1.0 - Math.exp(-angleDist / 26.0)); // 0..1
        float accel = 0.28f + ease * 0.34f;       // 0.28 .. 0.62
        float maxSpeed = 22f + ease * 40f;        // 22 .. 62 deg/tick

        // мягкий перехват свежей цели — без мгновенного снапа
        if (ticksOnTarget < 5) {
            float u = ticksOnTarget / 5f;
            accel *= 0.6f + u * 0.4f;
            maxSpeed *= 0.65f + u * 0.35f;
        }
        // у самой цели — медленно и точно
        if (angleDist < 4f) {
            float f = angleDist / 4f;
            accel *= 0.5f + f * 0.5f;
            maxSpeed *= 0.55f + f * 0.45f;
        }
        // ближний бой — чуть мягче, дальний — резвее (но в рамках клампа)
        if (distance < 3.0) { accel *= 0.92f; maxSpeed *= 0.85f; }

        accel *= 0.94f + random.nextFloat() * 0.12f;
        float friction = 0.48f + random.nextFloat() * 0.07f;

        yawVel = yawVel * friction + (yawDiff * accel) * (1f - friction);
        pitchVel = pitchVel * friction + (pitchDiff * accel) * (1f - friction);

        yawVel = MathHelper.clamp(yawVel, -maxSpeed, maxSpeed);
        pitchVel = MathHelper.clamp(pitchVel, -maxSpeed * 0.7f, maxSpeed * 0.7f);

        float nextYaw = lastYaw + yawVel;
        float nextPitch = lastPitch + pitchVel;

        // человеческий sway (падает в ближнем бою)
        float swayAmp = distance < 3.0 ? 0.5f : 1.0f;
        nextYaw += (float) (Math.sin(swayPhase * 1.7) * 1.2 + Math.cos(swayPhase * 2.3) * 0.5) * swayAmp * 0.35f;
        nextPitch += (float) (Math.cos(swayPhase * 1.5) * 0.8) * swayAmp * 0.28f;

        // редкий микро-твич против «робота»
        if (System.currentTimeMillis() - lastTwitch > 35 + random.nextInt(45)) {
            nextYaw += (random.nextFloat() - 0.5f) * 0.28f;
            nextPitch += (random.nextFloat() - 0.5f) * 0.18f;
            lastTwitch = System.currentTimeMillis();
        }

        // GCD-выравнивание (обязательно для Grim)
        double gcd = Calculate.computeGcd();
        if (gcd > 0.0) {
            float dYaw = (float) (Math.round((nextYaw - lastYaw) / gcd) * gcd);
            float dPitch = (float) (Math.round((nextPitch - lastPitch) / gcd) * gcd);
            nextYaw = lastYaw + dYaw;
            nextPitch = lastPitch + dPitch;
        }

        lastYaw = nextYaw;
        lastPitch = MathHelper.clamp(nextPitch, -89f, 89f);

        return new Turns(lastYaw, lastPitch);
    }

    private void updateOffset(Entity e) {
        if (System.currentTimeMillis() - lastOffsetTime > 160 + random.nextInt(140)) {
            tOffX = random.nextGaussian() * 0.11 * e.getWidth();
            tOffY = random.nextGaussian() * 0.08 * e.getHeight();
            tOffZ = random.nextGaussian() * 0.11 * e.getWidth();
            lastOffsetTime = System.currentTimeMillis();
        }
        offX += (tOffX - offX) * 0.12;
        offY += (tOffY - offY) * 0.12;
        offZ += (tOffZ - offZ) * 0.12;
    }

    private void newOffset(Entity e) {
        offX = tOffX = random.nextGaussian() * 0.07 * e.getWidth();
        offY = tOffY = random.nextGaussian() * 0.05 * e.getHeight();
        offZ = tOffZ = random.nextGaussian() * 0.07 * e.getWidth();
        lastOffsetTime = System.currentTimeMillis();
    }

    @Override
    public Vec3d randomValue() {
        return Vec3d.ZERO;
    }

    public void reset() {
        lastYaw = 0;
        lastPitch = 0;
        yawVel = pitchVel = 0;
        lastTargetId = -1;
        ticksOnTarget = 0;
        swayPhase = 0f;
        lastTwitch = 0;
    }
}
