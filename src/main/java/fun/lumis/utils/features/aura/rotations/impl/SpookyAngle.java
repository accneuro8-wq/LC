package fun.lumis.utils.features.aura.rotations.impl;

import fun.lumis.utils.features.aura.rotations.constructor.RotateConstructor;
import fun.lumis.utils.features.aura.warp.Turns;
import fun.lumis.utils.math.calc.Calculate;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class SpookyAngle extends RotateConstructor {
    private final Random random = new Random();
    private float lastYaw, lastPitch;
    private long lastTargetId = -1;
    private double offsetX, offsetY, offsetZ;
    private long lastOffsetUpdate = 0;
    private float currentSmooth = 14.0f;
    private boolean firstLock = true;
    private float circlePhase = 0.0f;
    private long lastTwitch = 0;

    public SpookyAngle() {
        super("Spooky");
    }

    @Override
    public Turns limitAngleChange(Turns currentTurns, Turns targetTurns, Vec3d vec3d, Entity entity) {
        if (mc.player == null || entity == null) return currentTurns;

        if (entity.getId() != lastTargetId) {
            lastYaw = currentTurns.getYaw();
            lastPitch = currentTurns.getPitch();
            lastTargetId = entity.getId();
            firstLock = true;
            currentSmooth = 12.0f + random.nextFloat() * 4.0f;
            circlePhase = random.nextFloat() * 6.28f;
            updateOffset(entity);
        }

        circlePhase += 0.16f + random.nextFloat() * 0.07f;

        if (System.currentTimeMillis() - lastOffsetUpdate > 50 + random.nextInt(80)) {
            updateOffset(entity);
            lastOffsetUpdate = System.currentTimeMillis();
        }

        double distance = mc.player.distanceTo(entity);
        double horizSpeed = Math.hypot(entity.getX() - entity.prevX, entity.getZ() - entity.prevZ);
        double verticalMotion = entity.getY() - entity.prevY;
        double predictFactor = 1.0 + horizSpeed * (2.8 + random.nextDouble() * 1.8) + distance * (0.12 + random.nextDouble() * 0.08);
        predictFactor = MathHelper.clamp(predictFactor, 0.7, 2.8);

        double predictedX = entity.getX() + (entity.getX() - entity.prevX) * predictFactor + offsetX;
        double predictedY = entity.getY() + verticalMotion * (0.85 + random.nextDouble() * 0.2) + (entity.getEyeHeight(entity.getPose()) * (0.78 + random.nextDouble() * 0.18)) + offsetY;
        double predictedZ = entity.getZ() + (entity.getZ() - entity.prevZ) * predictFactor + offsetZ;

        Vec3d eyePos = mc.player.getEyePos();
        double dx = predictedX - eyePos.x;
        double dy = predictedY - eyePos.y;
        double dz = predictedZ - eyePos.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        float idealYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float idealPitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));

        float yawDiff = MathHelper.wrapDegrees(idealYaw - lastYaw);
        float pitchDiff = idealPitch - lastPitch;

        float angleDist = (float) Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);

        float stickinessRandom = 0.42f + random.nextFloat() * 0.12f;
        float stickiness = angleDist < 6.5f ? stickinessRandom : 0.78f + random.nextFloat() * 0.12f;

        float baseSpeed = angleDist * stickiness;
        float maxSpeed = firstLock ? 180.0f : 120.0f;
        float minSpeed = firstLock ? 35.0f : 16.0f;
        float speed = MathHelper.clamp(baseSpeed, minSpeed, maxSpeed);

        float smoothRandom = 0.28f + random.nextFloat() * 0.1f;
        currentSmooth = MathHelper.lerp(smoothRandom, currentSmooth, 26.0f + random.nextFloat() * 7.0f);
        speed *= currentSmooth / 20.0f;

        float nextYaw = lastYaw + MathHelper.clamp(yawDiff, -speed, speed);
        float nextPitch = lastPitch + MathHelper.clamp(pitchDiff, -speed, speed);

        float circleAmpRandom = 0.85f + random.nextFloat() * 0.5f;
        float circleYaw = (float) (Math.sin(circlePhase * (2.8 + random.nextDouble() * 0.6)) * (11.0 * circleAmpRandom) + Math.cos(circlePhase * (2.2 + random.nextDouble() * 0.5)) * (4.8 * circleAmpRandom));
        float circlePitch = (float) (Math.cos(circlePhase * (2.3 + random.nextDouble() * 0.5)) * (7.5 * circleAmpRandom) + Math.sin(circlePhase * (3.6 + random.nextDouble() * 0.8)) * (3.0 * circleAmpRandom));

        nextYaw += circleYaw * (0.40f + random.nextFloat() * 0.1f);
        nextPitch += circlePitch * (0.32f + random.nextFloat() * 0.08f);

        if (System.currentTimeMillis() - lastTwitch > 7 + random.nextInt(9)) {
            float twitchRandom = 0.28f + random.nextFloat() * 0.17f;
            nextYaw += (random.nextFloat() - 0.5f) * twitchRandom;
            nextPitch += (random.nextFloat() - 0.5f) * twitchRandom;
            lastTwitch = System.currentTimeMillis();
        }

        firstLock = false;

        double gcd = Calculate.computeGcd();
        double gcdMod = 0.982 + random.nextDouble() * 0.06;
        nextYaw -= (nextYaw - lastYaw) % (gcd * gcdMod);
        nextPitch -= (nextPitch - lastPitch) % (gcd * gcdMod);

        lastYaw = nextYaw;
        lastPitch = MathHelper.clamp(nextPitch, -89.0f, 89.0f);

        return new Turns(lastYaw, lastPitch);
    }

    private void updateOffset(Entity entity) {
        float wRandom = 0.3f + random.nextFloat() * 0.2f;
        float hRandom = 0.2f + random.nextFloat() * 0.12f;
        float w = entity.getWidth() * wRandom;
        float h = entity.getHeight() * hRandom;
        offsetX = (random.nextDouble() - 0.5) * w + (random.nextDouble() - 0.5) * 0.08;
        offsetY = (random.nextDouble() - 0.5) * h * (0.8 + random.nextDouble() * 0.2) + (random.nextDouble() - 0.5) * 0.06;
        offsetZ = (random.nextDouble() - 0.5) * w + (random.nextDouble() - 0.5) * 0.08;
    }

    @Override
    public Vec3d randomValue() {
        return Vec3d.ZERO;
    }

    public void reset() {
        lastYaw = 0;
        lastPitch = 0;
        lastTargetId = -1;
        offsetX = 0;
        offsetY = 0;
        offsetZ = 0;
        lastOffsetUpdate = 0;
        currentSmooth = 14.0f;
        firstLock = true;
        circlePhase = 0.0f;
        lastTwitch = 0;
    }
}