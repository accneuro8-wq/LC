package fun.lumis.features.impl.player;

import fun.lumis.events.keyboard.KeyEvent;
import fun.lumis.events.keyboard.MouseRotationEvent;
import fun.lumis.events.render.CameraEvent;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.BindSetting;
import fun.lumis.features.module.setting.implement.SelectSetting;
import fun.lumis.features.module.setting.implement.SliderSettings;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.utils.features.aura.utils.MathAngle;
import fun.lumis.utils.features.aura.warp.Turns;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FreeLook extends Module {

    // Настройки (public через @Getter)
    final BindSetting keySetting = new BindSetting("Клавиша", "Клавиша обзора");
    final SelectSetting modeSetting = new SelectSetting("Режим", "Способ активации")
            .value("По нажатию", "Удержание").selected("По нажатию");
    final SliderSettings distanceSetting = new SliderSettings("Дистанция", "Дальность камеры")
            .range(1.0f, 10.0f).setValue(4.0f);
    final SliderSettings sensitivitySetting = new SliderSettings("Чувствительность", "Скорость вращения")
            .range(0.05f, 0.3f).setValue(0.15f);

    // Состояние
    Perspective savedPerspective;
    Turns cameraAngle;
    boolean active;

    public FreeLook() {
        super("FreeLook", "Free Look", ModuleCategory.RENDER);
        setup(keySetting, modeSetting, distanceSetting, sensitivitySetting);
    }

    @Override
    public void deactivate() {
        stop();
        super.deactivate();
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        if (e.key() != keySetting.getKey() || e.key() == GLFW.GLFW_KEY_UNKNOWN) return;

        boolean pressed = e.action() == GLFW.GLFW_PRESS;
        boolean released = e.action() == GLFW.GLFW_RELEASE;

        if (modeSetting.isSelected("По нажатию")) {
            if (pressed) toggle();
        } else {
            if (pressed && !active) start();
            else if (released && active) stop();
        }
    }

    private void toggle() {
        if (active) stop();
        else start();
    }

    private void start() {
        if (mc.player == null) return;
        active = true;
        savedPerspective = mc.options.getPerspective();
        cameraAngle = MathAngle.cameraAngle();
        mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
    }

    private void stop() {
        active = false;
        if (savedPerspective != null) {
            mc.options.setPerspective(savedPerspective);
            savedPerspective = null;
        }
        cameraAngle = null;
    }

    @EventHandler
    public void onMouseRotation(MouseRotationEvent e) {
        if (!active) return;
        if (cameraAngle == null) cameraAngle = MathAngle.cameraAngle();

        float sens = sensitivitySetting.getValue();
        cameraAngle.setYaw(cameraAngle.getYaw() + e.getCursorDeltaX() * sens);
        cameraAngle.setPitch(MathHelper.clamp(cameraAngle.getPitch() + e.getCursorDeltaY() * sens, -90f, 90f));
        e.cancel();
    }

    @EventHandler
    public void onCamera(CameraEvent e) {
        if (!active || cameraAngle == null) return;
        e.setAngle(cameraAngle);
        e.setDistance(distanceSetting.getValue());
        e.cancel();
    }

    // Публичный метод для проверки активности
    public boolean isLooking() {
        return active;
    }
}