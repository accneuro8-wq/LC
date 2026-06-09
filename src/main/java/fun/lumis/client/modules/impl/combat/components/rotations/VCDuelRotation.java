package fun.lumis.client.modules.impl.combat.components.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import fun.lumis.api.QClient;
import fun.lumis.api.storages.implement.RotationStorage;
import fun.lumis.api.utils.rotate.Rotation;
import fun.lumis.client.modules.impl.combat.Aura;
import fun.lumis.client.modules.impl.combat.components.RotationsSystem;

/**
 * VCDuelRotation — ротация для VC Duel (VimeWorld Crystal Duel / PvP).
 * Полный обход античита с агрессивным трекингом, мгновенным снапом на цель
 * и минимальными проверками. Оптимизирована для кристалл-PvP на VimeWorld.
 */
public class VCDuelRotation extends RotationsSystem implements QClient {

    private LivingEntity trackedTarget;
    private float lastSentYaw;
    private float lastSentPitch;
    private float noiseAngle;

    private static final float SNAP_FACTOR = 0.85F;
    private static final float MAX_STEP = 55.0F;
    private static final float NOISE_AMPLITUDE = 0.4F;
    private static final int PREDICT_TICKS = 1;
    private static final double INNER_MARGIN = 0.03;

    public void reset() {
        trackedTarget = null;
        noiseAngle = 0.0F;
        if (mc.player != null) {
            lastSentYaw = mc.player.getYaw();
            lastSentPitch = mc.player.getPitch();
        } else {
            lastSentYaw = lastSentPitch = 0.0F;
        }
    }

    public void onAttack() {
    }

    private float calcGcd() {
        double s = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        return (float) (s * s * s * 1.2);
    }

    private Vec3d targetVelocity(LivingEntity target) {
        return new Vec3d(
                target.getX() - target.prevX,
                target.getY() - target.prevY,
                target.getZ() - target.prevZ
        );
    }

    private Vec3d closestPoint(Vec3d eye, Box box) {
        double mx = Math.min(INNER_MARGIN, (box.maxX - box.minX) * 0.5);
        double my = Math.min(INNER_MARGIN, (box.maxY - box.minY) * 0.5);
        double mz = Math.min(INNER_MARGIN, (box.maxZ - box.minZ) * 0.5);

        double cx = MathHelper.clamp(eye.x, box.minX + mx, box.maxX - mx);
        double cy = MathHelper.clamp(eye.y, box.minY + my, box.maxY - my);
        double cz = MathHelper.clamp(eye.z, box.minZ + mz, box.maxZ - mz);

        return new Vec3d(cx, cy, cz);
    }

    private float[] generateNoise(float dist) {
        noiseAngle += 0.03F + (float) (Math.random() * 0.015F);
        float scale = MathHelper.clamp(dist / 4.0F, 0.15F, 1.0F);
        float amp = NOISE_AMPLITUDE * scale;

        float n1 = (float) Math.sin(noiseAngle * 0.91) * 0.5F;
        float n2 = (float) Math.cos(noiseAngle * 1.33 + 0.6) * 0.35F;

        float yawNoise = n1 * amp + ((float) Math.random() - 0.5F) * amp * 0.1F;
        float pitchNoise = n2 * amp * 0.4F + ((float) Math.random() - 0.5F) * amp * 0.08F;

        return new float[]{yawNoise, pitchNoise};
    }

    private static Vec3d dirFromAngles(float yaw, float pitch) {
        float f = pitch * 0.017453292F;
        float g = -yaw * 0.017453292F;
        float ch = MathHelper.cos(g);
        float sh = MathHelper.sin(g);
        float cp = MathHelper.cos(f);
        float sp = MathHelper.sin(f);
        return new Vec3d(sh * cp, -sp, ch * cp);
    }

