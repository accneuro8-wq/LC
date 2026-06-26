package fun.lumis.utils.features.aura.rotations.impl;

import fun.lumis.utils.features.aura.rotations.constructor.RotateConstructor;
import fun.lumis.utils.features.aura.warp.Turns;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Rotation profile tuned to bypass the Vodkacraft anticheat.
 * Humanized aim with velocity prediction, smooth acceleration curve,
 * hand inertia/jitter and GCD-correct mouse stepping.
 */
public class VodkacraftAngle extends RotateConstructor {

    private final Random random = new Random();

    private float lastYaw = 0f;
    private float lastPitch = 0f;
    private long targetId = -1;
    private int ticksOnTarget = 0;

    private Vec3d prevTargetPos = null;
    private Vec3d smoothedVelocity = Vec3d.ZERO;

    private float offsetYaw = 0f;
    private float offsetPitch = 0f;
    private int offsetLifetime = 0;
    private int offsetTick = 0;

    private float prevMouseDeltaYaw = 0f;
    private float prevMouseDeltaPitch = 0f;

    public VodkacraftAngle() {
        super("Vodkacraft");
    }

    @Override
    public Turns limitAngleChange(Turns currentTurns, Turns targetTurns, Vec3d vec3d, Entity entity) {
        if (mc.player == null || entity == null || !entity.isAlive()) {
            reset();
            return currentTurns;
        }

        // ====== Target switch ======
        if (entity.getId() != targetId) {
            lastYaw = currentTurns.getYaw();
            lastPitch = currentTurns.getPitch();
            targetId = entity.getId();
            ticksOnTarget = 0;
            prevTargetPos = entity.getPos();
            smoothedVelocity = Vec3d.ZERO;
            prevMouseDeltaYaw = 0f;
            prevMouseDeltaPitch = 0f;
            generateOffset(entity);
        }

        ticksOnTarget++;

        // ====== Target velocity prediction ======
        Vec3d currentPos = entity.getPos();
        if (prevTargetPos != null) {
            Vec3d rawVel = currentPos.subtract(prevTargetPos);
            smoothedVelocity = smoothedVelocity.multiply(0.6).add(rawVel.multiply(0.4));
        }
        prevTargetPos = currentPos;

        // ====== Offset refresh ======
        offsetTick++;
        if (offsetTick >= offsetLifetime) {
            generateOffset(entity);
        }

        // ====== Aim point ======
        float distance = mc.player.distanceTo(entity);
        double predScale = MathHelper.clamp(0.7 + distance * 0.1, 0.7, 1.8);

        double aimX = entity.getX() + smoothedVelocity.x * predScale;
        double aimY = entity.getY() + entity.getEyeHeight(entity.getPose()) * 0.72 + smoothedVelocity.y * predScale * 0.5;
        double aimZ = entity.getZ() + smoothedVelocity.z * predScale;

        Vec3d eyePos = mc.player.getEyePos();
        double dx = aimX - eyePos.x;
        double dy = aimY - eyePos.y;
        double dz = aimZ - eyePos.z;
        double horizDist = Math.sqrt(dx * dx + dz * dz);

        float idealYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f + offsetYaw;
        float idealPitch = (float) -Math.toDegrees(Math.atan2(dy, horizDist)) + offsetPitch;

        // ====== Delta ======
        float yawDelta = MathHelper.wrapDegrees(idealYaw - lastYaw);
        float pitchDelta = idealPitch - lastPitch;
        float totalAngle = (float) Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);

        // ====== Aim speed curve (smoother than RW for Vodkacraft) ======
        float baseSpeed;
        if (ticksOnTarget <= 2) {
            baseSpeed = 0.75f + random.nextFloat() * 0.1f;
        } else if (totalAngle > 40f) {
            baseSpeed = 0.6f + random.nextFloat() * 0.12f;
        } else if (totalAngle > 15f) {
            baseSpeed = 0.45f + random.nextFloat() * 0.13f;
        } else if (totalAngle > 5f) {
            baseSpeed = 0.3f + random.nextFloat() * 0.12f;
        } else {
            baseSpeed = 0.15f + random.nextFloat() * 0.1f;
        }

