package fun.lumis.features.impl.movement;

import fun.lumis.events.player.TickEvent;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.SelectSetting;
import fun.lumis.features.module.setting.implement.SliderSettings;
import fun.lumis.utils.client.managers.event.EventHandler;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.Vec3d;

public class ElytraMotion extends Module {

    private final SelectSetting mode = new SelectSetting("Mode", "Режим полета")
            .value("Matrix", "Grim").selected("Matrix");

    private final SliderSettings speed = new SliderSettings("Speed", "Горизонтальная скорость")
            .setValue(1.2F).range(0.1F, 3.0F);

    private final SliderSettings verticalSpeed = new SliderSettings("Vertical Speed", "Вертикальная скорость")
            .setValue(0.4F).range(0.1F, 1.5F);

    public ElytraMotion() {
        super("ElytraMotion", "ElytraMotion", ModuleCategory.MOVEMENT);
        setup(mode, speed, verticalSpeed);
    }

    @EventHandler
    public void onTick(TickEvent ignored) {
        if (mc.player == null || mc.world == null) return;

        // Авто-старт полета, если падаем
        if (!mc.player.isGliding() && mc.player.fallDistance > 0.5) {
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            return;
        }

        if (!mc.player.isGliding()) return;

        String currentMode = mode.getSelected();
        Vec3d look = mc.player.getRotationVector();
        double hSpeed = speed.getValue();
        double vSpeed = verticalSpeed.getValue();

        double motionX = look.x * hSpeed;
        double motionY = 0;
        double motionZ = look.z * hSpeed;

        // Управление высотой
        if (mc.options.jumpKey.isPressed()) {
            motionY = vSpeed;
        } else if (mc.options.sneakKey.isPressed()) {
            motionY = -vSpeed;
        }

        if (currentMode.equals("Matrix")) {
            // Плавный набор высоты или снижение
            if (!mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed()) {
                motionY = look.y * hSpeed; // Классическое управление, если ничего не зажато
            }
            mc.player.setVelocity(motionX, motionY, motionZ);
        }

        else if (currentMode.equals("Grim")) {
            // Более резкое и агрессивное движение
            if (!mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed()) {
                 motionY = -0.01; // Небольшое снижение по умолчанию
            }
            mc.player.setVelocity(motionX, motionY, motionZ);
        }
    }
}
