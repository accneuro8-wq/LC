package fun.lumis.utils.features.aura.rotations.impl;

import fun.lumis.utils.features.aura.rotations.constructor.RotateConstructor;
import fun.lumis.utils.features.aura.warp.Turns;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * FunTime / Polar AC rotation profile.
 *
 * Polar flags: inconsistent GCD, instant snaps, constant (robotic) aim speed,
 * sensitivity mismatch and large overshoots. This profile therefore:
 *  - snaps GCD on BOTH the delta and the final absolute angle (double clamp);
 *  - uses a smooth ease-in-out speed curve (no constant velocity, no big jumps);
 *  - caps yaw/pitch speed conservatively and ramps it instead of teleporting;
 *  - adds only tiny humanized jitter (no aggressive overshoot Polar would catch);
 *  - keeps a soft, slowly drifting aim offset inside the hitbox.
 */
public class PolarAngle extends RotateConstructor {

    private final Random random = new Random();

    private float lastYaw, lastPitch;
    private float yawVel, pitchVel;
    private long targetId = -1;
    private int ticksOnTarget = 0;

    // soft hitbox offset
    private double offX, offY, offZ;
    private double targetOffX, targetOffY, targetOffZ;
    private long lastOffsetTime = 0;

    // light velocity prediction
    private Vec3d targetVel = Vec3d.ZERO;
    private Vec3d prevPos = null;

    public PolarAngle() {
        super("Polar");
    }

    @Override
    public Turns limitAngleChange(Turns current, Turns target, Vec3d vec, Entity entity) {
        if (mc.player == null || entity == null) return current;

        if (entity.getId() != targetId) {
            lastYaw = current.getYaw();
            lastPitch = current.getPitch();
            targetId = entity.getId();
            ticksOnTarget = 0;
            yawVel *= 0.25f;
            pitchVel *= 0.25f;
            prevPos = entity.getPos();
            targetVel = Vec3d.ZERO;
            resetOffset(entity);
        }
        ticksOnTarget++;

        // target velocity (smoothed, low weight -> Polar dislikes over-prediction)
        Vec3d pos = entity.getPos();
        if (prevPos != null) {
            targetVel = targetVel.multiply(0.8).add(pos.subtract(prevPos).multiply(0.2));
        }
        prevPos = pos;

        updateOffset(entity);

        double dist = mc.player.distanceTo(entity);
        double predFactor = Math.min(dist * 0.05, 0.30); // conservative predict
        Vec3d eye = mc.player.getEyePos();

        double px = entity.getX() + targetVel.x * predFactor + offX;
        double py = entity.getY() + targetVel.y * predFactor
                + entity.getEyeHeight(entity.getPose()) * 0.80 + offY;
        double pz = entity.getZ() + targetVel.z * predFactor + offZ;

        double dx = px - eye.x, dy = py - eye.y, dz = pz - eye.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);

        float idealYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float idealPitch = (float) -Math.toDegrees(Math.atan2(dy, hDist));

        float yawDelta = MathHelper.wrapDegrees(idealYaw - lastYaw);
        float pitchDelta = idealPitch - lastPitch;
        float totalDelta = (float) Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);

        // ---- smooth ease-in-out speed curve (no robotic constant speed) ----
        // base factor grows with distance to target angle, then eases out near 0
        float ease = (float) (1.0 - Math.exp(-totalDelta / 22.0)); // 0..1, smooth
        float accel = 0.30f + ease * 0.30f;     // 0.30 .. 0.60
        float maxSpd = 16f + ease * 16f;        // 16 .. 32 deg/tick cap

        // gentle ramp-up when a fresh target is acquired (no instant snap)
        if (ticksOnTarget < 6) {
            float u = ticksOnTarget / 6f;       // 0 -> 1
            accel *= 0.55f + u * 0.45f;
            maxSpd *= 0.60f + u * 0.40f;
        }
        // ease further when already on target (small angle -> slow, precise)
        if (totalDelta < 4f) {
            float f = totalDelta / 4f;
            accel *= 0.5f + f * 0.5f;
            maxSpd *= 0.5f + f * 0.5f;
        }
        if (dist < 2.0) { accel *= 0.9f; maxSpd *= 0.85f; }

        // light, bounded humanization (Polar dislikes big variance & overshoot)
        accel *= 0.95f + random.nextFloat() * 0.10f;
        float friction = 0.50f + random.nextFloat() * 0.06f;

        float targetYawVel = yawDelta * accel;
        float targetPitchVel = pitchDelta * accel;

        yawVel = yawVel * friction + targetYawVel * (1f - friction);
        pitchVel = pitchVel * friction + targetPitchVel * (1f - friction);

        yawVel = MathHelper.clamp(yawVel, -maxSpd, maxSpd);
        pitchVel = MathHelper.clamp(pitchVel, -maxSpd * 0.65f, maxSpd * 0.65f);

        // tiny micro jitter only
        float micro = totalDelta < 4f ? 0.05f : 0.025f;
        float microYaw = (float) (random.nextGaussian() * micro);
        float microPitch = (float) (random.nextGaussian() * micro * 0.5f);

        float nextYaw = lastYaw + yawVel + microYaw;
        float nextPitch = lastPitch + pitchVel + microPitch;

        // ---- strict GCD: snap delta AND absolute angle (Polar consistency) ----
        double sens = mc.options.getMouseSensitivity().getValue();
        double fac = sens * 0.6 + 0.2;
        double gcd = fac * fac * fac * 1.2;

        float yawStep = (float) (Math.round((nextYaw - lastYaw) / gcd) * gcd);
        float pitchStep = (float) (Math.round((nextPitch - lastPitch) / gcd) * gcd);

        lastYaw += yawStep;
        lastPitch += pitchStep;

        // re-quantize absolute angles to the same grid so residuals never drift
        lastYaw = (float) (Math.round(lastYaw / gcd) * gcd);
        lastPitch = (float) (Math.round(lastPitch / gcd) * gcd);
        lastPitch = MathHelper.clamp(lastPitch, -90f, 90f);

        return new Turns(lastYaw, lastPitch);
    }

    private void updateOffset(Entity e) {
        if (System.currentTimeMillis() - lastOffsetTime > 180 + random.nextInt(120)) {
            targetOffX = random.nextGaussian() * 0.10 * e.getWidth();
            targetOffY = random.nextGaussian() * 0.07 * e.getHeight();
            targetOffZ = random.nextGaussian() * 0.10 * e.getWidth();
            lastOffsetTime = System.currentTimeMillis();
        }
        offX += (targetOffX - offX) * 0.10;
        offY += (targetOffY - offY) * 0.10;
        offZ += (targetOffZ - offZ) * 0.10;
    }

    private void resetOffset(Entity e) {
        offX = targetOffX = random.nextGaussian() * 0.07 * e.getWidth();
        offY = targetOffY = random.nextGaussian() * 0.05 * e.getHeight();
        offZ = targetOffZ = random.nextGaussian() * 0.07 * e.getWidth();
        lastOffsetTime = System.currentTimeMillis();
    }

    public void reset() {
        targetId = -1;
        ticksOnTarget = 0;
        yawVel = pitchVel = 0;
        prevPos = null;
        targetVel = Vec3d.ZERO;
    }

    @Override
    public Vec3d randomValue() {
        return Vec3d.ZERO;
    }
}