        if (distance < 2.0f) {
            baseSpeed *= 0.82f + random.nextFloat() * 0.1f;
        }

        float moveYaw = yawDelta * baseSpeed;
        float movePitch = pitchDelta * baseSpeed;

        // ====== Mouse inertia (real hand simulation) ======
        float inertia = 0.18f + random.nextFloat() * 0.1f;
        moveYaw = moveYaw * (1f - inertia) + prevMouseDeltaYaw * inertia;
        movePitch = movePitch * (1f - inertia) + prevMouseDeltaPitch * inertia;

        // ====== Speed inconsistency ======
        float speedJitter = 0.9f + random.nextFloat() * 0.18f;
        moveYaw *= speedJitter;
        movePitch *= (0.92f + random.nextFloat() * 0.14f);

        // ====== Max speed clamp ======
        float maxYawSpeed = 30f + random.nextFloat() * 10f;
        float maxPitchSpeed = 18f + random.nextFloat() * 6f;

        if (ticksOnTarget <= 2) {
            maxYawSpeed = 50f + random.nextFloat() * 12f;
            maxPitchSpeed = 30f + random.nextFloat() * 8f;
        }

        moveYaw = MathHelper.clamp(moveYaw, -maxYawSpeed, maxYawSpeed);
        movePitch = MathHelper.clamp(movePitch, -maxPitchSpeed, maxPitchSpeed);

        prevMouseDeltaYaw = moveYaw;
        prevMouseDeltaPitch = movePitch;

        // ====== Apply ======
        float nextYaw = lastYaw + moveYaw;
        float nextPitch = MathHelper.clamp(lastPitch + movePitch, -89.5f, 89.5f);

        // ====== GCD ======
        Turns result = applyGCD(new Turns(nextYaw, nextPitch), new Turns(lastYaw, lastPitch));

        lastYaw = result.getYaw();
        lastPitch = MathHelper.clamp(result.getPitch(), -89.5f, 89.5f);

        return new Turns(lastYaw, lastPitch);
    }

    private void generateOffset(Entity entity) {
        float w = entity.getWidth();
        float h = entity.getHeight();

        float maxYawOff = (float) Math.toDegrees(Math.atan2(w * 0.32, Math.max(mc.player.distanceTo(entity), 0.5)));
        float maxPitchOff = (float) Math.toDegrees(Math.atan2(h * 0.22, Math.max(mc.player.distanceTo(entity), 0.5)));

        offsetYaw = (float) (random.nextGaussian() * maxYawOff * 0.55);
        offsetPitch = (float) (random.nextGaussian() * maxPitchOff * 0.45);

        offsetLifetime = 10 + random.nextInt(12);
        offsetTick = 0;
    }

    private Turns applyGCD(Turns rotation, Turns prev) {
        double sensitivity = mc.options.getMouseSensitivity().getValue();
        double f = sensitivity * 0.6 + 0.2;
        double gcd = f * f * f * 1.2;

        float yawDiff = rotation.getYaw() - prev.getYaw();
        float pitchDiff = rotation.getPitch() - prev.getPitch();

        if (Math.abs(yawDiff) < gcd * 0.4 && Math.abs(pitchDiff) < gcd * 0.4) {
            return prev;
        }

        float fixedYaw = prev.getYaw() + (float) (Math.round(yawDiff / gcd) * gcd);
        float fixedPitch = prev.getPitch() + (float) (Math.round(pitchDiff / gcd) * gcd);

        return new Turns(fixedYaw, fixedPitch);
    }

    public void reset() {
        targetId = -1;
        ticksOnTarget = 0;
        prevTargetPos = null;
        smoothedVelocity = Vec3d.ZERO;
        prevMouseDeltaYaw = 0f;
        prevMouseDeltaPitch = 0f;
        offsetTick = 0;
    }

    @Override
    public Vec3d randomValue() {
        return new Vec3d(
                random.nextGaussian() * 0.01,
                random.nextGaussian() * 0.01,
                random.nextGaussian() * 0.01
        );
    }
}
