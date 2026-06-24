package fun.lumis.display.screens.mainmenu;

import net.minecraft.client.gui.DrawContext;

import java.util.concurrent.ThreadLocalRandom;

public class SnowSystem {

    // ── Конфигурация ──
    private static final float REPULSE_RADIUS = 60f;
    private static final float REPULSE_RADIUS_SQ = REPULSE_RADIUS * REPULSE_RADIUS;
    private static final float REPULSE_FORCE = 1.8f;
    private static final float WIND_CHANGE_INTERVAL = 180f;
    private static final float WIND_LERP = 0.005f;
    private static final float SYSTEM_FADE_SPEED = 0.04f;
    private static final float SPAWN_MARGIN_TOP = -20f;
    private static final float DESPAWN_MARGIN_BOTTOM = 30f;
    private static final int MAX_SPAWNS_PER_TICK = 3;
    private static final float CLEANUP_THRESHOLD = 0.8f;

    // ── Данные частиц (SoA — Structure of Arrays для кэш-эффективности) ──
    private final int maxParticles;
    private final float spawnRate;

    private int particleCount = 0;
    private float[] posX, posY;
    private float[] velX, velY;
    private float[] baseSpeedY;
    private float[] size;
    private float[] alpha;
    private float[] alphaTarget;
    private float[] wobblePhase;
    private float[] wobbleSpeed;
    private float[] wobbleAmplitude;
    private float[] rotation;
    private float[] rotationSpeed;
    private float[] fadeInSpeed;

    // ── Состояние системы ──
    private float spawnTimer = 0f;
    private boolean enabled = true;
    private int snowColor = 0xFFFFFF;
    private float mouseX, mouseY;

    private float systemAlpha = 1f;
    private float targetSystemAlpha = 1f;

    private float windStrength = 0f;
    private float windTarget = 0f;
    private float windTimer = 0f;

    // ── Кэш размеров экрана ──
    private float lastScreenW, lastScreenH;
    private boolean needsFullReset = true;

    public SnowSystem(int max, float rate) {
        this.maxParticles = max;
        this.spawnRate = rate;
        allocateArrays(max);
    }

    private void allocateArrays(int capacity) {
        posX = new float[capacity];
        posY = new float[capacity];
        velX = new float[capacity];
        velY = new float[capacity];
        baseSpeedY = new float[capacity];
        size = new float[capacity];
        alpha = new float[capacity];
        alphaTarget = new float[capacity];
        wobblePhase = new float[capacity];
        wobbleSpeed = new float[capacity];
        wobbleAmplitude = new float[capacity];
        rotation = new float[capacity];
        rotationSpeed = new float[capacity];
        fadeInSpeed = new float[capacity];
    }

    // ═══════════════════════════════════════════
    //  Публичные методы
    // ═══════════════════════════════════════════

    /**
     * Полный сброс системы. Вызывать при открытии/переоткрытии экрана.
     * Снежинки начнут появляться заново сверху — без мусора на экране.
     */
    public void reset() {
        particleCount = 0;
        spawnTimer = 0f;
        windStrength = 0f;
        windTarget = 0f;
        windTimer = 0f;
        systemAlpha = 0f; // начинаем с нуля — плавный fade-in
        targetSystemAlpha = enabled ? 1f : 0f;
        needsFullReset = true;
    }

    public void setMousePosition(float mx, float my) {
        this.mouseX = mx;
        this.mouseY = my;
    }

    public void setSnowColor(int color) {
        this.snowColor = color & 0xFFFFFF;
    }

