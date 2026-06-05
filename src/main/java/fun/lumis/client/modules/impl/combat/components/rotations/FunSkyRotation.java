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
 * FunSky — ротация, заточенная под ротационные проверки Grim AC.
 *
 * <p>Ядро: плавный человекоподобный доворот с ускорением/торможением (без постоянной угловой
 * скорости), наведение в дрейфующую точку внутри хитбокса и обязательная верификация
 * пересечения луча с предсказанной коробкой на каждом тике.
 *
 * <p>Слой под конкретные эвристики Grim:
 * <ul>
 *   <li><b>GCD (AimModulo / sensitivity)</b> — Grim восстанавливает шаг мыши по серии дельт:
 *       итоговая дельта углов обязана быть целым кратным GCD. Квантование идёт с переносом
 *       остатка (carry), чтобы не было ни систематического сноса от усечения, ни суб-GCD
 *       «заморозки с прыжком»;</li>
 *   <li><b>AimDuplicateLook</b> — Grim флагает повтор одних и тех же углов подряд. Микрошум
 *       гарантирует, что соседние пакеты не совпадают бит-в-бит, пока цель «на прицеле»;</li>
 *   <li><b>AimInvalidPitch</b> — pitch жёстко зажат в [-89, 89];</li>
 *   <li><b>Постоянная угловая скорость</b> — плавная интерполяция с ограничением рывка
 *       (acceleration cap) убирает прямоугольные «плато» скорости;</li>
 *   <li><b>Снап на свежую цель</b> — при смене цели идёт реакционная пауза с микро-довод­кой,
 *       без телепорта прицела.</li>
 * </ul>
 *
 * <p>Гарантия попадания: после наложения шума итоговый луч проверяется slab-тестом на
 * пересечение с предсказанным хитбоксом; если шум вывел прицел за полигон, углы итеративно
 * подтягиваются обратно.
 */
public class FunSkyRotation extends RotationsSystem implements QClient {

    private LivingEntity trackedTarget;

    private float currentYaw;
    private float currentPitch;
    private float yawVel;
    private float pitchVel;

    private float lastSentYaw;
    private float lastSentPitch;

    // Перенос суб-GCD остатка между тиками (целые шаги мыши без сноса/заморозки).
    private float yawCarry;
    private float pitchCarry;

    // Дрейфующее смещение точки наведения внутри хитбокса.
    private Vec3d aimOffset = Vec3d.ZERO;
    private long lastRepick;
    private long repickInterval;

    private float noiseAngle;

    // --- Реакция (anti-snap) ---
    private long firstSeenTime;
    private int reactionMs;
    private boolean reactionComplete;

    // Доля углового пути за тик при дальнем доводе.
    private static final float APPROACH_FACTOR = 0.34F;
    // Человекоподобный потолок угловой скорости одного тика.
    private static final float MAX_STEP = 30.0F;
    // Потолок РЫВКА (изменения скорости за тик) — против постоянной угловой скорости.
    private static final float MAX_ACCEL = 9.0F;
    // Внутренний отступ точки наведения от грани коробки (в блоках).
    private static final double INNER_MARGIN = 0.08;
    // Упреждение позиции цели на N тиков вперёд (анти-стрейф).
    private static final int PREDICT_TICKS = 2;
    // Базовая амплитуда углового микрошума.
    private static final float NOISE_AMPLITUDE = 1.1F;
    // Порог «на прицеле» — режим мягкого сопровождения с микрошумом.
    private static final float TRACK_THRESHOLD = 4.0F;

    public void reset() {
        trackedTarget = null;
        aimOffset = Vec3d.ZERO;
        noiseAngle = 0.0F;
        yawVel = pitchVel = 0.0F;
        yawCarry = pitchCarry = 0.0F;
        lastRepick = 0;
        repickInterval = 0;
        reactionComplete = false;
        reactionMs = 0;
        firstSeenTime = 0;
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

    /** Реакционная пауза в мс по начальному угловому отклонению. */
    private int computeReaction(float angle) {
        if (angle > 120.0F) return 120 + (int) (Math.random() * 80);
        if (angle > 60.0F) return 75 + (int) (Math.random() * 50);
        if (angle > 25.0F) return 38 + (int) (Math.random() * 28);
        return 8 + (int) (Math.random() * 16);
    }

    /** Начальное угловое отклонение от взгляда игрока до центра цели. */
    private float measureAngle(LivingEntity target) {
        Vec3d eyes = mc.player.getEyePos();
        Vec3d mid = target.getBoundingBox().getCenter();
        Vec3d delta = mid.subtract(eyes);
        float needYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
        float needPitch = (float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalLength()));
        float dYaw = Math.abs(MathHelper.wrapDegrees(needYaw - mc.player.getYaw()));
        float dPitch = Math.abs(needPitch - mc.player.getPitch());
        return dYaw + dPitch;
    }

