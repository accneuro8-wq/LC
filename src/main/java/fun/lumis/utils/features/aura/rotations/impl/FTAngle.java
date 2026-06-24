package fun.lumis.utils.features.aura.rotations.impl;

import fun.lumis.utils.features.aura.rotations.constructor.RotateConstructor;
import fun.lumis.utils.features.aura.warp.Turns;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class FTAngle extends RotateConstructor {

    private final Random random = new Random();

    // Состояние
    private float lastYaw, lastPitch;
    private float yawVel, pitchVel;
    private long targetId = -1;
    private int ticksOnTarget = 0;

    // Offset и предикт
    private double offX, offY, offZ;
    private double targetOffX, targetOffY, targetOffZ;
    private Vec3d targetVel = Vec3d.ZERO;
    private Vec3d prevPos = null;
    private long lastOffsetTime = 0;

    // Overshoot
    private float overshootYaw, overshootPitch;

    public FTAngle() {
        super("FT");
    }

    @Override
    public Turns limitAngleChange(Turns current, Turns target, Vec3d vec, Entity entity) {
        if (mc.player == null || entity == null) return current;

        // Смена цели
        if (entity.getId() != targetId) {
            lastYaw = current.getYaw();
            lastPitch = current.getPitch();
            targetId = entity.getId();
            ticksOnTarget = 0;
            yawVel *= 0.3f;
            pitchVel *= 0.3f;
            prevPos = entity.getPos();
            targetVel = Vec3d.ZERO;
            resetOffset(entity);
            if (random.nextFloat() < 0.4f) triggerOvershoot(0.5f);
        }

        ticksOnTarget++;

        // Velocity цели
        Vec3d pos = entity.getPos();
        if (prevPos != null) {
            targetVel = targetVel.multiply(0.7).add(pos.subtract(prevPos).multiply(0.3));
        }
        prevPos = pos;

        // Offset
        updateOffset(entity);

        // Идеальные углы
        double dist = mc.player.distanceTo(entity);
        double predFactor = Math.min(dist * 0.08, 0.5);
        Vec3d eye = mc.player.getEyePos();

        double px = entity.getX() + targetVel.x * predFactor + offX;
        double py = entity.getY() + targetVel.y * predFactor + entity.getEyeHeight(entity.getPose()) * 0.75 + offY;
        double pz = entity.getZ() + targetVel.z * predFactor + offZ;

        double dx = px - eye.x, dy = py - eye.y, dz = pz - eye.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);

        float idealYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float idealPitch = (float) -Math.toDegrees(Math.atan2(dy, hDist));

        // Дельта
        float yawDelta = MathHelper.wrapDegrees(idealYaw - lastYaw);
        float pitchDelta = idealPitch - lastPitch;
        float totalDelta = (float) Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);

        // Динамические параметры
        float accel = 0.45f, friction = 0.55f, maxSpd = 28f;

        if (ticksOnTarget < 8) { // Новая цель
            float u = 1f - ticksOnTarget / 8f;
            accel += u * 0.35f;
            maxSpd += u * 15f;
            friction -= u * 0.15f;
        }
        if (totalDelta > 30f) { // Большой угол
            float f = Math.min((totalDelta - 30f) / 60f, 1f);
            accel += f * 0.25f;
            maxSpd += f * 12f;
        } else if (totalDelta < 5f) { // Маленький угол
            float f = 1f - totalDelta / 5f;
            accel *= 1f - f * 0.4f;
            maxSpd *= 1f - f * 0.3f;
        }
        if (dist < 2.0) { // Близко
            accel *= 0.85f;
            maxSpd *= 0.8f;
        }

        // Рандомизация
        accel *= 0.92f + random.nextFloat() * 0.16f;
        friction *= 0.95f + random.nextFloat() * 0.1f;

        // Overshoot
        overshootYaw *= 0.85f;
        overshootPitch *= 0.85f;
        if (totalDelta > 15f && random.nextFloat() < 0.2f) triggerOvershoot(0.4f);
        if (totalDelta < 3f && totalDelta > 0.5f && random.nextFloat() < 0.12f) triggerOvershoot(0.2f);

        // Физика
        float targetYawVel = yawDelta * accel + overshootYaw;
        float targetPitchVel = pitchDelta * accel + overshootPitch;

        yawVel = yawVel * friction + targetYawVel * (1f - friction);
        pitchVel = pitchVel * friction + targetPitchVel * (1f - friction);

        yawVel = MathHelper.clamp(yawVel, -maxSpd, maxSpd);
        pitchVel = MathHelper.clamp(pitchVel, -maxSpd * 0.7f, maxSpd * 0.7f);

        // Микро-движения
        float microInt = totalDelta < 5f ? 0.12f : (totalDelta > 20f ? 0.03f : 0.06f);
        float microYaw = (float) (random.nextGaussian() * microInt);
        float microPitch = (float) (random.nextGaussian() * microInt * 0.5f);

        // Применение
        float nextYaw = lastYaw + yawVel + microYaw;
        float nextPitch = lastPitch + pitchVel + microPitch;

        // GCD
        double sens = mc.options.getMouseSensitivity().getValue();
        double f = sens * 0.6 + 0.2;
        double gcd = f * f * f * 1.2;

        lastYaw += (float) (Math.round((nextYaw - lastYaw) / gcd) * gcd);
        lastPitch += (float) (Math.round((nextPitch - lastPitch) / gcd) * gcd);
        lastPitch = MathHelper.clamp(lastPitch, -90f, 90f);

        return new Turns(lastYaw, lastPitch);
    }

    private void updateOffset(Entity e) {
        if (System.currentTimeMillis() - lastOffsetTime > 120 + random.nextInt(80)) {
            targetOffX = random.nextGaussian() * 0.15 * e.getWidth();
            targetOffY = random.nextGaussian() * 0.1 * e.getHeight();
            targetOffZ = random.nextGaussian() * 0.15 * e.getWidth();
            lastOffsetTime = System.currentTimeMillis();
        }
        offX += (targetOffX - offX) * 0.15;
        offY += (targetOffY - offY) * 0.15;
        offZ += (targetOffZ - offZ) * 0.15;
    }

    private void resetOffset(Entity e) {
        offX = targetOffX = random.nextGaussian() * 0.1 * e.getWidth();
        offY = targetOffY = random.nextGaussian() * 0.08 * e.getHeight();
        offZ = targetOffZ = random.nextGaussian() * 0.1 * e.getWidth();
        lastOffsetTime = System.currentTimeMillis();
    }

    private void triggerOvershoot(float intensity) {
        float mult = intensity * (0.5f + random.nextFloat() * 0.5f);
        overshootYaw = yawVel * mult;
        overshootPitch = pitchVel * mult * 0.6f;
    }

    public void reset() {
        targetId = -1;
        ticksOnTarget = 0;
        yawVel = pitchVel = 0;
        overshootYaw = overshootPitch = 0;
        prevPos = null;
        targetVel = Vec3d.ZERO;
    }

    @Override
    public Vec3d randomValue() {
        return Vec3d.ZERO;
    }
}