    private static boolean rayHitsBox(Vec3d eye, float yaw, float pitch, Box box) {
        Vec3d dir = dirFromAngles(yaw, pitch);

        double tmin = 0.0;
        double tmax = Double.MAX_VALUE;
        double[] o = {eye.x, eye.y, eye.z};
        double[] d = {dir.x, dir.y, dir.z};
        double[] mn = {box.minX, box.minY, box.minZ};
        double[] mx = {box.maxX, box.maxY, box.maxZ};

        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1.0E-8) {
                if (o[i] < mn[i] || o[i] > mx[i]) return false;
            } else {
                double inv = 1.0 / d[i];
                double t1 = (mn[i] - o[i]) * inv;
                double t2 = (mx[i] - o[i]) * inv;
                if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
                if (t1 > tmin) tmin = t1;
                if (t2 < tmax) tmax = t2;
                if (tmin > tmax) return false;
            }
        }
        return true;
    }

    @Override
    public void updateRotations(LivingEntity target) {
        if (mc.player == null || target == null) return;

        float gcd = calcGcd();

        if (trackedTarget != target) {
            trackedTarget = target;
            lastSentYaw = mc.player.getYaw();
            lastSentPitch = mc.player.getPitch();
            noiseAngle = (float) (Math.random() * Math.PI * 2);
        }

        Vec3d eye = mc.player.getEyePos();

        Box baseBox = target.getBoundingBox();
        Vec3d vel = targetVelocity(target);
        Vec3d predictedCenter = getPredictedPoint(target, baseBox.getCenter());
        Box box = baseBox.offset(predictedCenter.subtract(baseBox.getCenter()))
                .offset(vel.multiply(PREDICT_TICKS));

        float distance = (float) eye.distanceTo(box.getCenter());

        Vec3d aimPoint = closestPoint(eye, box);
        Vec3d dir = aimPoint.subtract(eye);
        float wantYaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0);
        float wantPitch = (float) -Math.toDegrees(Math.atan2(dir.y, dir.horizontalLength()));

        float diffYaw = MathHelper.wrapDegrees(wantYaw - lastSentYaw);
        float diffPitch = wantPitch - lastSentPitch;

        float moveYaw = MathHelper.clamp(diffYaw * SNAP_FACTOR, -MAX_STEP, MAX_STEP);
        float movePitch = MathHelper.clamp(diffPitch * SNAP_FACTOR, -MAX_STEP, MAX_STEP);

        float currentYaw = lastSentYaw + moveYaw;
        float currentPitch = MathHelper.clamp(lastSentPitch + movePitch, -89.0F, 89.0F);

        float[] noise = generateNoise(distance);
        float halfW = (float) Math.max(0.3, (box.maxX - box.minX) * 0.5);
        float halfH = (float) Math.max(0.4, (box.maxY - box.minY) * 0.5);
        float dd = Math.max(0.8F, distance);
        float angHalfYaw = (float) Math.toDegrees(Math.atan2(halfW, dd));
        float angHalfPitch = (float) Math.toDegrees(Math.atan2(halfH, dd));

        float devYaw = MathHelper.clamp(noise[0], -angHalfYaw * 0.25F, angHalfYaw * 0.25F);
        float devPitch = MathHelper.clamp(noise[1], -angHalfPitch * 0.25F, angHalfPitch * 0.25F);

        float outY = currentYaw + devYaw;
        float outP = MathHelper.clamp(currentPitch + devPitch, -89.0F, 89.0F);

        if (!rayHitsBox(eye, outY, outP, box)) {
            float blend = 0.5F;
            for (int i = 0; i < 6; i++) {
                float tryY = outY + MathHelper.wrapDegrees(currentYaw - outY) * blend;
                float tryP = MathHelper.clamp(outP + (currentPitch - outP) * blend, -89.0F, 89.0F);
                if (rayHitsBox(eye, tryY, tryP, box)) {
                    outY = tryY;
                    outP = tryP;
                    break;
                }
                blend = Math.min(1.0F, blend + 0.18F);
                if (i == 5) {
                    outY = currentYaw;
                    outP = MathHelper.clamp(currentPitch, -89.0F, 89.0F);
                }
            }
        }

        outY -= (outY - lastSentYaw) % gcd;
        outP -= (outP - lastSentPitch) % gcd;

        lastSentYaw = outY;
        lastSentPitch = outP;

        RotationStorage.update(new Rotation(outY, outP), 360.0F, 45.0F, 45.0F, 45.0F, 0, 1, Aura.clientLook.isState());
    }
}
