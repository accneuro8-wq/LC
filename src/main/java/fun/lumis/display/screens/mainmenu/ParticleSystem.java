package fun.lumis.display.screens.mainmenu;

import fun.lumis.utils.display.interfaces.QuickImports;
import fun.lumis.utils.display.shape.ShapeProperties;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ParticleSystem implements QuickImports {

    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();
    private final int maxParticles;

    private static final float MOUSE_REPEL_RADIUS = 80f;
    private static final float MOUSE_REPEL_FORCE = 2.5f;
    private static final float CONNECTION_DISTANCE = 100f;
    private static final float BASE_SPEED = 0.3f;

    public ParticleSystem(int count) {
        this.maxParticles = count;
    }

    public void init(int screenW, int screenH) {
        particles.clear();
        for (int i = 0; i < maxParticles; i++) {
            particles.add(new Particle(
                    random.nextFloat() * screenW,
                    random.nextFloat() * screenH,
                    (random.nextFloat() - 0.5f) * BASE_SPEED,
                    (random.nextFloat() - 0.5f) * BASE_SPEED,
                    1.5f + random.nextFloat() * 2f,
                    0.3f + random.nextFloat() * 0.5f
            ));
        }
    }

    public void update(int screenW, int screenH, int mouseX, int mouseY, float delta) {
        float mx = (float) mouseX;
        float my = (float) mouseY;
        float dt = delta * 60f;

        for (Particle p : particles) {
            p.x += p.vx * dt;
            p.y += p.vy * dt;

            float dx = p.x - mx;
            float dy = p.y - my;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);

            if (dist < MOUSE_REPEL_RADIUS && dist > 0.1f) {
                float force = (1f - dist / MOUSE_REPEL_RADIUS) * MOUSE_REPEL_FORCE;
                p.vx += (dx / dist) * force * dt;
                p.vy += (dy / dist) * force * dt;
            }

            p.vx *= 0.98f;
            p.vy *= 0.98f;

            float speed = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy);
            if (speed < BASE_SPEED * 0.3f) {
                p.vx += (random.nextFloat() - 0.5f) * 0.05f;
                p.vy += (random.nextFloat() - 0.5f) * 0.05f;
            }

            if (p.x < -20) p.x = screenW + 20;
            if (p.x > screenW + 20) p.x = -20;
            if (p.y < -20) p.y = screenH + 20;
            if (p.y > screenH + 20) p.y = -20;

            p.pulsePhase += 0.02f * dt;
            p.currentAlpha = p.baseAlpha + (float) Math.sin(p.pulsePhase) * 0.15f;
        }
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, int baseAlpha) {
        if (baseAlpha < 2) return;

        float mx = (float) mouseX;
        float my = (float) mouseY;
        int size = particles.size();

        // Линии соединения
        for (int i = 0; i < size; i++) {
            Particle a = particles.get(i);

            // К курсору
            float dxM = a.x - mx;
            float dyM = a.y - my;
            float distM = (float) Math.sqrt(dxM * dxM + dyM * dyM);
            float maxMouseDist = CONNECTION_DISTANCE * 1.2f;

            if (distM < maxMouseDist) {
                float lineAlpha = (1f - distM / maxMouseDist) * 0.4f;
                int la = (int) (lineAlpha * baseAlpha);
                if (la > 1) {
                    int lineCol = packColor(160, 170, 255, Math.min(255, la));
                    renderThinRect(ctx, a.x, a.y, mx, my, lineCol);
                }
            }

            // Между частицами
            for (int j = i + 1; j < size; j++) {
                Particle b = particles.get(j);
                float dx = a.x - b.x;
                float dy = a.y - b.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                if (dist < CONNECTION_DISTANCE) {
                    float lineAlpha = (1f - dist / CONNECTION_DISTANCE) * 0.2f;
                    int la = (int) (lineAlpha * baseAlpha);
                    if (la > 1) {
                        int lineCol = packColor(130, 140, 255, Math.min(255, la));
                        renderThinRect(ctx, a.x, a.y, b.x, b.y, lineCol);
                    }
                }
            }
        }

        // Частицы
        for (Particle p : particles) {
            float alpha = Math.max(0f, Math.min(1f, p.currentAlpha));
            int a = (int) (alpha * baseAlpha);
            if (a < 2) continue;

            // Свечение
            int glowCol = packColor(140, 150, 255, Math.min(255, a / 3));
            float glowSize = p.size * 2f;
            rectangle.render(ShapeProperties.create(ctx.getMatrices(),
                            p.x - glowSize, p.y - glowSize,
                            glowSize * 2f, glowSize * 2f)
                    .round((int) glowSize)
                    .color(glowCol)
                    .build());

            // Ядро
            int coreCol = packColor(200, 210, 255, Math.min(255, a));
            float half = p.size / 2f;
            rectangle.render(ShapeProperties.create(ctx.getMatrices(),
                            p.x - half, p.y - half,
                            p.size, p.size)
                    .round(Math.max(1, (int) half))
                    .color(coreCol)
                    .build());
        }
    }

    private void renderThinRect(DrawContext ctx, float x1, float y1, float x2, float y2, int color) {
        float minX = Math.min(x1, x2);
        float minY = Math.min(y1, y2);
        float w = Math.max(0.5f, Math.abs(x2 - x1));
        float h = Math.max(0.5f, Math.abs(y2 - y1));

        rectangle.render(ShapeProperties.create(ctx.getMatrices(), minX, minY, w, h)
                .round(1)
                .color(color)
                .build());
    }

    private static int packColor(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    private static class Particle {
        float x, y, vx, vy;
        float size, baseAlpha, currentAlpha;
        float pulsePhase;

        Particle(float x, float y, float vx, float vy, float size, float alpha) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.baseAlpha = alpha;
            this.currentAlpha = alpha;
            this.pulsePhase = (float) (Math.random() * Math.PI * 2);
        }
    }
}