    /** Линейная скорость цели за прошлый тик. */
    private Vec3d targetVelocity(LivingEntity target) {
        return new Vec3d(
                target.getX() - target.prevX,
                target.getY() - target.prevY,
                target.getZ() - target.prevZ
        );
    }

    /** Дрейфующая точка наведения внутри хитбокса (несоизмеримые частоты => апериодичность). */
    private Vec3d pickAimPoint(Box box) {
        long t = System.currentTimeMillis();
        double yFrac = 0.62
                + Math.sin(t * 0.00039) * 0.14
                + Math.cos(t * 0.00091) * 0.09;
        yFrac = MathHelper.clamp(yFrac, 0.38, 0.85);

        double latRange = (box.maxX - box.minX) * 0.34;
        double depRange = (box.maxZ - box.minZ) * 0.34;
        double lateral = Math.sin(t * 0.00069 + 1.1) * latRange;
        double depth = Math.cos(t * 0.00113 + 0.6) * depRange;

        double mx = Math.min(INNER_MARGIN, (box.maxX - box.minX) * 0.48);
        double my = Math.min(INNER_MARGIN, (box.maxY - box.minY) * 0.48);
        double mz = Math.min(INNER_MARGIN, (box.maxZ - box.minZ) * 0.48);

        double cx = MathHelper.clamp(box.getCenter().x + lateral, box.minX + mx, box.maxX - mx);
        double cy = MathHelper.clamp(box.minY + (box.maxY - box.minY) * yFrac, box.minY + my, box.maxY - my);
        double cz = MathHelper.clamp(box.getCenter().z + depth, box.minZ + mz, box.maxZ - mz);

        return new Vec3d(cx, cy, cz);
    }

    /** Угловой микрошум (yaw, pitch), масштабированный дистанцией. */
    private float[] generateNoise(float dist) {
        noiseAngle += 0.043F + (float) (Math.random() * 0.02F);

        float scale = MathHelper.clamp(dist / 4.0F, 0.25F, 1.0F);
        float amp = NOISE_AMPLITUDE * scale;

        float n1 = (float) Math.sin(noiseAngle * 0.89) * 0.4F;
        float n2 = (float) Math.cos(noiseAngle * 1.31 + 0.5) * 0.3F;

        float yawNoise = n1 * amp + ((float) Math.random() - 0.5F) * amp * 0.12F;
        float pitchNoise = n2 * amp * 0.5F + ((float) Math.random() - 0.5F) * amp * 0.08F;

        return new float[]{yawNoise, pitchNoise};
    }

