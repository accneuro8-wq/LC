package fun.lumis.utils.features.aura.rotations.impl;

import fun.lumis.utils.features.aura.rotations.constructor.RotateConstructor;
import fun.lumis.utils.features.aura.warp.Turns;
import fun.lumis.utils.math.calc.Calculate;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Sloth (full) — ротация полностью под античит Sloth 1.21.
 *
 * Sloth палит: рассинхрон GCD/сенсы, постоянную (роботизированную) скорость аима,
 * мгновенные снапы, превышение лимита дельты за тик, "слишком идеальный" трекинг
 * и aim-снапы вне естественного ускорения. Поэтому профиль:
 *  - жёсткое двойное GCD-выравнивание (дельта + абсолютный угол на одну сетку);
 *  - строгий лимит дельты за тик (Sloth режет резкие скачки) + ease-in-out;
 *  - реакционная задержка и плавный ramp-up при захвате/смене цели (без снапов);
 *  - random-walk человеческий микро-sway (без периодики) + редкий overshoot с докрутом;
 *  - умеренное упреждение в хитбокс-конус и мягкий дрейф точки прицеливания.
 */
public class SlothFullAngle extends RotateConstructor {
    private final Random random = new Random();

    private float lastYaw, lastPitch;
    private float yawVel, pitchVel;
    private long lastTargetId = -1;
    private int ticksOnTarget = 0;

    private float walkYaw = 0f, walkPitch = 0f;
    private float sessionSmooth = 0.5f;
    private int reactionDelay = 0;
    private float overshootYaw = 0f, overshootPitch = 0f;
    private long lastOvershoot = 0;
    private long lastTwitch = 0;

    private double offX, offY, offZ;
    private double tOffX, tOffY, tOffZ;
    private long lastOffsetTime = 0;

    public SlothFullAngle() {
        super("Sloth");
    }

