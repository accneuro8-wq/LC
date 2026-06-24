package fun.lumis.utils.features.aura.rotations.impl;

import fun.lumis.lumis;
import fun.lumis.features.impl.combat.Aura;
import fun.lumis.utils.features.aura.rotations.constructor.RotateConstructor;
import fun.lumis.utils.features.aura.striking.StrikeManager;
import fun.lumis.utils.features.aura.warp.Turns;
import fun.lumis.utils.math.calc.Calculate;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class LegitAngle extends RotateConstructor {
    private final Random random = new Random();
    private float lastYaw, lastPitch;
    private long lastTargetId = -1;
    private float circlePhase = 0.0f;
    private long lastTwitchTime = 0;
    private float focusStrength = 1.0f;

    public LegitAngle() {
        super("Legit");
    }

    @Override
    public Turns limitAngleChange(Turns currentAngle, Turns targetAngle, Vec3d vec3d, Entity entity) {
        if (mc.player == null || entity == null || !entity.isAlive()) return currentAngle;

        if (entity.getId() != lastTargetId) {
            lastYaw = currentAngle.getYaw();
            lastPitch = currentAngle.getPitch();
            lastTargetId = entity.getId();
            circlePhase = random.nextFloat() * 5.0f;
            focusStrength = 0.6f;
        }

        StrikeManager attackHandler = lumis.getInstance().getAttackPerpetrator().getAttackHandler();
        boolean isFighting = attackHandler.canAttack(Aura.getInstance().getConfig(), 0);

        circlePhase += isFighting ? 0.22f : 0.12f;
        focusStrength = MathHelper.lerp(0.1f, focusStrength, isFighting ? 1.2f : 0.8f);

        double horizSpeed = Math.hypot(entity.getX() - entity.prevX, entity.getZ() - entity.prevZ);
        double predictFactor = 1.1 + horizSpeed * 2.5;
        predictFactor = MathHelper.clamp(predictFactor, 1.0, 2.2);

        Vec3d predictedPos = entity.getPos().add(
                (entity.getX() - entity.prevX) * predictFactor,
                (entity.getY() - entity.prevY) * 0.5,
                (entity.getZ() - entity.prevZ) * predictFactor
        );

        Vec3d eye = mc.player.getEyePos();
        double targetHeight = entity.getEyeHeight(entity.getPose()) * 0.85;
        double dx = predictedPos.x - eye.x;
        double dy = (predictedPos.y + targetHeight) - eye.y;
        double dz = predictedPos.z - eye.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);

        float idealYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float idealPitch = (float) -Math.toDegrees(Math.atan2(dy, hDist));

        float yawDiff = MathHelper.wrapDegrees(idealYaw - lastYaw);
        float pitchDiff = idealPitch - lastPitch;
        float dist = (float) Math.hypot(yawDiff, pitchDiff);

        float speedMod = dist > 15.0f ? 1.8f : 0.85f;
        float baseSpeed = 15.0f + (dist * 0.45f * speedMod);
        float speed = MathHelper.clamp(baseSpeed, 8.0f, 120.0f) * focusStrength;

        float nextYaw = lastYaw + MathHelper.clamp(yawDiff, -speed, speed);
        float nextPitch = lastPitch + MathHelper.clamp(pitchDiff, -speed, speed);

        float swayMod = isFighting ? 0.4f : 1.0f;
        float circleYaw = (float) (Math.sin(circlePhase * 1.8) * 4.0 + Math.cos(circlePhase * 2.5) * 2.0);
        float circlePitch = (float) (Math.cos(circlePhase * 1.5) * 3.0 + Math.sin(circlePhase * 2.8) * 1.5);

        nextYaw += circleYaw * swayMod;
        nextPitch += circlePitch * swayMod;

        if (System.currentTimeMillis() - lastTwitchTime > 15 + random.nextInt(25)) {
            float twitchPower = isFighting ? 0.45f : 0.15f;
            nextYaw += (random.nextFloat() - 0.5f) * twitchPower;
            nextPitch += (random.nextFloat() - 0.5f) * twitchPower;
            lastTwitchTime = System.currentTimeMillis();
        }

        double gcd = Calculate.computeGcd();
        double gcdMod = 0.995 + random.nextDouble() * 0.01;

        float deltaYaw = nextYaw - lastYaw;
        float deltaPitch = nextPitch - lastPitch;

        lastYaw += (float) (deltaYaw - (deltaYaw % (gcd * gcdMod)));
        lastPitch += (float) (deltaPitch - (deltaPitch % (gcd * gcdMod)));
        lastPitch = MathHelper.clamp(lastPitch, -89.0f, 89.0f);

        return new Turns(lastYaw, lastPitch);
    }

    @Override public Vec3d randomValue() { return Vec3d.ZERO; }
    public void reset() { lastTargetId = -1; }
}