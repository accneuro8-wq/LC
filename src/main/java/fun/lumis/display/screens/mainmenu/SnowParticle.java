package fun.lumis.display.screens.mainmenu;

import net.minecraft.client.gui.DrawContext;

import java.util.Random;

public class SnowParticle {
    private static final Random RNG = new Random();

    float x, y;
    private float baseSpeedY;
    private float swayOffset, swaySpeed, swayAmplitude;
    private float size;
    private float alpha;
    private float lifetime;

    // Отталкивание
    private float pushVX, pushVY;
    private static final float PUSH_DRAG = 0.89f;

    // Плавное появление
    private float fadeIn;

    public SnowParticle(float sw, float sh) {
        respawn(sw, true);
    }

    public void respawn(float sw, boolean randomY) {
        x = RNG.nextFloat() * sw;
        y = randomY ? -RNG.nextFloat() * 60f : -RNG.nextFloat() * 15f - 5f;
        baseSpeedY = 0.2f + RNG.nextFloat() * 0.5f;
        swayOffset = RNG.nextFloat() * 6.283f;
        swaySpeed = 0.6f + RNG.nextFloat() * 1.5f;
        swayAmplitude = 0.1f + RNG.nextFloat() * 0.35f;
        size = 1f + RNG.nextFloat() * 2.5f;
        alpha = 0.2f + RNG.nextFloat() * 0.55f;
        lifetime = 0f;
        fadeIn = 0f;
        pushVX = 0f;
        pushVY = 0f;
    }

    public void update(float sw, float sh, float dt) {
        lifetime += dt;

        if (fadeIn < 1f) {
            fadeIn = Math.min(1f, fadeIn + dt * 1.5f);
        }

        float sway = (float) Math.sin(lifetime * swaySpeed + swayOffset) * swayAmplitude;

        pushVX *= PUSH_DRAG;
        pushVY *= PUSH_DRAG;

        x += sway + pushVX;
        y += baseSpeedY + pushVY;

        if (x < -20) x = sw + 20;
        if (x > sw + 20) x = -20;
    }

    public void pushFromMouse(float mx, float my, float radius, float strength) {
        float dx = x - mx;
        float dy = y - my;
        float dSq = dx * dx + dy * dy;
        float rSq = radius * radius;

        if (dSq < rSq && dSq > 0.5f) {
            float d = (float) Math.sqrt(dSq);
            float f = (1f - d / radius) * strength;
            pushVX += (dx / d) * f;
            pushVY += (dy / d) * f;
        }
    }

    public void render(DrawContext ctx, int baseColor) {
        float effectiveAlpha = alpha * fadeIn;
        int a = (int) (((baseColor >> 24) & 0xFF) * effectiveAlpha);
        if (a < 2) return;

        int col = (a << 24) | (baseColor & 0xFFFFFF);
        int ix = (int) x, iy = (int) y, is = Math.max(1, (int) size);

        ctx.fill(ix, iy, ix + is, iy + is, col);

        if (size > 1.6f && a > 20) {
            int ga = a / 6;
            ctx.fill(ix - 1, iy - 1, ix + is + 1, iy + is + 1,
                    (ga << 24) | (baseColor & 0xFFFFFF));
        }

        if (size > 2.2f && a > 35) {
            int ga2 = a / 12;
            ctx.fill(ix - 2, iy - 2, ix + is + 2, iy + is + 2,
                    (ga2 << 24) | (baseColor & 0xFFFFFF));
        }
    }
}