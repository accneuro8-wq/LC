package fun.lumis.utils.features.aura.rotations.impl;

import fun.lumis.utils.features.aura.rotations.constructor.RotateConstructor;
import fun.lumis.utils.features.aura.utils.MathAngle;
import fun.lumis.utils.features.aura.warp.Turns;
import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class MatrixAngle extends RotateConstructor {
    private static final Random RANDOM = new Random();
    private float lastYaw, lastPitch;
    private boolean initialized = false;
    private float velocityYaw = 0;
    private float velocityPitch = 0;
    private long lastTargetId = -1;

    public MatrixAngle() {
        super("Matrix");
    }

    @Override
    public Turns limitAngleChange(Turns currentAngle, Turns targetAngle, Vec3d vec3d, Entity entity) {
        if (!initialized) {
            lastYaw = currentAngle.getYaw();
            lastPitch = currentAngle.getPitch();
            initialized = true;
        }

        if (entity == null || !entity.isAlive()) {
            reset();
            return currentAngle;
        }

        if (entity.getId() != lastTargetId) {
            lastTargetId = entity.getId();
            velocityYaw = 0;
            velocityPitch = 0;
        }

        double predictFactor = 1.2 + RANDOM.nextDouble() * 0.3;
        Vec3d predictedPos = entity.getPos().add(
                (entity.getX() - entity.prevX) * predictFactor,
                (entity.getY() - entity.prevY) * predictFactor,
                (entity.getZ() - entity.prevZ) * predictFactor
        );

        Vec3d eyePos = mc.player.getEyePos();
        double centerHeight = entity.getHeight() * 0.65;
        double dx = predictedPos.x - eyePos.x;
        double dy = (predictedPos.y + centerHeight) - eyePos.y;
        double dz = predictedPos.z - eyePos.z;
        double dstXZ = MathHelper.sqrt((float) (dx * dx + dz * dz));

        float idealYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float idealPitch = (float) -Math.toDegrees(Math.atan2(dy, dstXZ));

        float yawDiff = MathHelper.wrapDegrees(idealYaw - lastYaw);
        float pitchDiff = idealPitch - lastPitch;

        float friction = 0.45F + (RANDOM.nextFloat() * 0.1F);
        float accel = Math.abs(yawDiff) + Math.abs(pitchDiff) > 10 ? 0.35F : 0.25F;

        velocityYaw = (velocityYaw * friction) + (yawDiff * accel);
        velocityPitch = (velocityPitch * friction) + (pitchDiff * accel);

        float nextYaw = lastYaw + MathHelper.clamp(velocityYaw, -60.0F, 60.0F);
        float nextPitch = lastPitch + MathHelper.clamp(velocityPitch, -40.0F, 40.0F);

        float swayTime = System.currentTimeMillis() / 350.0F;
        float sway = (float) Math.sin(swayTime) * 0.07F;
        nextYaw += sway + (RANDOM.nextFloat() - 0.5F) * 0.04F;
        nextPitch += sway + (RANDOM.nextFloat() - 0.5F) * 0.04F;

        float sensitivity = (float) (mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2);
        float multiplier = sensitivity * sensitivity * sensitivity * 1.2F;

        float finalYawDiff = nextYaw - lastYaw;
        float finalPitchDiff = nextPitch - lastPitch;

        float gcdYaw = Math.round(finalYawDiff / multiplier) * multiplier;
        float gcdPitch = Math.round(finalPitchDiff / multiplier) * multiplier;

        lastYaw += gcdYaw;
        lastPitch = MathHelper.clamp(lastPitch + gcdPitch, -90.0F, 90.0F);

        return new Turns(lastYaw, lastPitch);
    }

    public void reset() {
        this.initialized = false;
        this.velocityYaw = 0;
        this.velocityPitch = 0;
        this.lastTargetId = -1;
    }

    @Override
    public Vec3d randomValue() {
        return new Vec3d(0.002, 0.002, 0.002);
    }
}