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
 * HVH — ротация для HvH (hack-vs-hack): быстрый, агрессивный трекинг ближайшей точки
 * хитбокса с гарантией пересечения луча.
 *
 * <p>Ядро: наведение в ближайшую к лучу взгляда точку предсказанного хитбокса (минимальный
 * угловой путь), резкий, но кадрово-ограниченный доворот, небольшой апериодичный микрошум
 * против «constant-aim» и обязательный slab-тест пересечения луча с коробкой на каждом тике.
 *
 * <p>В HvH противник такой же чит — приоритет отдан скорости захвата цели и стабильному
 * удержанию прицела внутри хитбокса, а не маскировке под человека.
 */
public class HvhRotation extends RotationsSystem implements QClient {

    private LivingEntity trackedTarget;

    private float currentYaw;
    private float currentPitch;

    private float lastSentYaw;
    private float lastSentPitch;

    private float noiseAngle;

    // Доля углового пути, проходимая за один тик (агрессивный, но не мгновенный захват).
    private static final float SNAP_FACTOR = 0.62F;
    // Человекоподобный потолок угловой скорости одного тика.
    private static final float MAX_STEP = 42.0F;
    // Базовая амплитуда углового микрошума (анти constant-aim).
    private static final float NOISE_AMPLITUDE = 0.9F;
    // Упреждение позиции цели на N тиков вперёд (анти-стрейф).
    private static final int PREDICT_TICKS = 2;
    // Внутренний отступ точки наведения от грани коробки (в блоках).
    private static final double INNER_MARGIN = 0.05;

    public void reset() {
        trackedTarget = null;
        noiseAngle = 0.0F;
        if (mc.player != null) {
            currentYaw = lastSentYaw = mc.player.getYaw();
            currentPitch = lastSentPitch = mc.player.getPitch();
        } else {
            currentYaw = currentPitch = 0.0F;
            lastSentYaw = lastSentPitch = 0.0F;
        }
    }

    /** Прицел продолжает вести цель после удара — без ухода pitch в небо. */
    public void onAttack() {
    }

    /** Шаг мыши (GCD) от текущей чувствительности. */
    private float calcGcd() {
        double s = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        return (float) (s * s * s * 1.2);
    }

    /** Линейная скорость цели за прошлый тик. */
    private Vec3d targetVelocity(LivingEntity target) {
        return new Vec3d(
                target.getX() - target.prevX,
                target.getY() - target.prevY,
                target.getZ() - target.prevZ
        );
    }

    /** Ближайшая к лучу взгляда (точке глаз) точка коробки с внутренним отступом. */
    private Vec3d closestPoint(Vec3d eye, Box box) {
        double mx = Math.min(INNER_MARGIN, (box.maxX - box.minX) * 0.5);
        double my = Math.min(INNER_MARGIN, (box.maxY - box.minY) * 0.5);
        double mz = Math.min(INNER_MARGIN, (box.maxZ - box.minZ) * 0.5);

        double cx = MathHelper.clamp(eye.x, box.minX + mx, box.maxX - mx);
        double cy = MathHelper.clamp(eye.y, box.minY + my, box.maxY - my);
        double cz = MathHelper.clamp(eye.z, box.minZ + mz, box.maxZ - mz);

        return new Vec3d(cx, cy, cz);
    }

    /** Угловой микрошум (yaw, pitch), масштабированный дистанцией. */
    private float[] generateNoise(float dist) {
        noiseAngle += 0.05F + (float) (Math.random() * 0.025F);

        float scale = MathHelper.clamp(dist / 4.0F, 0.2F, 1.0F);
        float amp = NOISE_AMPLITUDE * scale;

        float n1 = (float) Math.sin(noiseAngle * 0.91) * 0.4F;
        float n2 = (float) Math.cos(noiseAngle * 1.37 + 0.6) * 0.3F;

        float yawNoise = n1 * amp + ((float) Math.random() - 0.5F) * amp * 0.15F;
        float pitchNoise = n2 * amp * 0.5F + ((float) Math.random() - 0.5F) * amp * 0.1F;

        return new float[]{yawNoise, pitchNoise};
    }

