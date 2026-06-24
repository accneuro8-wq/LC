package fun.lumis.display.screens.mainmenu;

import fun.lumis.utils.display.color.ColorAssist;
import fun.lumis.utils.display.font.Fonts;
import fun.lumis.utils.display.interfaces.QuickImports;
import fun.lumis.utils.display.shape.ShapeProperties;
import net.minecraft.client.gui.DrawContext;

public class SettingsPanelWidget implements QuickImports {

    private static final float PW = 160f, PH = 135f, PGAP = 5f;
    private static final float WB = 28f, WG = 4f;
    private static final int WROW = 4, WCNT = 8;
    private static final float TW = 28f, TH = 14f;
    private static final float GRID_OX = 10f, GRID_OY = 35f;
    private static final float TOGGLE_CS = 10f;

    // ── Цвета ──
    private static final int C_ACCENT     = 0xFF828CFF;
    private static final int C_OUT_IDLE   = 0x40606078;
    private static final int C_OUT_HOVER  = 0xA096A0FF;
    private static final int C_FILL_IDLE  = 0x18202030;
    private static final int C_FILL_HOVER = 0x403C4170;
    private static final int C_TXT_IDLE   = 0xFFBBBBCC;
    private static final int C_TXT_HOVER  = 0xFFFFFFFF;
    private static final int C_MUTED      = 0xFFA0A0B4;
    private static final int C_SUBTLE     = 0xFF6E6E82;
    private static final int C_SEPARATOR  = 0x20808090;

    // ── Скорости анимаций ──
    private static final float OPEN_SPEED    = 0.12f;
    private static final float HOVER_SPEED   = 0.15f;
    private static final float TOGGLE_SPEED  = 0.13f;
    private static final float SELECT_SPEED  = 0.18f;
    private static final float GLOW_PULSE_SP = 0.04f;

    // ── Состояние ──
    private boolean open;
    private float openAnim;
    private final float[] wHov = new float[WCNT];
    private final float[] wSelect = new float[WCNT]; // анимация выделения
    private float sAnim = 1f, sHovAnim, cHovAnim;
    private boolean sHov, cHov;
    private float glowPhase; // пульсация glow выбранного

    // ── Кэш ──
    private final ClientSettings cfg = ClientSettings.get();
    private int cachedSelected = -1;
    private float cachedPanelY;

    public boolean isOpen() { return open; }

    public void toggle() { open = !open; }

    public void close() { open = false; }

    public float getOpenAnim() { return openAnim; }

    public void tick() {
        float openTarget = open ? 1f : 0f;
        openAnim += (openTarget - openAnim) * OPEN_SPEED;
        if (Math.abs(openAnim - openTarget) < 0.001f) openAnim = openTarget;

        float snowTarget = cfg.isSnowEnabled() ? 1f : 0f;
        sAnim += (snowTarget - sAnim) * TOGGLE_SPEED;
        if (Math.abs(sAnim - snowTarget) < 0.001f) sAnim = snowTarget;

        sHovAnim += ((sHov ? 1f : 0f) - sHovAnim) * HOVER_SPEED;
        cHovAnim += ((cHov ? 1f : 0f) - cHovAnim) * HOVER_SPEED;

        int sel = cfg.getSelectedWallpaper();
        if (sel != cachedSelected) cachedSelected = sel;

        for (int i = 0; i < WCNT; i++) {
            float selTarget = (i + 1 == cachedSelected) ? 1f : 0f;
            wSelect[i] += (selTarget - wSelect[i]) * SELECT_SPEED;
        }

        glowPhase += GLOW_PULSE_SP;
        if (glowPhase > 6.2831853f) glowPhase -= 6.2831853f;
    }

