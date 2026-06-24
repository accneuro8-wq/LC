package fun.lumis.utils.features.aura.rotations.impl;

import fun.lumis.utils.features.aura.rotations.constructor.RotateConstructor;
import fun.lumis.utils.features.aura.warp.Turns;
import fun.lumis.utils.math.calc.Calculate;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * SpookytimeDuel — ротация под Grim для дуэлей (ближний бой 1v1).
 *
 * Что делает её безопасной под Grim:
 *  - Все дельты yaw/pitch округляются до кратного GCD клиента (обход AimA / GCD-проверки).
 *  - Ease-out: скорость пропорциональна остатку угла, без мгновенных снапов (обход RotationSpeed).
 *  - Лёгкий рандомизированный sway + редкие микро-твичи, чтобы трек не был «роботизированным» (обход AimB).
 *  - Питч жёстко зажат в [-89; 89], дельты ограничены по скорости.
 */
public class SpookytimeDuel extends RotateConstructor {
    private final Random random = new Random();

    private float lastYaw, lastPitch;
    private long lastTargetId = -1;
    private boolean firstLock = true;
    private float smooth = 0.5f;
    private float swayPhase = 0.0f;
    private long lastTwitch = 0;

    public SpookytimeDuel() {
        super("SpookytimeDuel");
    }

    @Override
    public Turns limitAngleChange(Turns currentTurns, Turns targetTurns, Vec3d vec3d, Entity entity) {
        if (mc.player == null || entity == null || !entity.isAlive()) return currentTurns;

        // Новая цель — мягко перехватываем с текущего угла игрока
        if (entity.getId() != lastTargetId) {
            lastYaw = currentTurns.getYaw();
            lastPitch = currentTurns.getPitch();
            lastTargetId = entity.getId();
            firstLock = true;
            smooth = 0.45f + random.nextFloat() * 0.1f;
            swayPhase = random.nextFloat() * 6.28f;
        }

        swayPhase += 0.18f + random.nextFloat() * 0.05f;

        // Упреждение по скорости цели — для дуэли (ближний бой) держим небольшим
        double dxV = entity.getX() - entity.prevX;
        double dyV = entity.getY() - entity.prevY;
        double dzV = entity.getZ() - entity.prevZ;

        double distance = mc.player.distanceTo(entity);
        double horizSpeed = Math.hypot(dxV, dzV);
        double predict = MathHelper.clamp(1.0 + horizSpeed * 1.6, 1.0, 1.8);

        Vec3d eye = mc.player.getEyePos();
        double aimHeight = entity.getEyeHeight(entity.getPose()) * (0.82 + random.nextDouble() * 0.08);

        double px = entity.getX() + dxV * predict;
        double py = entity.getY() + aimHeight + dyV * 0.5;
        double pz = entity.getZ() + dzV * predict;

        double dx = px - eye.x;
        double dy = py - eye.y;
        double dz = pz - eye.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);

        float idealYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float idealPitch = MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, hDist)), -89.0f, 89.0f);

        float yawDiff = MathHelper.wrapDegrees(idealYaw - lastYaw);
        float pitchDiff = idealPitch - lastPitch;
        float angleDist = (float) Math.hypot(yawDiff, pitchDiff);

        // Ease-out: ближе к цели — медленнее, как у человека
        float ratio = firstLock ? 0.55f : 0.32f;
        smooth = MathHelper.lerp(0.25f, smooth, ratio);
        float maxSpeed = firstLock ? 80.0f : 55.0f;
        float minSpeed = firstLock ? 12.0f : 4.0f;
        float speed = MathHelper.clamp(angleDist * smooth, minSpeed, maxSpeed);

        float nextYaw = lastYaw + MathHelper.clamp(yawDiff, -speed, speed);
        float nextPitch = lastPitch + MathHelper.clamp(pitchDiff, -speed, speed);

        // Лёгкий sway — амплитуда падает в ближнем бою
        float swayAmp = distance < 3.0 ? 0.6f : 1.1f;
        nextYaw += (float) (Math.sin(swayPhase * 1.7) * 1.3 + Math.cos(swayPhase * 2.3) * 0.6) * swayAmp * 0.4f;
        nextPitch += (float) (Math.cos(swayPhase * 1.5) * 0.9) * swayAmp * 0.3f;

        // Редкий микро-твич против проверок на «робота»
        if (System.currentTimeMillis() - lastTwitch > 30 + random.nextInt(40)) {
            nextYaw += (random.nextFloat() - 0.5f) * 0.3f;
            nextPitch += (random.nextFloat() - 0.5f) * 0.2f;
            lastTwitch = System.currentTimeMillis();
        }

        firstLock = false;

        // GCD-выравнивание — обязательно для Grim: дельта должна быть кратна шагу мыши
        double gcd = Calculate.computeGcd();
        if (gcd > 0.0) {
            float dYaw = nextYaw - lastYaw;
            float dPitch = nextPitch - lastPitch;
            dYaw = (float) (Math.round(dYaw / gcd) * gcd);
            dPitch = (float) (Math.round(dPitch / gcd) * gcd);
            nextYaw = lastYaw + dYaw;
            nextPitch = lastPitch + dPitch;
        }

        lastYaw = nextYaw;
        lastPitch = MathHelper.clamp(nextPitch, -89.0f, 89.0f);

        return new Turns(lastYaw, lastPitch);
    }

    @Override
    public Vec3d randomValue() {
        return Vec3d.ZERO;
    }

    public void reset() {
        lastYaw = 0;
        lastPitch = 0;
        lastTargetId = -1;
        firstLock = true;
        smooth = 0.5f;
        swayPhase = 0.0f;
        lastTwitch = 0;
    }
}
