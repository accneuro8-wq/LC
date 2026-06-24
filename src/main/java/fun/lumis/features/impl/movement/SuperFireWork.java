package fun.lumis.features.impl.movement;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.math.Vec3d;

import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.BooleanSetting;
import fun.lumis.features.module.setting.implement.SelectSetting;
import fun.lumis.features.module.setting.implement.SliderSettings;
import fun.lumis.events.player.FireworkEvent;
import fun.lumis.utils.features.aura.warp.TurnsConnection;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SuperFireWork extends Module {

    // ══════════════════════════════════════════════
    // Настройки
    // ══════════════════════════════════════════════

    SelectSetting modeSetting = new SelectSetting("Режим", "Тип обхода античита")
            .value("Grim", "BravoHvH", "Custom");

    // ── Custom настройки ─────────────────────────
    SliderSettings speedSetting = new SliderSettings("Скорость", "Базовая скорость полёта")
            .range(1f, 50f)
            .setValue(20f)
            .visible(() -> modeSetting.isSelected("Custom"));

    SliderSettings boostFactor = new SliderSettings("Множитель буста", "Сила углового ускорения")
            .range(0.1f, 1.0f)
            .setValue(0.3f)
            .visible(() -> modeSetting.isSelected("Custom"));

    SliderSettings pitchThreshold = new SliderSettings("Порог Pitch", "Угол для вертикального ускорения")
            .range(30f, 85f)
            .setValue(60f)
            .visible(() -> modeSetting.isSelected("Custom"));

    // ── BravoHvH настройки ───────────────────────
    SliderSettings bravoStrong = new SliderSettings("Strong Mult", "Сильный множитель (45°)")
            .range(0.05f, 0.5f)
            .setValue(0.26f)
            .visible(() -> modeSetting.isSelected("BravoHvH"));

    SliderSettings bravoBase = new SliderSettings("Base Mult", "Базовый множитель")
            .range(0.8f, 1.0f)
            .setValue(0.95f)
            .visible(() -> modeSetting.isSelected("BravoHvH"));

    // ── Общие ────────────────────────────────────
    BooleanSetting verticalBoost = new BooleanSetting("Vertical Boost", "Ускорение по Y при крутом угле")
            .setValue(true);

    public SuperFireWork() {
        super("SuperFireWork", "Super FireWork", ModuleCategory.MOVEMENT);
        setup(modeSetting, speedSetting, boostFactor, pitchThreshold,
                bravoStrong, bravoBase, verticalBoost);
    }

    // ══════════════════════════════════════════════
    // Обработчик фейерверка
    // ══════════════════════════════════════════════

    @EventHandler
    public void onFirework(FireworkEvent e) {
        switch (modeSetting.getSelected()) {
            case "Grim"     -> handleGrim(e);
            case "BravoHvH" -> handleBravo(e);
            case "Custom"   -> handleCustom(e);
        }
    }

    // ══════════════════════════════════════════════
    // Режимы
    // ══════════════════════════════════════════════

    /**
     * Grim — стандартный угловой буст
     * Максимальное ускорение при повороте на ~45° от оси
     */
    private void handleGrim(FireworkEvent e) {
        double acceleration = calcAngleAcceleration();
        double boost = 1.0 + (0.3 * acceleration * acceleration);
        boolean yBoost = shouldBoostVertical();

        Vec3d vec = e.getVector();
        e.setVector(new Vec3d(
                vec.x * boost,
                yBoost ? vec.y * boost : vec.y,
                vec.z * boost
        ));
    }

    /**
     * BravoHvH — двухслойный буст
     * Сильный множитель привязан к 45° (диагональ)
     * Слабый множитель — плавная кривая по всему yaw
     */
    private void handleBravo(FireworkEvent e) {
        float yaw = TurnsConnection.INSTANCE.getRotation().getYaw();

        // Сильный буст — максимум при 45° от оси
        double accel45 = calcAngleAcceleration();
        double strongMult = bravoStrong.getValue();
        double strongBoost = strongMult * accel45 * accel45;

        // Слабый буст — плавная кривая по yaw
        double yawNorm = Math.abs(yaw % 90) / 90.0;
        double weakMult = strongMult / 2.2;
        double weakBoost = weakMult * yawNorm * yawNorm;

        double totalBoost = bravoBase.getValue() + strongBoost + weakBoost;
        boolean yBoost = shouldBoostVertical();

        Vec3d vec = e.getVector();
        e.setVector(new Vec3d(
                vec.x * totalBoost,
                yBoost ? vec.y * totalBoost : vec.y,
                vec.z * totalBoost
        ));
    }

    /**
     * Custom — полная настройка через слайдеры
     * Направление берётся из вектора взгляда
     */
    private void handleCustom(FireworkEvent e) {
        double acceleration = calcAngleAcceleration();
        double rotBoost = 1.0 + (boostFactor.getValue() * acceleration * acceleration);
        boolean yBoost = shouldBoostVertical();

        Vec3d direction = TurnsConnection.INSTANCE.getMoveRotation().toVector();
        double speed = speedSetting.getValue() / 20.0;
        double finalSpeed = speed * rotBoost;

        e.setVector(new Vec3d(
                direction.x * finalSpeed,
                yBoost ? direction.y * finalSpeed : direction.y * speed,
                direction.z * finalSpeed
        ));
    }

    // ══════════════════════════════════════════════
    // Утилиты
    // ══════════════════════════════════════════════

    /**
     * Угловое ускорение: 0.0 при взгляде по оси, 1.0 при 45°
     * Формула: |((yaw + offset) % 90 - offset)| / 45
     */
    private double calcAngleAcceleration() {
        float yaw = TurnsConnection.INSTANCE.getRotation().getYaw();
        int offset = yaw > 0f ? 45 : -45;
        return Math.abs((yaw + offset) % 90 - offset) / 45.0;
    }

    /**
     * Нужно ли ускорять по Y — при крутом угле pitch
     */
    private boolean shouldBoostVertical() {
        if (!verticalBoost.isValue()) return false;
        float pitch = Math.abs(TurnsConnection.INSTANCE.getMoveRotation().getPitch());
        return pitch > pitchThreshold.getValue();
    }
}