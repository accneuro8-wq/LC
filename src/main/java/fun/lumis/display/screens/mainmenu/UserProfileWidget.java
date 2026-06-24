package fun.lumis.display.screens.mainmenu;

import antidaunleak.api.UserProfile;
import fun.lumis.utils.display.color.ColorAssist;
import fun.lumis.utils.display.font.Fonts;
import fun.lumis.utils.display.geometry.Render2D;
import fun.lumis.utils.display.interfaces.QuickImports;
import fun.lumis.utils.display.shape.ShapeProperties;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public class UserProfileWidget implements QuickImports {

    private final String username;
    private final SettingsPanelWidget settingsPanel = new SettingsPanelWidget();

    private float pulsePhase, glowPhase, breathPhase;
    private float settingsHoverAnim, frameHoverAnim;
    private boolean settingsHovered, frameHovered;
    private SnowSystem snowSystem;

    // ── Кэш идентификатора текстуры ──
    private static final Identifier STEVE_TEX =
            Identifier.of("minecraft", "textures/mainmenu/steve.png");

    // ── Layout ──
    private static final float AVATAR = 20f, DOT = 5f;
    private static final float FW = 155f, FH = 30f, FX = 6f, FY_OFF = 5f;
    private static final float SBTN = 30f, GAP = 3.5f, ROUND = 7f;
    private static final float LERP = 0.15f;
    private static final float TWO_PI = 6.2831853f;

    // ── Цвета ──
    private static final int C_ACCENT    = 0xFF828CFF;
    private static final int C_OUT_IDLE  = 0x40606078;
    private static final int C_OUT_HOVER = 0xA096A0FF;
    private static final int C_FILL_IDLE = 0x18202030;
    private static final int C_FILL_HOVER= 0x403C4170;
    private static final int C_TXT       = 0xFFBBBBCC;
    private static final int C_TXT_HI    = 0xFFFFFFFF;
    private static final int C_MUTED     = 0xFFA0A0B4;
    private static final int C_SUBTLE    = 0xFF6E6E82;
    private static final int C_ICON      = 0xFFD0D0E0;
    private static final int C_GREEN     = 0xFF23E141;
    private static final int C_SHADOW    = 0x000E0E12;

    public UserProfileWidget() {
        this.username = UserProfile.getInstance().profile("username");
        ClientSettings.get().setListener(new ClientSettings.SettingsListener() {
            @Override public void onWallpaperChanged(int w) {}
            @Override public void onSnowChanged(boolean e) {
                if (snowSystem != null) snowSystem.setEnabled(e);
            }
        });
    }

    public void setSnowSystem(SnowSystem s) { snowSystem = s; }
    public int getSelectedWallpaper() { return ClientSettings.get().getSelectedWallpaper(); }
    public boolean isSettingsPanelOpen() { return settingsPanel.isOpen(); }
    public void closeSettingsPanel() { settingsPanel.close(); }

    public void tick() {
        pulsePhase = wrap(pulsePhase + 0.07f);
        glowPhase  = wrap(glowPhase  + 0.05f);
        breathPhase= wrap(breathPhase+ 0.025f);

        settingsHoverAnim += ((settingsHovered ? 1f : 0f) - settingsHoverAnim) * LERP;
        frameHoverAnim    += ((frameHovered    ? 1f : 0f) - frameHoverAnim)    * LERP;
        snap();
        settingsPanel.tick();
    }

    public void render(DrawContext ctx, int sw, int sh, int alpha, double op) {
        if (alpha < 2) return;

        float bx = FX, by = sh - FH - FY_OFF;
        int pa = toA(op);

        // Дыхание рамки — лёгкий масштаб
        float breath = 1f + (float) Math.sin(breathPhase) * 0.002f;
        ctx.getMatrices().push();
        scaleAround(ctx, bx + FW * 0.5f, by + FH * 0.5f, breath);

        renderFrame(ctx, bx, by, alpha, op, pa);

        ctx.getMatrices().pop();

        float btnX = bx + FW + GAP;
        renderSettingsBtn(ctx, btnX, by, alpha, op, pa);
        settingsPanel.render(ctx, bx, by, alpha, op);
    }

    // ═══════════════════════════════════════════
    //  Profile Frame
    // ═══════════════════════════════════════════

    private void renderFrame(DrawContext ctx, float x, float y,
                             int alpha, double op, int pa) {
        float fh = ease(frameHoverAnim);

        // Тень
        rect(ctx, x + 1.5f, y + 2f, FW, FH, 8, withA(C_SHADOW, toA(op * 0.2)));

        // Hover glow
        if (fh > 0.01f) {
            int g = packC(toA(op * fh * 0.06), 130, 140, 255);
            rect(ctx, x - 2f, y - 2f, FW + 4f, FH + 4f, 10, g);
        }

        // Фон
        int oc = ColorAssist.lerp(fh * 0.4f, C_OUT_IDLE, C_OUT_HOVER);
        int fc = ColorAssist.lerp(fh * 0.3f, C_FILL_IDLE, C_FILL_HOVER);
        rectangle.render(ShapeProperties.create(ctx.getMatrices(), x, y, FW, FH)
                .thickness(1).round((int) ROUND)
                .outlineColor(applyA(oc, pa)).color(applyA(fc, pa)).build());

        // Аватар
        float ax = x + 6f, ay = y + (FH - AVATAR) / 2f;
        renderAvatar(ctx, ax, ay, alpha, op, pa);

        // Текст
        float tx = ax + AVATAR + 8f;
        Fonts.getSize(12, Fonts.Type.DEFAULT)
                .drawString(ctx.getMatrices(), username, tx, y + 10f, applyA(C_TXT, alpha));

        renderVersion(ctx, tx, y + 18f, alpha, pa);

        // Нижняя акцентная линия при hover
        if (fh > 0.02f) {
            float lw = (FW - 24f) * fh;
            rect(ctx, x + (FW - lw) / 2f, y + FH - 1.5f, lw, 1f, 1,
                    applyA(C_ACCENT, (int)(pa * fh * 0.25f)));
        }
    }

    private void renderAvatar(DrawContext ctx, float ax, float ay,
                              int alpha, double op, int pa) {
        // Зелёная рамка с пульсацией
        float gp = pulse(glowPhase, 0.35f, 0.65f);
        int greenGlow = applyA(withA(C_GREEN, toA(op * gp * 0.45)), alpha);
        rectangle.render(ShapeProperties.create(ctx.getMatrices(),
                        ax - 1.5f, ay - 1.5f, AVATAR + 3f, AVATAR + 3f)
                .thickness(1.2f).round(8)
                .outlineColor(greenGlow).color(0).build());

        // Фон аватара
        rect(ctx, ax, ay, AVATAR, AVATAR, 7, applyA(C_FILL_IDLE, pa));

        // Скин
        Render2D.drawTexture(ctx, STEVE_TEX,
                ax + 1.5f, ay + 1.5f, 17, 7, 32, 32, 32,
                applyA(0xFFFFFFFF, alpha));

        // Статус-точка (online)
        float p = pulse(pulsePhase, 0.35f, 0.65f);
        float dx = ax + AVATAR - 4f, dy = ay + AVATAR - 4f;

        // Подложка точки
        rect(ctx, dx - 1.5f, dy - 1.5f, DOT + 3f, DOT + 3f, 4,
                withA(C_SHADOW, toA(op * 0.88)));

        // Точка
        rect(ctx, dx, dy, DOT, DOT, 3,
                applyA(withA(C_GREEN, toA(op * p)), alpha));

        // Свечение точки
        if (p > 0.6f) {
            rect(ctx, dx - 1.5f, dy - 1.5f, DOT + 3f, DOT + 3f, 4,
                    packC(toA(op * (p - 0.6f) * 0.35), 35, 225, 65));
        }
    }

    private void renderVersion(DrawContext ctx, float x, float y, int alpha, int pa) {
        var f = Fonts.getSize(11, Fonts.Type.DEFAULT);
        String v1 = "Version ", v2 = "1.0.0", v3 = " Beta";
        float w1 = f.getStringWidth(v1), w2 = f.getStringWidth(v2);

        f.drawString(ctx.getMatrices(), v1, x, y,          applyA(C_SUBTLE, alpha));
        f.drawString(ctx.getMatrices(), v2, x + w1, y,     applyA(C_ACCENT, (int)(alpha * 0.7f)));
        f.drawString(ctx.getMatrices(), v3, x + w1 + w2, y,applyA(C_MUTED,  (int)(alpha * 0.45f)));
    }

    // ═══════════════════════════════════════════
    //  Settings Button
    // ═══════════════════════════════════════════

    private void renderSettingsBtn(DrawContext ctx, float x, float y,
                                   int alpha, double op, int pa) {
        float eh = ease(settingsHoverAnim);

        // Hover glow
        if (eh > 0.01f) {
            rect(ctx, x - 2.5f, y - 1.5f, SBTN + 5f, SBTN + 3f, 10,
                    packC((int)(55 * eh), 100, 120, 255));
        }

        // Тень
        rect(ctx, x + 1.5f, y + 2f, SBTN, SBTN, 8,
                withA(C_SHADOW, toA(op * 0.2)));

        // Фон
        int oc = ColorAssist.lerp(eh, C_OUT_IDLE, C_OUT_HOVER);
        int fc = ColorAssist.lerp(eh, C_FILL_IDLE, C_FILL_HOVER);
        rectangle.render(ShapeProperties.create(ctx.getMatrices(), x, y, SBTN, SBTN)
                .thickness(settingsHovered ? 2 : 1).round((int) ROUND)
                .outlineColor(applyA(oc, pa)).color(applyA(fc, pa)).build());

        // Акцентная линия снизу
        if (eh > 0.02f) {
            float lw = (SBTN - 10f) * eh;
            rect(ctx, x + (SBTN - lw) / 2f, y + SBTN - 2f, lw, 1.2f, 1,
                    applyA(C_ACCENT, (int)(pa * eh * 0.6f)));
        }

        // Иконка с микро-вращением при hover
        int ic = ColorAssist.lerp(eh,
                applyA(C_MUTED, (int)(pa * 0.6f)),
                applyA(C_ICON, pa));

        var iconFont = Fonts.getSize(18, Fonts.Type.ICONSTYPENEW);
        float iconH = iconFont.getStringHeight("s");
        float icx = x + SBTN / 2f, icy = y + SBTN / 2f - iconH / 2f + 7.8f;

        // Лёгкий поворот иконки при hover
        if (eh > 0.01f) {
            float rot = eh * 18f;
            ctx.getMatrices().push();
            scaleAround(ctx, icx, icy - 2f, 1f); // точка вращения
            ctx.getMatrices().translate(icx, icy - 2f, 0);
            ctx.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z
                    .rotationDegrees(rot));
            ctx.getMatrices().translate(-icx, -(icy - 2f), 0);
            iconFont.drawCenteredString(ctx.getMatrices(), "s", icx, icy, ic);
            ctx.getMatrices().pop();
        } else {
            iconFont.drawCenteredString(ctx.getMatrices(), "s", icx, icy, ic);
        }

        // Индикатор открытой панели — линия сверху
        float panelA = settingsPanel.getOpenAnim();
        if (panelA > 0.02f) {
            float lw = (SBTN - 8f) * panelA;
            rect(ctx, x + (SBTN - lw) / 2f, y + 1.5f, lw, 1.2f, 1,
                    applyA(C_ACCENT, (int)(pa * panelA * 0.55f)));
        }
    }

    // ═══════════════════════════════════════════
    //  Input
    // ═══════════════════════════════════════════

    public boolean mouseClicked(double mx, double my, int btn, int sw, int sh) {
        if (btn != 0) return false;
        float bx = FX, by = sh - FH - FY_OFF;
        float btnX = bx + FW + GAP;

        if (hit(mx, my, btnX, by, SBTN, SBTN)) {
            settingsPanel.toggle();
            return true;
        }
        return settingsPanel.mouseClicked(mx, my, bx, by);
    }

    public void mouseMoved(double mx, double my, int sw, int sh) {
        float bx = FX, by = sh - FH - FY_OFF;
        float btnX = bx + FW + GAP;

        settingsHovered = hit(mx, my, btnX, by, SBTN, SBTN);
        frameHovered    = hit(mx, my, bx, by, FW, FH);

        settingsPanel.mouseMoved(mx, my, bx, by);
    }

    // ═══════════════════════════════════════════
    //  Утилиты
    // ═══════════════════════════════════════════

    private void rect(DrawContext ctx, float x, float y, float w, float h, int r, int c) {
        rectangle.render(ShapeProperties.create(ctx.getMatrices(), x, y, w, h)
                .round(r).color(c).build());
    }

    private static void scaleAround(DrawContext ctx, float cx, float cy, float s) {
        ctx.getMatrices().translate(cx, cy, 0);
        ctx.getMatrices().scale(s, s, 1f);
        ctx.getMatrices().translate(-cx, -cy, 0);
    }

    private static boolean hit(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    /** sin pulse: возвращает значение в диапазоне [lo, hi] */
    private static float pulse(float phase, float lo, float hi) {
        return lo + (hi - lo) * (0.5f + 0.5f * (float) Math.sin(phase));
    }

    private static float wrap(float v) {
        return v > TWO_PI ? v - TWO_PI : v;
    }

    /** Snap анимации к target если разница < epsilon */
    private void snap() {
        if (Math.abs(settingsHoverAnim - (settingsHovered ? 1f : 0f)) < 0.002f)
            settingsHoverAnim = settingsHovered ? 1f : 0f;
        if (Math.abs(frameHoverAnim - (frameHovered ? 1f : 0f)) < 0.002f)
            frameHoverAnim = frameHovered ? 1f : 0f;
    }

    private static int withA(int rgb, int a) {
        return (clamp(a) << 24) | (rgb & 0x00FFFFFF);
    }

    static int applyA(int argb, int ext) {
        int a = ((argb >>> 24) & 0xFF) * ext;
        return (clamp((a + 127) / 255) << 24) | (argb & 0x00FFFFFF);
    }

    static int packC(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    static int toA(double v) { return clamp((int)(255.0 * v)); }

    private static int clamp(int v) { return v < 0 ? 0 : Math.min(v, 255); }

    private static float ease(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float i = 1f - t;
        return 1f - i * i * i;
    }
}