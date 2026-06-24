package fun.lumis.utils.features.aura.rotations.impl;

import fun.lumis.utils.features.aura.rotations.constructor.RotateConstructor;
import fun.lumis.utils.features.aura.warp.Turns;
import fun.lumis.utils.math.calc.Calculate;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.Random;

public class HWAngle extends RotateConstructor {
    private final Random random = new Random();
    private float lastYaw, lastPitch;
    private long lastTargetId = -1;
    private boolean firstLock = true;
    private float currentSmooth = 12.0f;
    private long lastTwitchTime = 0;
    private float circlePhase = 0.0f;
    private double offsetX, offsetY, offsetZ;
    private long lastOffsetUpdate = 0;

    public HWAngle() {
        super("HWAngle");
    }

    @Override
    public Turns limitAngleChange(Turns currentTurns, Turns targetTurns, Vec3d vec3d, Entity entity) {
        if (mc.player == null || entity == null) return currentTurns;

        boolean targetChanged = entity.getId() != lastTargetId;
        if (targetChanged) {
            lastYaw = currentTurns.getYaw();
            lastPitch = currentTurns.getPitch();
            lastTargetId = entity.getId();
            firstLock = true;
            currentSmooth = 10.0f;
            circlePhase = 0.0f;
            updateOffset(entity);
        }

        circlePhase += 0.14f + random.nextFloat() * 0.06f;

        if (System.currentTimeMillis() - lastOffsetUpdate > 60 + random.nextInt(80)) {
            updateOffset(entity);
            lastOffsetUpdate = System.currentTimeMillis();
        }

        double dist = mc.player.distanceTo(entity);
        double horizSpeed = Math.hypot(entity.getX() - entity.prevX, entity.getZ() - entity.prevZ);
        double verticalMotion = entity.getY() - entity.prevY;

        double predictFactor = 1.0 + horizSpeed * (2.6 + random.nextDouble() * 1.6) + dist * 0.15;
        predictFactor = MathHelper.clamp(predictFactor, 0.8, 2.6);

        double predictedX = entity.getX() + (entity.getX() - entity.prevX) * predictFactor + offsetX;
        double predictedY = entity.getY() + verticalMotion * 0.9 + (entity.getEyeHeight(entity.getPose()) * (0.78 + random.nextDouble() * 0.16)) + offsetY;
        double predictedZ = entity.getZ() + (entity.getZ() - entity.prevZ) * predictFactor + offsetZ;

        Vec3d eye = mc.player.getEyePos();
        double dx = predictedX - eye.x;
        double dy = predictedY - eye.y;
        double dz = predictedZ - eye.z;
        double horizDist = Math.sqrt(dx * dx + dz * dz);

        float idealYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float idealPitch = (float) -Math.toDegrees(Math.atan2(dy, horizDist));

        float yawDiff = MathHelper.wrapDegrees(idealYaw - lastYaw);
        float pitchDiff = idealPitch - lastPitch;

        float totalAngle = (float) Math.hypot(yawDiff, pitchDiff);

        float stickiness = totalAngle < 7.0f ? 0.45f : 0.78f;
        float baseSpeed = totalAngle * stickiness;

        float maxSpeed = firstLock ? 140.0f : 105.0f;
        float minSpeed = firstLock ? 24.0f : 12.0f;
        float speed = MathHelper.clamp(baseSpeed, minSpeed, maxSpeed);

        float smoothRandom = 0.28f + random.nextFloat() * 0.08f;
        currentSmooth = MathHelper.lerp(smoothRandom, currentSmooth, 22.0f + random.nextFloat() * 5.0f);
        speed *= currentSmooth / 20.0f;

        float nextYaw = lastYaw + MathHelper.clamp(yawDiff, -speed, speed);
        float nextPitch = lastPitch + MathHelper.clamp(pitchDiff, -speed, speed);

        float circleAmpRandom = 0.85f + random.nextFloat() * 0.4f;
        float circleYaw = (float) (Math.sin(circlePhase * 2.6) * (10.5 * circleAmpRandom) + Math.cos(circlePhase * 1.9) * (4.5 * circleAmpRandom));
        float circlePitch = (float) (Math.cos(circlePhase * 2.2) * (7.2 * circleAmpRandom) + Math.sin(circlePhase * 3.5) * (2.8 * circleAmpRandom));
        nextYaw += circleYaw * 0.38f;
        nextPitch += circlePitch * 0.30f;

        if (System.currentTimeMillis() - lastTwitchTime > 7 + random.nextInt(8)) {
            float twitchRandom = 0.25f + random.nextFloat() * 0.17f;
            nextYaw += (random.nextFloat() - 0.5f) * twitchRandom;
            nextPitch += (random.nextFloat() - 0.5f) * twitchRandom;
            lastTwitchTime = System.currentTimeMillis();
        }

        firstLock = false;

        double gcd = Calculate.computeGcd();
        double gcdMod = 0.985 + random.nextDouble() * 0.05;

        double deltaYaw = nextYaw - lastYaw;
        double deltaPitch = nextPitch - lastPitch;

        float adjustedYaw = (float) (deltaYaw - (deltaYaw % (gcd * gcdMod)));
        float adjustedPitch = (float) (deltaPitch - (deltaPitch % (gcd * gcdMod)));

        lastYaw += adjustedYaw;
        lastPitch += adjustedPitch;
        lastPitch = MathHelper.clamp(lastPitch, -89.0f, 89.0f);

        return new Turns(lastYaw, lastPitch);
    }

    private void updateOffset(Entity entity) {
        float wRandom = 0.25f + random.nextFloat() * 0.2f;
        float hRandom = 0.15f + random.nextFloat() * 0.15f;
        float w = entity.getWidth() * wRandom;
        float h = entity.getHeight() * hRandom;
        offsetX = (random.nextDouble() - 0.5) * w + (random.nextDouble() - 0.5) * 0.05;
        offsetY = (random.nextDouble() - 0.5) * h * 0.8 + (random.nextDouble() - 0.5) * 0.03;
        offsetZ = (random.nextDouble() - 0.5) * w + (random.nextDouble() - 0.5) * 0.05;
    }

    public void reset() {
        lastTargetId = -1;
        firstLock = true;
    }

    @Override
    public Vec3d randomValue() {
        return Vec3d.ZERO;
    }
}