    /** Шаг к цели с ограничением скорости И рывка (анти constant-rotation-speed Grim). */
    private float approach(float current, float target, boolean yaw) {
        float diff = yaw ? MathHelper.wrapDegrees(target - current) : (target - current);
        float desired = MathHelper.clamp(diff * APPROACH_FACTOR, -MAX_STEP, MAX_STEP);

        float vel = yaw ? yawVel : pitchVel;
        float delta = MathHelper.clamp(desired - vel, -MAX_ACCEL, MAX_ACCEL);
        vel += delta;

        if (yaw) yawVel = vel; else pitchVel = vel;
        return current + vel;
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

    /** Апериодично перевыбирает точку наведения внутри хитбокса. */
    private void repickAimPoint(Box box) {
        aimOffset = pickAimPoint(box).subtract(box.getCenter());
        lastRepick = System.currentTimeMillis();
        repickInterval = 240 + (long) (Math.random() * 460);
    }

    @Override
    public void updateRotations(LivingEntity target) {
        if (mc.player == null || target == null) return;

        float gcd = calcGcd();

        if (trackedTarget != target) {
            trackedTarget = target;
            currentYaw = lastSentYaw = mc.player.getYaw();
            currentPitch = lastSentPitch = mc.player.getPitch();
            yawVel = pitchVel = 0.0F;
            yawCarry = pitchCarry = 0.0F;
            noiseAngle = (float) (Math.random() * Math.PI * 2);
            repickAimPoint(target.getBoundingBox());

            reactionMs = computeReaction(measureAngle(target));
            firstSeenTime = System.currentTimeMillis();
            reactionComplete = false;
        }

        Vec3d eye = mc.player.getEyePos();

        // --- Предсказанная коробка цели (упреждение по скорости — анти-стрейф) ---
        Box baseBox = target.getBoundingBox();
        Vec3d vel = targetVelocity(target);
        Vec3d predictedCenter = getPredictedPoint(target, baseBox.getCenter());
        Box box = baseBox.offset(predictedCenter.subtract(baseBox.getCenter()))
                .offset(vel.multiply(PREDICT_TICKS));

        float distance = (float) eye.distanceTo(box.getCenter());

        // --- Реакция (anti-snap): пред-наводка с микро-джиттером, без снапа ---
        if (!reactionComplete) {
            long elapsed = System.currentTimeMillis() - firstSeenTime;
            if (elapsed < reactionMs) {
                Vec3d preAim = box.getCenter();
                Vec3d preDir = preAim.subtract(eye);
                float needYaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(preDir.z, preDir.x)) - 90.0);
                float needPitch = (float) -Math.toDegrees(Math.atan2(preDir.y, preDir.horizontalLength()));

                float driftK = 0.04F + (float) (Math.random() * 0.03F);
                float driftY = MathHelper.wrapDegrees(needYaw - lastSentYaw) * driftK;
                float driftP = (needPitch - lastSentPitch) * driftK;

                float jitterY = ((float) Math.random() - 0.5F) * 0.22F;
                float jitterP = ((float) Math.random() - 0.5F) * 0.14F;
                emit(lastSentYaw + driftY + jitterY, lastSentPitch + driftP + jitterP, gcd);
                return;
            }
            reactionComplete = true;
        }

        // --- Апериодичная смена точки наведения ---
        if (System.currentTimeMillis() - lastRepick >= repickInterval) {
            repickAimPoint(box);
        }

        // --- Точка наведения: центр предсказанного хитбокса + дрейфующее смещение ---
        Vec3d aimPoint = box.getCenter().add(aimOffset);
        Vec3d dir = aimPoint.subtract(eye);
        float wantYaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0);
        float wantPitch = (float) -Math.toDegrees(Math.atan2(dir.y, dir.horizontalLength()));

        // --- Плавный доворот с ограничением скорости и рывка ---
        currentYaw = approach(currentYaw, wantYaw, true);
        currentPitch = MathHelper.clamp(approach(currentPitch, wantPitch, false), -89.0F, 89.0F);

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

        emit(outY, outP, gcd);
    }

    /**
     * GCD-квантование с переносом остатка и отправка. Желаемая дельта накапливается с
     * переносом ({@code carry}) и округляется к ближайшему целому числу шагов мыши — итоговая
     * дельта всегда кратна gcd, без систематического сноса от усечения и без суб-GCD «заморозки
     * с прыжком». Именно серию таких дельт анализирует Grim (AimModulo / sensitivity).
     */
    private void emit(float yaw, float pitch, float gcd) {
        float outP = MathHelper.clamp(pitch, -89.0F, 89.0F);

        float wantDeltaYaw = MathHelper.wrapDegrees(yaw - lastSentYaw) + yawCarry;
        float wantDeltaPitch = (outP - lastSentPitch) + pitchCarry;

        float stepsYaw = Math.round(wantDeltaYaw / gcd);
        float stepsPitch = Math.round(wantDeltaPitch / gcd);

        float sentDeltaYaw = stepsYaw * gcd;
        float sentDeltaPitch = stepsPitch * gcd;

        yawCarry = wantDeltaYaw - sentDeltaYaw;
        pitchCarry = wantDeltaPitch - sentDeltaPitch;

        float finalYaw = lastSentYaw + sentDeltaYaw;
        float finalPitch = MathHelper.clamp(lastSentPitch + sentDeltaPitch, -89.0F, 89.0F);

        lastSentYaw = finalYaw;
        lastSentPitch = finalPitch;

        RotationStorage.update(new Rotation(finalYaw, finalPitch), 360.0F, 45.0F, 45.0F, 45.0F, 0, 1, Aura.clientLook.isState());
    }
}