    @Override
    public Turns limitAngleChange(Turns current, Turns target, Vec3d vec, Entity entity) {
        if (mc.player == null || entity == null || !entity.isAlive()) return current;

        if (entity.getId() != lastTargetId) {
            lastYaw = current.getYaw();
            lastPitch = current.getPitch();
            lastTargetId = entity.getId();
            ticksOnTarget = 0;
            yawVel *= 0.18f;
            pitchVel *= 0.18f;
            reactionDelay = 1 + random.nextInt(3);
            sessionSmooth = 0.46f + random.nextFloat() * 0.14f;
            walkYaw = walkPitch = 0f;
            overshootYaw = overshootPitch = 0f;
            newOffset(entity);
        }
        ticksOnTarget++;
        if (reactionDelay > 0) { reactionDelay--; return new Turns(lastYaw, lastPitch); }

        double dxV = entity.getX() - entity.prevX;
        double dyV = entity.getY() - entity.prevY;
        double dzV = entity.getZ() - entity.prevZ;

        double distance = mc.player.distanceTo(entity);
        double horizSpeed = Math.hypot(dxV, dzV);

        // умеренное упреждение — в пределах хитбокс-конуса
        double predict = MathHelper.clamp(1.0 + horizSpeed * (0.7 + distance * 0.06), 1.0, 1.7);

        updateOffset(entity);

        Vec3d eye = mc.player.getEyePos();
        double aimHeight = entity.getEyeHeight(entity.getPose()) * (0.84 + random.nextDouble() * 0.08);

        double px = entity.getX() + dxV * predict + offX;
        double py = entity.getY() + aimHeight + dyV * 0.4 + offY;
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

        // ease-in-out
        float ease = (float) (1.0 - Math.exp(-angleDist / 24.0));
        float accel = 0.26f + ease * 0.28f;        // 0.26 .. 0.54
        float maxSpeed = 18f + ease * 24f;         // 18 .. 42 deg/tick (Sloth-friendly cap)

        if (ticksOnTarget < 6) {
            float u = ticksOnTarget / 6f;
            accel *= 0.55f + u * 0.45f;
            maxSpeed *= 0.6f + u * 0.4f;
        }
        if (angleDist < 4f) {
            float fction = angleDist / 4f;
            accel *= 0.5f + fction * 0.5f;
            maxSpeed *= 0.55f + fction * 0.45f;
        }
        if (distance < 3.0) { accel *= 0.9f; maxSpeed *= 0.85f; }

        accel *= 0.93f + random.nextFloat() * 0.12f;
        float friction = sessionSmooth + random.nextFloat() * 0.05f;

        yawVel = yawVel * friction + (yawDiff * accel) * (1f - friction);
        pitchVel = pitchVel * friction + (pitchDiff * accel) * (1f - friction);

        yawVel = MathHelper.clamp(yawVel, -maxSpeed, maxSpeed);
        pitchVel = MathHelper.clamp(pitchVel, -maxSpeed * 0.68f, maxSpeed * 0.68f);

        float nextYaw = lastYaw + yawVel;
        float nextPitch = lastPitch + pitchVel;

        // random-walk микро-sway (без периодики)
        float walkAmp = distance < 3.0 ? 0.16f : 0.26f;
        walkYaw = walkYaw * 0.87f + (float) random.nextGaussian() * walkAmp;
        walkPitch = walkPitch * 0.87f + (float) random.nextGaussian() * walkAmp * 0.7f;
        walkYaw = MathHelper.clamp(walkYaw, -1.2f, 1.2f);
        walkPitch = MathHelper.clamp(walkPitch, -0.9f, 0.9f);
        nextYaw += walkYaw;
        nextPitch += walkPitch;

        // редкий микро-твич
        if (System.currentTimeMillis() - lastTwitch > 80 + random.nextInt(90)) {
            nextYaw += (random.nextFloat() - 0.5f) * 0.14f;
            nextPitch += (random.nextFloat() - 0.5f) * 0.09f;
            lastTwitch = System.currentTimeMillis();
        }

        // редкий естественный overshoot + докрут
        if (angleDist > 12f && System.currentTimeMillis() - lastOvershoot > 700 + random.nextInt(900)) {
            overshootYaw = yawDiff * (0.08f + random.nextFloat() * 0.08f);
            overshootPitch = pitchDiff * (0.06f + random.nextFloat() * 0.06f);
            lastOvershoot = System.currentTimeMillis();
        }
        nextYaw += overshootYaw;
        nextPitch += overshootPitch;
        overshootYaw *= 0.55f;
        overshootPitch *= 0.55f;

        // жёсткий лимит дельты за тик (Sloth режет резкие скачки)
        float maxDelta = maxSpeed + 2f;
        nextYaw = lastYaw + MathHelper.clamp(MathHelper.wrapDegrees(nextYaw - lastYaw), -maxDelta, maxDelta);
        nextPitch = lastPitch + MathHelper.clamp(nextPitch - lastPitch, -maxDelta * 0.68f, maxDelta * 0.68f);

        // двойное GCD-выравнивание (дельта + абсолютный угол)
        double gcd = Calculate.computeGcd();
        if (gcd > 0.0) {
            float dYaw = (float) (Math.round((nextYaw - lastYaw) / gcd) * gcd);
            float dPitch = (float) (Math.round((nextPitch - lastPitch) / gcd) * gcd);
            nextYaw = lastYaw + dYaw;
            nextPitch = lastPitch + dPitch;
            nextYaw = (float) (Math.round(nextYaw / gcd) * gcd);
            nextPitch = (float) (Math.round(nextPitch / gcd) * gcd);
        }

        lastYaw = nextYaw;
        lastPitch = MathHelper.clamp(nextPitch, -89f, 89f);

        return new Turns(lastYaw, lastPitch);
    }

    private void updateOffset(Entity e) {
        if (System.currentTimeMillis() - lastOffsetTime > 170 + random.nextInt(130)) {
            tOffX = random.nextGaussian() * 0.07 * e.getWidth();
            tOffY = random.nextGaussian() * 0.05 * e.getHeight();
            tOffZ = random.nextGaussian() * 0.07 * e.getWidth();
            lastOffsetTime = System.currentTimeMillis();
        }
        offX += (tOffX - offX) * 0.11;
        offY += (tOffY - offY) * 0.11;
        offZ += (tOffZ - offZ) * 0.11;
    }

    private void newOffset(Entity e) {
        offX = tOffX = random.nextGaussian() * 0.05 * e.getWidth();
        offY = tOffY = random.nextGaussian() * 0.04 * e.getHeight();
        offZ = tOffZ = random.nextGaussian() * 0.05 * e.getWidth();
        lastOffsetTime = System.currentTimeMillis();
    }

    @Override
    public Vec3d randomValue() {
        return Vec3d.ZERO;
    }

    public void reset() {
        lastYaw = lastPitch = 0;
        yawVel = pitchVel = 0;
        lastTargetId = -1;
        ticksOnTarget = 0;
        walkYaw = walkPitch = 0f;
        overshootYaw = overshootPitch = 0f;
        reactionDelay = 0;
        lastTwitch = 0;
    }
}
