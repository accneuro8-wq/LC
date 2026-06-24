package fun.lumis.features.impl.render;

import fun.lumis.events.render.WorldRenderEvent;
import fun.lumis.events.player.JumpEvent;
import fun.lumis.utils.display.geometry.Render3D;
import fun.lumis.utils.math.Counter;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.SliderSettings;
import fun.lumis.features.module.setting.implement.ColorSetting;
import fun.lumis.features.module.setting.implement.SelectSetting;
import fun.lumis.utils.display.color.ColorAssist;
import org.joml.Vector4i;
import java.util.ArrayList;
import java.util.List;
import java.awt.Color;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class JumpCircle extends Module {

    private final List<Circle> circles = new ArrayList<>();
    final Identifier circleTexture = Identifier.of("textures/circle2.png");

    final SliderSettings maxSize = new SliderSettings("Max Size", "Максимальный размер круга").setValue(2.5f).range(1.0f, 3.0f);
    final SliderSettings speed = new SliderSettings("Speed", "Скорость анимации").setValue(1000f).range(500f, 5000f);

    final SelectSetting colorMode = new SelectSetting("Режим цвета", "Откуда брать цвет")
            .value("Client", "Custom")
            .selected("Client");

    final ColorSetting customColor = new ColorSetting("Кастомный цвет", "Выберите цвет круга")
            .value(new Color(225, 225, 255, 255).getRGB())
            .visible(() -> colorMode.isSelected("Custom"));

    public JumpCircle() {
        super("JumpCircle", "Jump Circle", ModuleCategory.RENDER);
        setup(maxSize, speed, colorMode, customColor);
    }

    @EventHandler
    public void onJump(JumpEvent event) {
        if (mc.player == null || event.getPlayer() != mc.player) return;

        Vec3d pos = new Vec3d(
                mc.player.getX(),
                Math.floor(mc.player.getY()) + 0.001,
                mc.player.getZ()
        );
        circles.add(new Circle(pos, new Counter()));
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        circles.removeIf(c -> c.timer.passedMs((long) speed.getValue()));
        renderCircles();
    }

    private void renderCircles() {
        if (circles.isEmpty()) return;

        for (Circle circle : circles) {
            renderSingleCircle(circle);
        }
    }

    private void renderSingleCircle(Circle circle) {
        float lifeTime = (float) circle.timer.getPassedTimeMs();
        float maxTime = speed.getValue();
        float progress = Math.min(lifeTime / maxTime, 1f);

        if (progress >= 1f) return;

        float easedProgress = bounceOut(progress);
        float scale = easedProgress * maxSize.getValue();

        float fadeInDuration = 0.15f;
        float glowStart = 0.65f;
        float fadeOutStart = 0.85f;
        float alpha;

        if (progress < fadeInDuration) {
            alpha = progress / fadeInDuration;
        } else if (progress >= fadeOutStart) {
            float fadeOutProgress = (progress - fadeOutStart) / (1f - fadeOutStart);
            alpha = 1f - fadeOutProgress;

            if (progress > glowStart) {
                float glowProgress = (progress - glowStart) / (fadeOutStart - glowStart);
                float glowPulse = (float) (Math.sin(glowProgress * Math.PI * 3) * 0.3 + 0.3);
                alpha += glowPulse * (1f - fadeOutProgress);
            }
        } else if (progress > glowStart) {
            float glowProgress = (progress - glowStart) / (fadeOutStart - glowStart);
            float glowPulse = (float) (Math.sin(glowProgress * Math.PI * 3) * 0.3 + 0.3);
            alpha = 1f + glowPulse;
        } else {
            alpha = 1f;
        }

        alpha = Math.max(0f, Math.min(1f, alpha));

        int baseColor = colorMode.isSelected("Client") ? ColorAssist.getClientColor() : customColor.getColor();
        int finalColor = ColorAssist.multAlpha(baseColor, alpha);

        Camera camera = mc.getEntityRenderDispatcher().camera;
        Vec3d cameraPos = camera.getPos();
        Vec3d circlePos = circle.pos();

        MatrixStack matrixStack = new MatrixStack();

        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));

        matrixStack.translate(circlePos.x - cameraPos.x, circlePos.y - cameraPos.y, circlePos.z - cameraPos.z);

        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));

        MatrixStack.Entry entry = matrixStack.peek();
        Vector4i colors = new Vector4i(finalColor, finalColor, finalColor, finalColor);

        Render3D.drawTexture(entry, circleTexture, -scale/2, -scale/2, scale, scale, colors, true);
    }

    private float bounceOut(float value) {
        float n1 = 7.5625f;
        float d1 = 2.75f;
        if (value < 1.0f / d1) {
            return n1 * value * value;
        } else if (value < 2.0f / d1) {
            return n1 * (value -= 1.5f / d1) * value + 0.75f;
        } else if (value < 2.5f / d1) {
            return n1 * (value -= 2.25f / d1) * value + 0.9375f;
        } else {
            return n1 * (value -= 2.625f / d1) * value + 0.984375f;
        }
    }

    public record Circle(Vec3d pos, Counter timer) {}
}