    public void render(DrawContext ctx, float ax, float ay, int alpha, double op) {
        if (openAnim < 0.008f) return;

        float e = easeCubic(openAnim);
        int pa = alpha;
        float px = ax;
        float py = ay - PH - PGAP - 10f + (1f - e) * 14f;
        cachedPanelY = py;
        float scale = 0.95f + 0.05f * e;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(px + PW * 0.5f, py + PH, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.getMatrices().translate(-(px + PW * 0.5f), -(py + PH), 0);

        // Тень
        int shadowA = toA(op * e * 0.22);
        if (shadowA > 1) {
            drawRect(ctx, px + 3f, py + 3.5f, PW, PH, 10, packC(shadowA, 0, 0, 0));
        }

        // Фон
        rectangle.render(ShapeProperties.create(ctx.getMatrices(), px, py, PW, PH)
                .thickness(1).round(8)
                .outlineColor(applyA(C_OUT_IDLE, pa))
                .color(applyA(C_FILL_IDLE, pa))
                .build());

        // Заголовок
        Fonts.getSize(11, Fonts.Type.DEFAULT)
                .drawString(ctx.getMatrices(), "Settings", px + 10f, py + 7f, applyA(C_TXT_HOVER, pa));

        // Крестик
        int closeCol = ColorAssist.lerp(easeCubic(cHovAnim), C_SUBTLE, C_TXT_HOVER);
        float closeScale = 1f + cHovAnim * 0.08f;
        float closeX = px + PW - 16f, closeY = py + 7.5f;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(closeX + 3f, closeY + 4f, 0);
        ctx.getMatrices().scale(closeScale, closeScale, 1f);
        ctx.getMatrices().translate(-(closeX + 3f), -(closeY + 4f), 0);
        Fonts.getSize(10, Fonts.Type.DEFAULT)
                .drawString(ctx.getMatrices(), "✕", closeX, closeY, applyA(closeCol, pa));
        ctx.getMatrices().pop();

        drawSep(ctx, px, py + 20f, pa);

        Fonts.getSize(8, Fonts.Type.DEFAULT)
                .drawString(ctx.getMatrices(), "WALLPAPER", px + 10f, py + 25f, applyA(C_SUBTLE, pa));

        renderGrid(ctx, px + GRID_OX, py + GRID_OY, alpha, pa, op);

        float sY = py + GRID_OY + 2f * (WB + WG) + 4f;
        drawSep(ctx, px, sY, pa);

        Fonts.getSize(8, Fonts.Type.DEFAULT)
                .drawString(ctx.getMatrices(), "SNOW EFFECT", px + 10f, sY + 5f, applyA(C_SUBTLE, pa));

        boolean snowOn = cfg.isSnowEnabled();
        int labelCol = snowOn ? C_ACCENT : C_SUBTLE;
        Fonts.getSize(8, Fonts.Type.DEFAULT)
                .drawString(ctx.getMatrices(), snowOn ? "Enabled" : "Disabled",
                        px + 10f, sY + 15f, applyA(labelCol, (int) (pa * 0.55f)));

        renderToggle(ctx, px + PW - 40f, sY + 5f, pa, op);

        ctx.getMatrices().pop();
    }

    private void renderGrid(DrawContext ctx, float sx, float sy, int alpha, int pa, double op) {
        float glowPulse = 0.7f + 0.3f * (float) Math.sin(glowPhase);

        for (int i = 0; i < WCNT; i++) {
            int row = i / WROW, col = i % WROW;
            float bx = sx + col * (WB + WG);
            float by = sy + row * (WB + WG);

            float hov = wHov[i];
            float sel = wSelect[i];
            float eh = easeCubic(hov);
            float es = easeCubic(sel);
            float m = Math.max(eh, es);

            // Glow — пульсирует только для выбранного
            if (m > 0.01f) {
                float glowMul = sel > 0.5f ? glowPulse : 1f;
                int glowA = toA(op * m * (sel > 0.5f ? 0.16 : 0.08) * glowMul);
                if (glowA > 1) {
                    drawRect(ctx, bx - 2.5f, by - 2.5f, WB + 5f, WB + 5f, 9,
                            packC(glowA, 130, 140, 255));
                }
            }

            // Рамка с плавным переходом толщины
            float thick = 1f + es * 0.4f;
            int oc = ColorAssist.lerp(m, C_OUT_IDLE, C_OUT_HOVER);
            int fc = ColorAssist.lerp(eh, C_FILL_IDLE, C_FILL_HOVER);

            // Bounce при выделении
            float bounce = (float) Math.sin(es * 3.14159f) * 0.6f;
            float bxOff = -bounce * 0.3f, byOff = -bounce * 0.3f;
            float bwOff = bounce * 0.6f, bhOff = bounce * 0.6f;

            rectangle.render(ShapeProperties.create(ctx.getMatrices(),
                            bx + bxOff, by + byOff, WB + bwOff, WB + bhOff)
                    .thickness(thick).round(6)
                    .outlineColor(applyA(oc, pa))
                    .color(applyA(fc, pa))
                    .build());

            // Превью обоев
            String tex = i == 0 ? "backmenu.png" : "bg" + (i + 1) + ".png";
            int tA = toA(op * (0.45 + eh * 0.2));
            if (tA > 2) {
                try {
                    image.setTexture("textures/mainmenu/" + tex)
                            .render(ShapeProperties.create(ctx.getMatrices(),
                                            bx + 2.5f, by + 2.5f, WB - 5f, WB - 5f)
                                    .round(4)
                                    .color(applyA(packC(tA, 255, 255, 255), alpha))
                                    .build());
                } catch (Exception ignored) {}
            }

            // Номер
            int nc = ColorAssist.lerp(m, C_MUTED, C_TXT_HOVER);
            Fonts.getSize(8, Fonts.Type.DEFAULT)
                    .drawCenteredString(ctx.getMatrices(), String.valueOf(i + 1),
                            bx + WB / 2f, by + WB - 9f, applyA(nc, pa));

            // Галочка + акцентная линия для выбранного
            if (es > 0.02f) {
                int checkA = (int) (pa * 0.9f * es);
                Fonts.getSize(7, Fonts.Type.DEFAULT)
                        .drawString(ctx.getMatrices(), "✓", bx + 2.5f, by + 1.5f,
                                applyA(C_ACCENT, checkA));

                float lw = (WB - 10f) * es;
                if (lw > 0.5f) {
                    drawRect(ctx, bx + (WB - lw) / 2f, by + WB - 1.5f, lw, 1.2f, 1,
                            applyA(C_ACCENT, (int) (pa * 0.5f * es)));
                }
            }
        }
    }

    private void renderToggle(DrawContext ctx, float x, float y, int pa, double op) {
        float eh = easeCubic(sHovAnim);

        // Hover glow
        if (eh > 0.01f) {
            int ga = toA(op * eh * 0.08);
            if (ga > 1) {
                drawRect(ctx, x - 2.5f, y - 2.5f, TW + 5f, TH + 5f, 9,
                        packC(ga, 130, 140, 255));
            }
        }

        // Track
        int oc = ColorAssist.lerp(sAnim, C_OUT_IDLE, C_OUT_HOVER);
        int fc = ColorAssist.lerp(sAnim, C_FILL_IDLE, C_FILL_HOVER);
        rectangle.render(ShapeProperties.create(ctx.getMatrices(), x, y, TW, TH)
                .thickness(1f).round(7)
                .outlineColor(applyA(oc, pa))
                .color(applyA(fc, pa))
                .build());

        // Accent bar внутри track при включении
        if (sAnim > 0.05f) {
            float barW = (TW - 4f) * sAnim;
            int barA = toA(op * sAnim * 0.12);
            if (barA > 1) {
                drawRect(ctx, x + 2f, y + TH - 2.5f, barW, 1f, 1,
                        applyA(C_ACCENT, (int) (pa * sAnim * 0.4f)));
            }
        }

        // Thumb (кружок)
        float thumbTravel = TW - TOGGLE_CS - 4f;
        float cx = x + 2f + sAnim * thumbTravel;
        float cy = y + (TH - TOGGLE_CS) / 2f;

        // Bounce при переключении
        float thumbBounce = (float) Math.sin(sAnim * 3.14159f) * 1.2f;
        float thumbScale = 1f + thumbBounce * 0.04f;

        int thumbCol = ColorAssist.lerp(sAnim, C_MUTED, C_TXT_HOVER);
        float tcs = TOGGLE_CS * thumbScale;
        float tcx = cx - (tcs - TOGGLE_CS) * 0.5f;
        float tcy = cy - (tcs - TOGGLE_CS) * 0.5f;

        rectangle.render(ShapeProperties.create(ctx.getMatrices(), tcx, tcy, tcs, tcs)
                .round(5).color(applyA(thumbCol, (int) (pa * 0.92f))).build());

        // Thumb glow при включении
        if (sAnim > 0.5f) {
            float glowIntensity = (sAnim - 0.5f) * 2f;
            int glowA = (int) (pa * glowIntensity * 0.2f);
            if (glowA > 1) {
                drawRect(ctx, tcx - 2f, tcy - 2f, tcs + 4f, tcs + 4f, 7,
                        applyA(C_ACCENT, glowA));
            }
        }
    }

    private void drawSep(DrawContext ctx, float px, float y, int pa) {
        float sepW = PW - 20f;
        float halfW = sepW * 0.5f;
        int mid = applyA(C_SEPARATOR, pa);
        int edge = applyA(C_SEPARATOR, (int) (pa * 0.2f));
        float sx = px + 10f;
        rectangle.render(ShapeProperties.create(ctx.getMatrices(), sx, y, halfW, 0.7f)
                .round(1).color(edge, mid, edge, mid).build());
        rectangle.render(ShapeProperties.create(ctx.getMatrices(), sx + halfW, y, halfW, 0.7f)
                .round(1).color(mid, edge, mid, edge).build());
    }

    // ═══════════════════════════════════════════
    //  Ввод
    // ═══════════════════════════════════════════

    public boolean mouseClicked(double mx, double my, float ax, float ay) {
        if (!open || openAnim < 0.4f) return false;
        float px = ax, py = panelY(ay);

        // Крестик
        if (mx >= px + PW - 20f && mx <= px + PW - 4f && my >= py + 4f && my <= py + 18f) {
            open = false;
            return true;
        }

        // Сетка обоев
        float gx = px + GRID_OX, gy = py + GRID_OY;
        for (int i = 0; i < WCNT; i++) {
            int col = i % WROW, row = i / WROW;
            float bx = gx + col * (WB + WG), by = gy + row * (WB + WG);
            if (mx >= bx && mx <= bx + WB && my >= by && my <= by + WB) {
                cfg.setSelectedWallpaper(i + 1);
                return true;
            }
        }

        // Тоггл снега
        float sY = py + GRID_OY + 2f * (WB + WG) + 4f;
        float tx = px + PW - 40f, ty = sY + 5f;
        if (mx >= tx && mx <= tx + TW && my >= ty && my <= ty + TH) {
            cfg.toggleSnow();
            return true;
        }

        return false;
    }

    public void mouseMoved(double mx, double my, float ax, float ay) {
        if (!open || openAnim < 0.4f) {
            fadeOutHovers();
            return;
        }

        float px = ax, py = panelY(ay);

        // Крестик hover
        cHov = mx >= px + PW - 20f && mx <= px + PW - 4f && my >= py + 4f && my <= py + 18f;

        // Сетка hover
        float gx = px + GRID_OX, gy = py + GRID_OY;
        for (int i = 0; i < WCNT; i++) {
            int col = i % WROW, row = i / WROW;
            float bx = gx + col * (WB + WG), by = gy + row * (WB + WG);
            boolean hit = mx >= bx && mx <= bx + WB && my >= by && my <= by + WB;
            wHov[i] += ((hit ? 1f : 0f) - wHov[i]) * HOVER_SPEED;
        }

        // Тоггл hover
        float sY = py + GRID_OY + 2f * (WB + WG) + 4f;
        sHov = mx >= px + PW - 40f && mx <= px + PW - 12f && my >= sY + 5f && my <= sY + 5f + TH;
    }

    private void fadeOutHovers() {
        for (int i = 0; i < WCNT; i++) {
            wHov[i] *= (1f - HOVER_SPEED);
        }
        sHov = false;
        cHov = false;
    }

    private float panelY(float ay) {
        return ay - PH - PGAP - 10f + (1f - easeCubic(openAnim)) * 14f;
    }

    // ═══════════════════════════════════════════
    //  Утилиты
    // ═══════════════════════════════════════════

    private void drawRect(DrawContext ctx, float x, float y, float w, float h, int r, int c) {
        rectangle.render(ShapeProperties.create(ctx.getMatrices(), x, y, w, h)
                .round(r).color(c).build());
    }

    /** Cubic ease-out: более плавное торможение чем quadratic */
    static float easeCubic(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float i = 1f - t;
        return 1f - i * i * i;
    }

    static int applyA(int argb, int ext) {
        int a = ((argb >>> 24) & 0xFF) * ext;
        return (clamp255((a + 127) / 255) << 24) | (argb & 0x00FFFFFF);
    }

    static int packC(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    static int toA(double v) {
        return clamp255((int) (255.0 * v));
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}