    public void setEnabled(boolean e) {
        this.enabled = e;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getParticleCount() {
        return particleCount;
    }

    // ═══════════════════════════════════════════
    //  Обновление
    // ═══════════════════════════════════════════

    public void update(float sw, float sh, float dt) {
        if (sw <= 0 || sh <= 0) return;

        lastScreenW = sw;
        lastScreenH = sh;

        // Fade системы
        targetSystemAlpha = enabled ? 1f : 0f;
        systemAlpha += (targetSystemAlpha - systemAlpha) * SYSTEM_FADE_SPEED;

        if (systemAlpha < 0.005f) {
            particleCount = 0;
            return;
        }

        if (needsFullReset) {
            needsFullReset = false;
            // Не спавним всё сразу — пусть появляются постепенно
        }

        updateWind(dt);
        spawnParticles(sw, sh, dt);
        updateParticles(sw, sh, dt);
    }

    private void updateWind(float dt) {
        windTimer += dt;
        if (windTimer > WIND_CHANGE_INTERVAL) {
            windTimer = 0f;
            windTarget = randomRange(-0.3f, 0.3f);
        }
        windStrength += (windTarget - windStrength) * WIND_LERP;
    }

    private void spawnParticles(float sw, float sh, float dt) {
        spawnTimer += dt;
        int spawned = 0;

        while (spawnTimer >= spawnRate && particleCount < maxParticles && spawned < MAX_SPAWNS_PER_TICK) {
            initParticle(particleCount, sw, sh, true);
            particleCount++;
            spawnTimer -= spawnRate;
            spawned++;
        }

        // Защита от накопления таймера
        if (spawnTimer > spawnRate * 4f) {
            spawnTimer = 0f;
        }
    }

    private void updateParticles(float sw, float sh, float dt) {
        float despawnY = sh + DESPAWN_MARGIN_BOTTOM;
        int cleanupThreshold = (int) (maxParticles * CLEANUP_THRESHOLD);

        for (int i = particleCount - 1; i >= 0; i--) {
            // Fade-in
            alpha[i] += (alphaTarget[i] - alpha[i]) * fadeInSpeed[i];

            // Покачивание (wobble)
            wobblePhase[i] += wobbleSpeed[i] * dt;
            float wobbleX = (float) Math.sin(wobblePhase[i]) * wobbleAmplitude[i];

            // Применяем силы
            float fx = windStrength * dt + wobbleX * dt;
            float fy = baseSpeedY[i] * dt;

            // Отталкивание от мыши
            float dmx = posX[i] - mouseX;
            float dmy = posY[i] - mouseY;
            float distSq = dmx * dmx + dmy * dmy;

            if (distSq < REPULSE_RADIUS_SQ && distSq > 0.01f) {
                float dist = (float) Math.sqrt(distSq);
                float factor = (1f - dist / REPULSE_RADIUS) * REPULSE_FORCE * dt;
                fx += (dmx / dist) * factor;
                fy += (dmy / dist) * factor;
            }

            // Применяем velocity с затуханием
            velX[i] = velX[i] * 0.95f + fx;
            velY[i] = velY[i] * 0.95f + fy;

            posX[i] += velX[i];
            posY[i] += velY[i];

            // Вращение
            rotation[i] += rotationSpeed[i] * dt;

            // Проверка границ
            if (posY[i] > despawnY) {
                if (particleCount > cleanupThreshold) {
                    // Удаляем: swap with last
                    removeParticle(i);
                } else {
                    // Респавн сверху
                    respawnAtTop(i, sw);
                }
            } else if (posX[i] < -50f) {
                posX[i] = sw + 20f;
            } else if (posX[i] > sw + 50f) {
                posX[i] = -20f;
            }
        }
    }

    // ═══════════════════════════════════════════
    //  Рендеринг
    // ═══════════════════════════════════════════

    public void render(DrawContext ctx, float globalAlpha) {
        if (systemAlpha < 0.005f || particleCount == 0) return;

        float effectiveAlpha = globalAlpha * systemAlpha;
        if (effectiveAlpha < 0.005f) return;

        int baseColorRGB = snowColor & 0xFFFFFF;

        for (int i = 0; i < particleCount; i++) {
            float pAlpha = alpha[i] * effectiveAlpha;
            if (pAlpha < 0.01f) continue;

            int a = clampByte((int) (pAlpha * 255f));
            if (a <= 0) continue;

            int color = (a << 24) | baseColorRGB;
            float s = size[i];
            float px = posX[i];
            float py = posY[i];

            // Рендерим как заполненный квадрат (маленький — выглядит как точка)
            int x1 = (int) (px - s * 0.5f);
            int y1 = (int) (py - s * 0.5f);
            int x2 = x1 + Math.max(1, (int) s);
            int y2 = y1 + Math.max(1, (int) s);

            ctx.fill(x1, y1, x2, y2, color);
        }
    }

    // ═══════════════════════════════════════════
    //  Инициализация частиц
    // ═══════════════════════════════════════════

    /**
     * Инициализировать частицу с нуля.
     *
     * @param spawnOnTop true = спавн сверху экрана, false = случайная Y
     */
    private void initParticle(int i, float sw, float sh, boolean spawnOnTop) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        posX[i] = rng.nextFloat() * sw;

        if (spawnOnTop) {
            // Спавн выше экрана — снежинка "падает" сверху
            posY[i] = SPAWN_MARGIN_TOP - rng.nextFloat() * 40f;
        } else {
            // Случайная позиция (для начального заполнения, если нужно)
            posY[i] = rng.nextFloat() * sh;
        }

        velX[i] = 0f;
        velY[i] = 0f;

        // Скорость падения: разная для разных размеров (маленькие медленнее)
        float sizeVal = 1f + rng.nextFloat() * 2.5f;
        size[i] = sizeVal;
        baseSpeedY[i] = 0.2f + sizeVal * 0.15f + rng.nextFloat() * 0.3f;

        // Прозрачность: начинаем с 0, плавно нарастает
        alpha[i] = 0f;
        alphaTarget[i] = 0.3f + rng.nextFloat() * 0.5f; // 0.3 - 0.8
        fadeInSpeed[i] = 0.01f + rng.nextFloat() * 0.03f;

        // Покачивание
        wobblePhase[i] = rng.nextFloat() * 6.28f;
        wobbleSpeed[i] = 0.5f + rng.nextFloat() * 1.5f;
        wobbleAmplitude[i] = 0.2f + rng.nextFloat() * 0.6f;

        // Вращение (визуальное, для будущего)
        rotation[i] = rng.nextFloat() * 360f;
        rotationSpeed[i] = -1f + rng.nextFloat() * 2f;
    }

