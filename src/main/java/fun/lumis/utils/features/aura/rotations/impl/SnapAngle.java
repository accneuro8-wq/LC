package fun.lumis.utils.features.aura.rotations.impl;

import fun.lumis.utils.features.aura.rotations.constructor.RotateConstructor;
import fun.lumis.utils.features.aura.warp.Turns;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class SnapAngle extends RotateConstructor {
    private final Random rng = new Random();
    private float lYaw, lPitch, tension = 1f;
    private long tId = -1;
    private boolean snapped;
    private int settle;

    private Vec3d prevPos, sVel = Vec3d.ZERO;
    private double offX, offY, offZ, tOffX, tOffY, tOffZ;
    private long offTime;
    private float tremY, tremP, tremTY, tremTP, gripY, gripP, gripTY, gripTP;
    private int tremTick, gripTick;

    // 🔥 НОВЫЕ ПАРАМЕТРЫ ДЛЯ PVP
    private float snapSpeed = 1.0f;      // Скорость снэпа
    private float accuracy = 0.98f;      // Точность попадания
    private boolean aggressive = true;   // Агрессивный режим

    public SnapAngle() { super("Snap"); }

    @Override
    public Turns limitAngleChange(Turns cur, Turns tgt, Vec3d vec, Entity e) {
        if (mc.player == null || e == null || !e.isAlive()) { reset(); return cur; }

        if (e.getId() != tId) {
            lYaw = mc.player.getYaw();
            lPitch = mc.player.getPitch();
            tId = e.getId();
            snapped = false;
            settle = 0;
            prevPos = e.getPos();
            sVel = Vec3d.ZERO;
            resetOff(e);
            tension = 1.5f + rng.nextFloat() * 0.5f; // 🔥 УСИЛЕНО
        }

        updateHand();
        Vec3d pos = e.getPos();
        if (prevPos != null) sVel = sVel.multiply(0.7).add(pos.subtract(prevPos).multiply(0.3));
        prevPos = pos;
        updateOff(e);

        boolean falling = !mc.player.isOnGround() && mc.player.fallDistance > 0;
        boolean ready = mc.player.getAttackCooldownProgress(0.5f) >= 0.85f; // 🔥 БЫСТРЕЕ
        float rY, rP;

        if (falling && ready && !snapped) {
            // ====== АГРЕССИВНЫЙ СНЭП (360°) ======
            float[] a = aim(e, true);
            float dy = MathHelper.wrapDegrees(a[0] - lYaw);
            float dp = a[1] - lPitch;

            // 🔥 ОБРАБОТКА 360° ПОВОРОТОВ
            if (Math.abs(dy) > 180) {
                dy = dy > 0 ? dy - 360 : dy + 360;
            }

            float tot = (float) Math.sqrt(dy * dy + dp * dp);

            // 🔥 МАКСИМАЛЬНАЯ СКОРОСТЬ СНЭПА
            float comp = (tot > 120 ? 0.95f : tot > 60 ? 0.92f : tot > 25 ? 0.96f : 0.98f)
                    + rng.nextFloat() * 0.02f;

            rY = lYaw + dy * comp * snapSpeed;
            rP = MathHelper.clamp(lPitch + dp * comp * snapSpeed, -89.5f, 89.5f);
            snapped = true;
            settle = 0;
            tension = 1.8f + rng.nextFloat() * 0.2f; // 🔥 МАКСИМУМ

        } else if (snapped && settle < 1) { // 🔥 БЫСТРЕЕ ДОВОДКА
            settle++;
            float[] a = aim(e, false);
            float sp = 0.65f + rng.nextFloat() * 0.15f; // 🔥 БЫСТРЕЕ
            float[] t = tremor();

            float dy = MathHelper.wrapDegrees(a[0] - lYaw);
            if (Math.abs(dy) > 180) {
                dy = dy > 0 ? dy - 360 : dy + 360;
            }

            rY = lYaw + dy * sp + t[0] * 0.3f;
            rP = MathHelper.clamp(lPitch + (a[1] - lPitch) * sp + t[1] * 0.3f, -89.5f, 89.5f);

        } else {
            // ====== ОТСЛЕЖИВАНИЕ (360° РЕЖИМ) ======
            snapped = false;
            settle = 0;
            tension = Math.max(tension * 0.95f, 1.2f); // 🔥 ВЫШЕ МИНИМУМ
            float[] t = tremor();

            float[] a = aim(e, false);
            float dy = MathHelper.wrapDegrees(a[0] - mc.player.getYaw());
            if (Math.abs(dy) > 180) {
                dy = dy > 0 ? dy - 360 : dy + 360;
            }

            rY = mc.player.getYaw() + dy * 0.15f + t[0] * 0.5f;
            rP = MathHelper.clamp(mc.player.getPitch() + (a[1] - mc.player.getPitch()) * 0.15f + t[1] * 0.5f, -89.5f, 89.5f);
        }

        // ====== GCD (ОПТИМИЗИРОВАН) ======
        double s = mc.options.getMouseSensitivity().getValue();
        double f = s * 0.6 + 0.2;
        double gcd = f * f * f * 1.2;

        float dy = rY - lYaw;
        float dp = rP - lPitch;

        // 🔥 ОБРАБОТКА 360° ДЛЯ GCD
        if (Math.abs(dy) > 180) {
            dy = dy > 0 ? dy - 360 : dy + 360;
        }

        if (Math.abs(dy) < gcd * 0.25 && Math.abs(dp) < gcd * 0.25) {
            return new Turns(lYaw, lPitch);
        }

        lYaw += (float) (Math.round(dy / gcd) * gcd);
        lPitch = MathHelper.clamp(lPitch + (float) (Math.round(dp / gcd) * gcd), -89.5f, 89.5f);

        return new Turns(lYaw, lPitch);
    }

    private float[] aim(Entity e, boolean randH) {
        float d = mc.player.distanceTo(e);
        double pf = MathHelper.clamp(0.95 + d * 0.15, 0.95, 2.5); // 🔥 ЛУЧШЕ ПРЕДИКТ
        Vec3d eye = mc.player.getEyePos();

        // 🔥 ОПТИМАЛЬНАЯ ВЫСОТА ДЛЯ PVP
        float hp = randH ? 0.5f + rng.nextFloat() * 0.35f : 0.65f + rng.nextFloat() * 0.15f;

        double ax = e.getX() + sVel.x * pf + offX;
        double ay = e.getY() + e.getHeight() * hp + sVel.y * pf * 0.5 + offY;
        double az = e.getZ() + sVel.z * pf + offZ;

        double dx = ax - eye.x;
        double dya = ay - eye.y;
        double dz = az - eye.z;
        double hz = Math.sqrt(dx * dx + dz * dz);

        return new float[]{
                (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f,
                (float) -Math.toDegrees(Math.atan2(dya, hz))
        };
    }

    private void updateHand() {
        if (++tremTick >= 1 + rng.nextInt(2)) { // 🔥 ЧАЩЕ ОБНОВЛЯЕТСЯ
            tremTY = gn(0.2f);
            tremTP = gn(0.12f);
            tremTick = 0;
        }
        float tl = 0.35f + rng.nextFloat() * 0.12f;
        tremY += (tremTY - tremY) * tl;
        tremP += (tremTP - tremP) * tl;

        if (++gripTick >= 6 + rng.nextInt(10)) { // 🔥 БЫСТРЕЕ
            gripTY = gn(0.4f);
            gripTP = gn(0.2f);
            gripTick = 0;
        }
        float gl = 0.1f + rng.nextFloat() * 0.05f;
        gripY += (gripTY - gripY) * gl;
        gripP += (gripTP - gripP) * gl;
    }

    private float[] tremor() {
        return new float[]{
                (tremY + gripY) * tension * 0.8f,
                (tremP + gripP) * tension * 0.8f
        };
    }

    private float gn(float s) {
        return (float) (rng.nextGaussian() * s * tension * 0.9f);
    }

    private void updateOff(Entity e) {
        if (System.currentTimeMillis() - offTime > 60 + rng.nextInt(40)) { // 🔥 БЫСТРЕЕ
            float s = MathHelper.clamp(1f / (mc.player.distanceTo(e) * 0.35f + 0.4f), 0.3f, 1.2f);
            tOffX = rng.nextGaussian() * 0.1 * e.getWidth() * s;
            tOffY = rng.nextGaussian() * 0.06 * e.getHeight() * s;
            tOffZ = rng.nextGaussian() * 0.1 * e.getWidth() * s;
            offTime = System.currentTimeMillis();
        }
        float l = 0.12f + rng.nextFloat() * 0.04f;
        offX += (tOffX - offX) * l;
        offY += (tOffY - offY) * l;
        offZ += (tOffZ - offZ) * l;
    }

    private void resetOff(Entity e) {
        float s = MathHelper.clamp(1f / (mc.player.distanceTo(e) * 0.35f + 0.4f), 0.3f, 1.2f);
        offX = tOffX = rng.nextGaussian() * 0.07 * e.getWidth() * s;
        offY = tOffY = rng.nextGaussian() * 0.05 * e.getHeight() * s;
        offZ = tOffZ = rng.nextGaussian() * 0.07 * e.getWidth() * s;
        offTime = System.currentTimeMillis();
    }

    public void reset() {
        tId = -1;
        snapped = false;
        settle = 0;
        tension = 1f;
        tremY = tremP = gripY = gripP = 0;
        prevPos = null;
        sVel = Vec3d.ZERO;
    }

    @Override
    public Vec3d randomValue() {
        return new Vec3d(
                rng.nextGaussian() * 0.004,
                rng.nextGaussian() * 0.004,
                rng.nextGaussian() * 0.004
        );
    }
}