    /** Вектор направления из углов (градусы). */
    private static Vec3d dirFromAngles(float yaw, float pitch) {
        float f = pitch * 0.017453292F;
        float g = -yaw * 0.017453292F;
        float ch = MathHelper.cos(g);
        float sh = MathHelper.sin(g);
        float cp = MathHelper.cos(f);
        float sp = MathHelper.sin(f);
        return new Vec3d(sh * cp, -sp, ch * cp);
    }

    /** Slab-тест: пересекает ли луч из {@code eye} под углами (yaw,pitch) коробку {@code box}. */
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
            currentYaw = lastSentYaw = mc.player.getYaw();
            currentPitch = lastSentPitch = mc.player.getPitch();
            noiseAngle = (float) (Math.random() * Math.PI * 2);
        }

        Vec3d eye = mc.player.getEyePos();

        // --- Предсказанная коробка цели (упреждение по скорости — анти-стрейф) ---
        Box baseBox = target.getBoundingBox();
        Vec3d vel = targetVelocity(target);
        Vec3d predictedCenter = getPredictedPoint(target, baseBox.getCenter());
        Box box = baseBox.offset(predictedCenter.subtract(baseBox.getCenter()))
                .offset(vel.multiply(PREDICT_TICKS));

        float distance = (float) eye.distanceTo(box.getCenter());

        // --- Точка наведения: ближайшая к лучу взгляда точка хитбокса (минимальный путь) ---
        Vec3d aimPoint = closestPoint(eye, box);
        Vec3d dir = aimPoint.subtract(eye);
        float wantYaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0);
        float wantPitch = (float) -Math.toDegrees(Math.atan2(dir.y, dir.horizontalLength()));

        // --- Резкий, но кадрово-ограниченный доворот ---
        float diffYaw = MathHelper.wrapDegrees(wantYaw - currentYaw);
        float diffPitch = wantPitch - currentPitch;

        float moveYaw = MathHelper.clamp(diffYaw * SNAP_FACTOR, -MAX_STEP, MAX_STEP);
        float movePitch = MathHelper.clamp(diffPitch * SNAP_FACTOR, -MAX_STEP, MAX_STEP);

        currentYaw += moveYaw;
        currentPitch = MathHelper.clamp(currentPitch + movePitch, -89.0F, 89.0F);

        // --- Микрошум, ограниченный долей УГЛОВОГО размера цели ---
        float[] noise = generateNoise(distance);
        float halfW = (float) Math.max(0.3, (box.maxX - box.minX) * 0.5);
        float halfH = (float) Math.max(0.4, (box.maxY - box.minY) * 0.5);
        float dd = Math.max(0.8F, distance);
        float angHalfYaw = (float) Math.toDegrees(Math.atan2(halfW, dd));
        float angHalfPitch = (float) Math.toDegrees(Math.atan2(halfH, dd));

        float devYaw = MathHelper.clamp(noise[0], -angHalfYaw * 0.3F, angHalfYaw * 0.3F);
        float devPitch = MathHelper.clamp(noise[1], -angHalfPitch * 0.3F, angHalfPitch * 0.3F);

        float outY = currentYaw + devYaw;
        float outP = MathHelper.clamp(currentPitch + devPitch, -89.0F, 89.0F);

        // --- Гарант попадания: луч обязан пересекать хитбокс ---
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

        // --- GCD-квантование (дельта углов кратна шагу мыши) ---
        outY -= (outY - lastSentYaw) % gcd;
        outP -= (outP - lastSentPitch) % gcd;

        lastSentYaw = outY;
        lastSentPitch = outP;

        RotationStorage.update(new Rotation(outY, outP), 360.0F, 45.0F, 45.0F, 45.0F, 0, 1, Aura.clientLook.isState());
    }
}