    private void respawnAtTop(int i, float sw) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        posX[i] = rng.nextFloat() * sw;
        posY[i] = SPAWN_MARGIN_TOP - rng.nextFloat() * 30f;
        velX[i] = 0f;
        velY[i] = 0f;

        // Сброс альфы для плавного появления
        alpha[i] = 0f;
        alphaTarget[i] = 0.3f + rng.nextFloat() * 0.5f;
        fadeInSpeed[i] = 0.01f + rng.nextFloat() * 0.03f;

        // Новый wobble
        wobblePhase[i] = rng.nextFloat() * 6.28f;
    }

    /**
     * Удаление частицы swap-with-last (O(1), без сдвига массива).
     */
    private void removeParticle(int i) {
        int last = particleCount - 1;
        if (i != last) {
            posX[i] = posX[last];
            posY[i] = posY[last];
            velX[i] = velX[last];
            velY[i] = velY[last];
            baseSpeedY[i] = baseSpeedY[last];
            size[i] = size[last];
            alpha[i] = alpha[last];
            alphaTarget[i] = alphaTarget[last];
            wobblePhase[i] = wobblePhase[last];
            wobbleSpeed[i] = wobbleSpeed[last];
            wobbleAmplitude[i] = wobbleAmplitude[last];
            rotation[i] = rotation[last];
            rotationSpeed[i] = rotationSpeed[last];
            fadeInSpeed[i] = fadeInSpeed[last];
        }
        particleCount--;
    }

    // ═══════════════════════════════════════════
    //  Утилиты
    // ═══════════════════════════════════════════

    private static float randomRange(float min, float max) {
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }

    private static int clampByte(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}