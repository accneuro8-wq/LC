package fun.lumis.features.impl.movement;

import fun.lumis.events.player.TickEvent;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.SelectSetting;
import fun.lumis.features.module.setting.implement.SliderSettings;
import fun.lumis.utils.client.managers.event.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Fly extends Module {

    private final SelectSetting mode = new SelectSetting("Mode", "Режим")
            .value("Elytra", "Elytra-2", "Elytra-3")
            .selected("Elytra");

    private final SliderSettings speed = new SliderSettings("Speed", "Скорость")
            .setValue(1.0f)
            .range(0.1f, 5.0f);

    private final SliderSettings verticalSpeed = new SliderSettings("Vertical", "Верт. скорость")
            .setValue(0.3f)
            .range(0.05f, 5.0f);

    private int tickCounter = 0;
    private boolean glidePacketSent = false;

    public Fly() {
        super("Fly", ModuleCategory.MOVEMENT);
        setup(mode, speed, verticalSpeed);
    }

    @Override
    public void activate() {
        tickCounter = 0;
        glidePacketSent = false;
    }

    @Override
    public void deactivate() {
        ClientPlayerEntity p = mc.player;
        if (p != null && mc.world != null) {
            // Плавный выход — не обнуляем резко
            Vec3d vel = p.getVelocity();
            p.setVelocity(vel.x * 0.5, Math.min(vel.y, -0.05), vel.z * 0.5);
        }
    }

    @EventHandler
    public void onTick(TickEvent ignoredE) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        if (p.getInventory().getArmorStack(2).getItem() != Items.ELYTRA) return;

        tickCounter++;

        // Запуск глайдинга — только если не летим и нажат пробел
        if (!p.isGliding()) {
            glidePacketSent = false;
            if (mc.options.jumpKey.isPressed()) {
                if (p.isOnGround()) {
                    p.jump();
                } else if (!glidePacketSent) {
                    // Отправляем START_FALL_FLYING только ОДИН раз
                    p.networkHandler.sendPacket(
                            new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING)
                    );
                    glidePacketSent = true;
                }
            }
            return;
        }

        // Уже летим — не спамим START_FALL_FLYING
        glidePacketSent = false;

        switch (mode.getSelected()) {
            case "Elytra"   -> handleElytra(p);
            case "Elytra-2" -> handleElytra2(p);
            case "Elytra-3" -> handleElytra3(p);
        }
    }

    // ─────────────────────────────────────────────────────
    // Elytra — Полёт в направлении взгляда с имитацией физики
    //
    // Вместо жёсткой установки velocity, модифицируем
    // текущую скорость плавно. Это выглядит как ускорение
    // ракетой, а не как телепорт.
    // ─────────────────────────────────────────────────────
    private void handleElytra(ClientPlayerEntity p) {
        double spd = speed.getValue();
        double vSpd = verticalSpeed.getValue();

        Vec3d vel = p.getVelocity();
        Vec3d look = p.getRotationVector();

        float forward = p.input.movementForward;
        float sideways = p.input.movementSideways;

        double dx = vel.x;
        double dy = vel.y;
        double dz = vel.z;

        // Ускорение в направлении взгляда (как фейерверк)
        if (forward > 0) {
            double boost = spd * 0.05;
            dx += look.x * boost;
            dy += look.y * boost * 0.5;
            dz += look.z * boost;
        } else if (forward < 0) {
            double brake = 0.85;
            dx *= brake;
            dz *= brake;
        }

        // Стрейф
        if (sideways != 0) {
            double yaw = Math.toRadians(p.getYaw());
            double rightX = Math.cos(yaw);
            double rightZ = Math.sin(yaw);
            double strafeBoost = spd * 0.03;

            // sideways > 0 = A (лево), < 0 = D (право)
            dx -= rightX * sideways * strafeBoost;
            dz -= rightZ * sideways * strafeBoost;
        }

        // Вертикаль — плавное изменение
        if (mc.options.jumpKey.isPressed()) {
            dy += vSpd * 0.08;
        } else if (mc.options.sneakKey.isPressed()) {
            dy -= vSpd * 0.08;
        }

        // Ограничение скорости чтобы не флагать energy checks
        double maxHorizontal = spd * 1.5;
        double horizontalSpeed = Math.sqrt(dx * dx + dz * dz);
        if (horizontalSpeed > maxHorizontal) {
            double scale = maxHorizontal / horizontalSpeed;
            dx *= scale;
            dz *= scale;
        }

        dy = MathHelper.clamp(dy, -vSpd, vSpd);

        // Имитация сопротивления воздуха
        dx *= 0.99;
        dy *= 0.98;
        dz *= 0.99;

        p.setVelocity(dx, dy, dz);
    }

    // ─────────────────────────────────────────────────────
    // Elytra-2 — WASD полёт с имитацией физики
    //
    // Горизонтальное движение по WASD, не зависит от pitch.
    // Плавное ускорение с drag (сопротивлением воздуха).
    // ─────────────────────────────────────────────────────
    private void handleElytra2(ClientPlayerEntity p) {
        double spd = speed.getValue();
        double vSpd = verticalSpeed.getValue();

        Vec3d vel = p.getVelocity();
        Vec3d target = calcWASD(p, spd);

        double dx = vel.x;
        double dy = vel.y;
        double dz = vel.z;

        if (isMoving()) {
            // Плавное ускорение к целевой скорости
            double accel = 0.06 * spd;
            dx += (target.x - dx) * accel;
            dz += (target.z - dz) * accel;
        }

        // Вертикаль
        if (mc.options.jumpKey.isPressed()) {
            dy += vSpd * 0.06;
        } else if (mc.options.sneakKey.isPressed()) {
            dy -= vSpd * 0.06;
        }

        // Ограничения
        double maxH = spd * 1.2;
        double hSpd = Math.sqrt(dx * dx + dz * dz);
        if (hSpd > maxH) {
            double scale = maxH / hSpd;
            dx *= scale;
            dz *= scale;
        }
        dy = MathHelper.clamp(dy, -vSpd * 0.8, vSpd * 0.8);

        // Drag — имитация воздуха
        dx *= 0.98;
        dy *= 0.97;
        dz *= 0.98;

        p.setVelocity(dx, dy, dz);
    }

    // ─────────────────────────────────────────────────────
    // Elytra-3 — Креативный стиль но с имитацией физики
    //
    // Быстрая реакция, но не мгновенная.
    // Сопротивление воздуха + ограничение скорости.
    // ─────────────────────────────────────────────────────
    private void handleElytra3(ClientPlayerEntity p) {
        double spd = speed.getValue();
        double vSpd = verticalSpeed.getValue();

        Vec3d vel = p.getVelocity();
        Vec3d target = calcWASD(p, spd);

        double dx = vel.x;
        double dy = vel.y;
        double dz = vel.z;

        // Быстрое но не мгновенное ускорение
        double accel = isMoving() ? 0.12 : 0.08;

        dx += (target.x - dx) * accel;
        dz += (target.z - dz) * accel;

        // Вертикаль — быстрее чем другие режимы
        double targetY = 0;
        if (mc.options.jumpKey.isPressed()) targetY = vSpd * 0.5;
        if (mc.options.sneakKey.isPressed()) targetY = -vSpd * 0.5;

        dy += (targetY - dy) * 0.1;

        // Не двигаемся — активное торможение
        if (!isMoving() && !mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed()) {
            dx *= 0.9;
            dy *= 0.9;
            dz *= 0.9;

            // Полная остановка при маленькой скорости
            if (Math.abs(dx) < 0.001) dx = 0;
            if (Math.abs(dy) < 0.001) dy = 0;
            if (Math.abs(dz) < 0.001) dz = 0;
        }

        // Ограничения
        double maxH = spd * 1.3;
        double hSpd = Math.sqrt(dx * dx + dz * dz);
        if (hSpd > maxH) {
            double scale = maxH / hSpd;
            dx *= scale;
            dz *= scale;
        }
        dy = MathHelper.clamp(dy, -vSpd, vSpd);

        // Минимальный drag
        dx *= 0.99;
        dy *= 0.99;
        dz *= 0.99;

        p.setVelocity(dx, dy, dz);
    }

    // ─────────────────────────────────────────────
    // Утилиты
    // ─────────────────────────────────────────────

    private Vec3d calcWASD(ClientPlayerEntity p, double spd) {
        float forward = p.input.movementForward;
        float sideways = p.input.movementSideways;

        if (forward == 0 && sideways == 0) return Vec3d.ZERO;

        double len = Math.sqrt(forward * forward + sideways * sideways);
        double nF = forward / len;
        double nS = sideways / len;

        double yaw = Math.toRadians(p.getYaw());
        double sinYaw = Math.sin(yaw);
        double cosYaw = Math.cos(yaw);

        // forward: (-sin, cos), right: (cos, sin)
        // sideways > 0 = A (лево) = -right
        double dx = -sinYaw * nF - cosYaw * nS;
        double dz =  cosYaw * nF - sinYaw * nS;

        return new Vec3d(dx * spd, 0, dz * spd);
    }

    private boolean isMoving() {
        ClientPlayerEntity p = mc.player;
        return p != null && (p.input.movementForward != 0 || p.input.movementSideways != 0);
    }
}