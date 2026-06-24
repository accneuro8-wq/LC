package fun.lumis.utils.features.aura.rotations.impl;

import fun.lumis.utils.features.aura.rotations.constructor.RotateConstructor;
import fun.lumis.utils.features.aura.warp.Turns;
import fun.lumis.utils.math.calc.Calculate;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.Random;

public class HAngle extends RotateConstructor {
    private final Random random = new Random();
    private float lastYaw, lastPitch;
    private long lastTargetId = -1;

    public HAngle() {
        super("HvH");
    }

    @Override
    public Turns limitAngleChange(Turns current, Turns target, Vec3d vec, Entity entity) {
        if (mc.player == null || entity == null || !entity.isAlive()) return current;

        if (entity.getId() != lastTargetId) {
            lastYaw = current.getYaw();
            lastPitch = current.getPitch();
            lastTargetId = entity.getId();
        }

        double dist = mc.player.distanceTo(entity);

        double predictFactor = 1.8 + (dist * 0.12) + random.nextDouble() * 0.4;
        double pX = entity.getX() + (entity.getX() - entity.prevX) * predictFactor;
        double pZ = entity.getZ() + (entity.getZ() - entity.prevZ) * predictFactor;

        double motionY = entity.getY() - entity.prevY;
        double aimY = entity.getY() + (entity.getEyeHeight(entity.getPose()) * (0.42 + random.nextDouble() * 0.38)) + (motionY * 0.5);

        Vec3d eyePos = mc.player.getEyePos();
        double dx = pX - eyePos.x;
        double dy = aimY - eyePos.y;
        double dz = pZ - eyePos.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        float idealYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float idealPitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));

        float yawDelta = MathHelper.wrapDegrees(idealYaw - lastYaw);
        float pitchDelta = idealPitch - lastPitch;

        float speed = 180.0f;

        float nextYaw = lastYaw + MathHelper.clamp(yawDelta, -speed, speed);
        float nextPitch = lastPitch + MathHelper.clamp(pitchDelta, -speed, speed);

        double sensitivity = mc.options.getMouseSensitivity().getValue();
        double f = sensitivity * 0.6 + 0.2;
        double gcd = f * f * f * 1.2;

        float deltaYaw = nextYaw - lastYaw;
        float deltaPitch = nextPitch - lastPitch;

        float finalYaw = lastYaw + (float) (Math.round(deltaYaw / gcd) * gcd);
        float finalPitch = lastPitch + (float) (Math.round(deltaPitch / gcd) * gcd);

        lastYaw = finalYaw;
        lastPitch = MathHelper.clamp(finalPitch, -90.0f, 90.0f);

        return new Turns(lastYaw, lastPitch);
    }

    public void reset() {
        lastTargetId = -1;
    }

    @Override
    public Vec3d randomValue() {
        return Vec3d.ZERO;
    }
}