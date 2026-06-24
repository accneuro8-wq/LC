package fun.lumis.features.impl.movement;

import fun.lumis.events.player.TickEvent;
import fun.lumis.features.impl.combat.Aura;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.MultiSelectSetting;
import fun.lumis.features.module.setting.implement.SelectSetting;
import fun.lumis.features.module.setting.implement.SliderSettings;
import fun.lumis.utils.client.Instance;
import fun.lumis.utils.client.managers.event.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class TargetStrafe extends Module {

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();

    // ══════════════════════════════════════════════
    // Настройки
    // ══════════════════════════════════════════════

    public final SelectSetting mode = new SelectSetting("Режим", "Алгоритм стрейфа")
            .value("HvH", "HvH-2", "Matrix", "Grim")
            .selected("HvH-2");

    public final SliderSettings radius = new SliderSettings("Радиус", "Дистанция до цели")
            .setValue(3.0f)
            .range(0.5f, 6.0f);

    public final SliderSettings speed = new SliderSettings("Скорость", "Скорость движения")
            .setValue(0.30f)
            .range(0.15f, 0.50f);

    public final SliderSettings aggression = new SliderSettings("Агрессия", "Сила атаки")
            .setValue(0.7f)
            .range(0.1f, 1.0f)
            .visible(() -> isHvHMode());

    public final MultiSelectSetting settings = new MultiSelectSetting("Настройки", "Опции")
            .value("Авто прыжок", "Смена стороны", "Убегать", "Предикт цели", "Обход преград", "Сглаживание")
            .selected("Авто прыжок", "Смена стороны", "Предикт цели", "Обход преград");

    public final SliderSettings predictTicks = new SliderSettings("Предикт", "Тиков предсказания")
            .setValue(3f)
            .range(1f, 8f)
            .visible(() -> settings.isSelected("Предикт цели"));

    // ══════════════════════════════════════════════
    // Состояние
    // ══════════════════════════════════════════════

    private int side = 1;
    private long lastSideChange = 0;
    private long startTime = 0;
    private int tickCounter = 0;

    // HvH
    private int hvhPattern = 0;
    private int patternTicks = 0;
    private double jitter = 1.0;
    private boolean rushMode = false;
    private double rushIntensity = 0;

    // Сглаживание направления (НЕ радиуса!)
    private Vec3d smoothDir = Vec3d.ZERO;

    // Grim орбита
    private double orbitAngle = 0;

    public TargetStrafe() {
        super("TargetStrafe", ModuleCategory.MOVEMENT);
        setup(mode, radius, speed, aggression, settings, predictTicks);
    }

    public static TargetStrafe getInstance() {
        return Instance.get(TargetStrafe.class);
    }

    public boolean isMode(String modeName) {
        String selected = mode.getSelected();
        return selected != null && selected.equals(modeName);
    }

    public boolean isHvHMode() {
        return mode.isSelected("HvH") || mode.isSelected("HvH-2");
    }

    @Override
    public void activate() {
        startTime = System.currentTimeMillis();
        smoothDir = Vec3d.ZERO;
        orbitAngle = 0;
        side = 1;
        hvhPattern = 0;
        patternTicks = 0;
        jitter = 1.0;
        rushMode = false;
        rushIntensity = 0;
        tickCounter = 0;
    }

    @Override
    public void deactivate() {
        smoothDir = Vec3d.ZERO;
        side = 1;
    }

    // ══════════════════════════════════════════════
    // Основной тик
    // ══════════════════════════════════════════════

    @EventHandler
    public void onTick(TickEvent ignored) {
        if (mc.player == null || mc.world == null) return;

        LivingEntity target = Aura.getInstance().getTarget();
        if (target == null || !target.isAlive()) {
            smoothDir = Vec3d.ZERO;
            return;
        }

        tickCounter++;

        // Авто-прыжок
        if (settings.isSelected("Авто прыжок") && mc.player.isOnGround()) {
            mc.player.jump();
        }

        // Обновляем состояние
        updateState(target);
        handleSideSwitch(target);

        // Целевая позиция
        Vec3d targetPos = getPredictedPosition(target);

        // Расчёт направления
        Vec3d direction = computeDirection(targetPos, target);

        // Сглаживание ТОЛЬКО направления (не радиуса!)
        Vec3d finalDir;
        if (settings.isSelected("Сглаживание") && !isHvHMode()) {
            double factor = mode.isSelected("Matrix") ? 0.25 : 0.35;
            smoothDir = lerpDirection(smoothDir, direction, factor);
            finalDir = smoothDir;
        } else {
            // HvH — без сглаживания для максимальной резкости
            finalDir = direction;
            smoothDir = direction;
        }

        // Применяем движение
        if (lengthXZ(finalDir) > 0.001) {
            applyVelocity(finalDir, target);
        }
    }

    // ══════════════════════════════════════════════
    // Обновление состояния HvH
    // ══════════════════════════════════════════════

    private void updateState(LivingEntity target) {
        if (!isHvHMode()) return;

        // Джиттер — меняется каждые 2-4 тика
        if (tickCounter % (2 + random.nextInt(3)) == 0) {
            jitter = 0.75 + random.nextDouble() * 0.5; // 0.75 - 1.25
        }

        // Паттерн — меняется каждые 8-20 тиков
        patternTicks++;
        if (patternTicks > 8 + random.nextInt(12)) {
            hvhPattern = random.nextInt(6); // 6 паттернов
            patternTicks = 0;
        }

        // Rush mode
        boolean targetAttacking = target.handSwingTicks > 0 && target.handSwingTicks < 5;
        boolean targetHurt = target.hurtTime > 0;

        if (!settings.isSelected("Убегать")) {
            // Без "Убегать" — агрессивно атакуем когда цель атакует
            if (targetAttacking || targetHurt) {
                rushMode = true;
                rushIntensity = Math.min(rushIntensity + 0.15, 1.0);
            } else {
                rushIntensity = Math.max(rushIntensity - 0.08, 0);
                rushMode = rushIntensity > 0.3;
            }
        } else {
            rushMode = false;
            rushIntensity = 0;
        }
    }

    // ══════════════════════════════════════════════
    // Расчёт направления
    // ══════════════════════════════════════════════

    private Vec3d computeDirection(Vec3d targetPos, LivingEntity target) {
        return switch (mode.getSelected()) {
            case "HvH"    -> computeHvH(targetPos, target);
            case "HvH-2"  -> computeHvH2(targetPos, target);
            case "Matrix" -> computeMatrix(targetPos, target);
            case "Grim"   -> computeGrim(targetPos, target);
            default       -> computeHvH2(targetPos, target);
        };
    }

    /**
     * HvH — Максимальная агрессия
     * Резкие рывки, 6 паттернов, переменный джиттер
     */
    private Vec3d computeHvH(Vec3d targetPos, LivingEntity target) {
        Vec3d playerPos = mc.player.getPos();
        Vec3d toTarget = targetPos.subtract(playerPos);
        double dist = lengthXZ(toTarget);

        if (dist < 0.001) return Vec3d.ZERO;

        // ВАЖНО: используем radius.getValue() напрямую, без сглаживания!
        double targetRadius = radius.getValue();
        double agr = aggression.getValue();

        // Единичные векторы
        double ux = toTarget.x / dist;
        double uz = toTarget.z / dist;

        // ═══ РАДИАЛЬНАЯ КОРРЕКЦИЯ (держим дистанцию) ═══
        double radiusError = dist - targetRadius;
        // Очень сильная коррекция для мгновенной реакции
        double radialForce = radiusError * (0.6 + agr * 0.6); // 0.6 - 1.2

        // ═══ ТАНГЕНЦИАЛЬНОЕ ДВИЖЕНИЕ (по кругу) ═══
        double tx = -uz * side;
        double tz = ux * side;

        // Джиттер на тангенсе
        tx *= jitter;
        tz *= jitter;

        // ═══ ПАТТЕРН ═══
        double px = 0, pz = 0;
        double patternForce = 0.3 + agr * 0.4;

        switch (hvhPattern) {
            case 0 -> { // Зигзаг — резкие рывки влево-вправо
                double zigzag = ((tickCounter / 3) % 2 == 0) ? 1 : -1;
                px = -uz * zigzag * patternForce;
                pz = ux * zigzag * patternForce;
            }
            case 1 -> { // Рывок к цели
                px = ux * patternForce * 1.5;
                pz = uz * patternForce * 1.5;
            }
            case 2 -> { // Рывок от цели
                px = -ux * patternForce * 1.3;
                pz = -uz * patternForce * 1.3;
            }
            case 3 -> { // Спираль — переменный радиус
                double spiral = Math.sin(tickCounter * 0.4) * patternForce;
                px = ux * spiral;
                pz = uz * spiral;
            }
            case 4 -> { // Резкий боковой рывок
                double sideJump = (random.nextBoolean() ? 1 : -1) * patternForce * 1.4;
                px = -uz * sideJump;
                pz = ux * sideJump;
            }
            case 5 -> { // Хаотичный — рандомные направления
                px = (random.nextDouble() - 0.5) * patternForce * 2;
                pz = (random.nextDouble() - 0.5) * patternForce * 2;
            }
        }

        // ═══ RUSH MODE (атака) ═══
        if (rushMode) {
            // Приближаемся агрессивно
            radialForce += rushIntensity * 0.8;
            // Ускоряем стрейф
            tx *= 1.0 + rushIntensity * 0.4;
            tz *= 1.0 + rushIntensity * 0.4;
        }

        // ═══ УБЕГАНИЕ ═══
        if (settings.isSelected("Убегать") && target.handSwingTicks == 1) {
            radialForce = -0.9; // Резкий отскок
        }

        // ═══ ФИНАЛЬНЫЙ ВЕКТОР ═══
        double fx = tx + ux * radialForce + px;
        double fz = tz + uz * radialForce + pz;

        return normalizeXZ(fx, fz);
    }

    /**
     * HvH-2 — Динамический радиус + волновое движение
     */
    private Vec3d computeHvH2(Vec3d targetPos, LivingEntity target) {
        Vec3d playerPos = mc.player.getPos();
        Vec3d toTarget = targetPos.subtract(playerPos);
        double dist = lengthXZ(toTarget);

        if (dist < 0.001) return Vec3d.ZERO;

        double agr = aggression.getValue();
        double time = (System.currentTimeMillis() - startTime) / 300.0;

        // Динамический радиус — пульсация от настройки
        double baseRadius = radius.getValue();
        double pulseAmp = 0.2 + agr * 0.2; // 20-40% амплитуда
        double dynamicRadius = baseRadius * (1.0 + Math.sin(time) * pulseAmp);

        // Единичные векторы
        double ux = toTarget.x / dist;
        double uz = toTarget.z / dist;

        // ═══ РАДИАЛЬНАЯ КОРРЕКЦИЯ ═══
        double radiusError = dist - dynamicRadius;
        double radialForce = radiusError * (0.5 + agr * 0.5); // 0.5 - 1.0

        // ═══ ТАНГЕНЦИАЛЬНОЕ ДВИЖЕНИЕ ═══
        double tx = -uz * side;
        double tz = ux * side;

        // Волновой джиттер
        double wave = 1.0 + Math.sin(time * 2.5) * 0.2 * agr;
        tx *= wave;
        tz *= wave;

        // ═══ ВТОРИЧНАЯ ВОЛНА (красивая траектория) ═══
        double wave2 = Math.sin(time * 1.7) * 0.25 * agr;
        tx += ux * wave2;
        tz += uz * wave2;

        // ═══ RUSH MODE ═══
        if (rushMode) {
            radialForce += rushIntensity * 0.6;
            tx *= 1.0 + rushIntensity * 0.3;
            tz *= 1.0 + rushIntensity * 0.3;
        }

        // ═══ УБЕГАНИЕ ═══
        if (settings.isSelected("Убегать") && target.handSwingTicks == 1) {
            radialForce = -0.7;
        }

        // ═══ МИНИ-ДЖИТТЕР ═══
        if (tickCounter % 5 == 0) {
            double miniJitter = (random.nextDouble() - 0.5) * 0.15 * agr;
            tx += miniJitter;
            tz += miniJitter;
        }

        double fx = tx + ux * radialForce;
        double fz = tz + uz * radialForce;

        return normalizeXZ(fx, fz);
    }

    /**
     * Matrix — Плавный и осторожный
     */
    private Vec3d computeMatrix(Vec3d targetPos, LivingEntity target) {
        Vec3d playerPos = mc.player.getPos();
        Vec3d toTarget = targetPos.subtract(playerPos);
        double dist = lengthXZ(toTarget);

        if (dist < 0.001) return Vec3d.ZERO;

        double targetRadius = radius.getValue();

        double ux = toTarget.x / dist;
        double uz = toTarget.z / dist;

        // Умеренная радиальная коррекция
        double radiusError = dist - targetRadius;
        double radialForce = radiusError * 0.35;

        // Плавное тангенциальное
        double tx = -uz * side * 0.85;
        double tz = ux * side * 0.85;

        // Убегание (мягкое)
        if (settings.isSelected("Убегать") && target.handSwingTicks == 1) {
            radialForce = -0.4;
        }

        double fx = tx + ux * radialForce;
        double fz = tz + uz * radialForce;

        return normalizeXZ(fx, fz);
    }

    /**
     * Grim — Угловая орбита
     */
    private Vec3d computeGrim(Vec3d targetPos, LivingEntity target) {
        Vec3d playerPos = mc.player.getPos();

        double dx = playerPos.x - targetPos.x;
        double dz = playerPos.z - targetPos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist < 0.001) return Vec3d.ZERO;

        double targetRadius = radius.getValue();

        // Угол и шаг
        orbitAngle = Math.atan2(dz, dx);
        double angleStep = Math.min(speed.getValue(), 0.24) / Math.max(targetRadius, 0.5);
        orbitAngle += angleStep * side;

        // Целевая точка
        double orbX = targetPos.x + Math.cos(orbitAngle) * targetRadius;
        double orbZ = targetPos.z + Math.sin(orbitAngle) * targetRadius;

        // Радиальная коррекция
        double radiusError = dist - targetRadius;
        double radialForce = radiusError * 0.4;

        double toOrbX = orbX - playerPos.x;
        double toOrbZ = orbZ - playerPos.z;

        // Комбинируем
        double ux = -dx / dist;
        double uz = -dz / dist;

        double fx = toOrbX + ux * radialForce * 0.5;
        double fz = toOrbZ + uz * radialForce * 0.5;

        return normalizeXZ(fx, fz);
    }

    // ══════════════════════════════════════════════
    // Смена стороны
    // ══════════════════════════════════════════════

    private void handleSideSwitch(LivingEntity target) {
        long now = System.currentTimeMillis();
        long cooldown = isHvHMode() ? 80 : 150;

        if (now - lastSideChange < cooldown) return;

        boolean shouldSwitch = false;

        // Препятствия
        if (settings.isSelected("Обход преград")) {
            if (mc.player.horizontalCollision || checkVoidAhead()) {
                shouldSwitch = true;
            }
        }

        // Смена при атаке
        if (!shouldSwitch && settings.isSelected("Смена стороны")) {
            if (target.hurtTime == 1 || target.handSwingTicks == 1) {
                shouldSwitch = true;
            }
        }

        // HvH — случайная смена (реже)
        if (!shouldSwitch && isHvHMode() && random.nextInt(150) < 2) {
            shouldSwitch = true;
        }

        if (shouldSwitch) {
            side *= -1;
            lastSideChange = now;
        }
    }

    // ══════════════════════════════════════════════
    // Применение скорости
    // ══════════════════════════════════════════════

    private void applyVelocity(Vec3d dir, LivingEntity target) {
        if (mc.player == null) return;

        double spd = getEffectiveSpeed();

        // HvH: джиттер скорости
        if (isHvHMode()) {
            spd *= 0.9 + jitter * 0.2; // 0.9 - 1.15

            // Rush boost
            if (rushMode) {
                spd *= 1.0 + rushIntensity * 0.2;
            }
        }

        double vx = dir.x * spd;
        double vz = dir.z * spd;

        Vec3d vel = mc.player.getVelocity();
        mc.player.setVelocity(vx, vel.y, vz);
    }

    private double getEffectiveSpeed() {
        double base = speed.getValue();

        return switch (mode.getSelected()) {
            case "HvH"    -> base * 1.1;
            case "HvH-2"  -> base * 1.05;
            case "Matrix" -> base * 0.78;
            case "Grim"   -> Math.min(base, 0.24);
            default       -> base;
        };
    }

    // ══════════════════════════════════════════════
    // Утилиты
    // ══════════════════════════════════════════════

    private Vec3d getPredictedPosition(LivingEntity target) {
        Vec3d pos = target.getPos();

        if (!settings.isSelected("Предикт цели")) return pos;

        double ticks = predictTicks.getValue();

        // Ограничения для античитов
        if (mode.isSelected("Grim")) ticks = Math.min(ticks, 2.0);
        else if (mode.isSelected("Matrix")) ticks = Math.min(ticks, 2.5);

        return pos.add(target.getVelocity().multiply(ticks));
    }

    private Vec3d lerpDirection(Vec3d from, Vec3d to, double factor) {
        double x = from.x + (to.x - from.x) * factor;
        double z = from.z + (to.z - from.z) * factor;
        return normalizeXZ(x, z);
    }

    private Vec3d normalizeXZ(double x, double z) {
        double len = Math.sqrt(x * x + z * z);
        if (len < 0.001) return Vec3d.ZERO;
        return new Vec3d(x / len, 0, z / len);
    }

    private double lengthXZ(Vec3d v) {
        return Math.sqrt(v.x * v.x + v.z * v.z);
    }

    private boolean checkVoidAhead() {
        if (mc.player == null || mc.world == null) return false;

        Vec3d vel = mc.player.getVelocity();
        double len = lengthXZ(vel);
        if (len < 0.01) return false;

        double ax = vel.x / len * 1.3;
        double az = vel.z / len * 1.3;

        BlockPos pos = BlockPos.ofFloored(
                mc.player.getX() + ax,
                mc.player.getY() - 1,
                mc.player.getZ() + az
        );

        return mc.world.getBlockState(pos).isAir()
                && mc.world.getBlockState(pos.down()).isAir();
    }
}