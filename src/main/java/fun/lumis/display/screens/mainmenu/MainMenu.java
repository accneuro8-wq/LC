package fun.lumis.display.screens.mainmenu;

import fun.lumis.common.animation.Direction;
import fun.lumis.common.animation.implement.Decelerate;
import fun.lumis.display.screens.mainmenu.altscreen.AltScreen;
import fun.lumis.utils.client.text.TextAnimation;
import fun.lumis.utils.display.color.ColorAssist;
import fun.lumis.utils.display.font.Fonts;
import fun.lumis.utils.display.gif.GifRender;
import fun.lumis.utils.display.interfaces.QuickImports;
import fun.lumis.utils.display.shape.ShapeProperties;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MainMenu extends Screen implements QuickImports {
    public static MainMenu INSTANCE = new MainMenu();

    // ── Layout ──
    private static final int GUI_SCALE = 2;
    private static final float BTN_WIDTH = 120f, BTN_HEIGHT = 20f, BTN_GAP = 3.5f, BTN_ROUND = 7f;
    private static final float BTN_HALF_W = (BTN_WIDTH - BTN_GAP) / 2f;
    private static final float LOGO_OFFSET_Y = -95f, TITLE_OFFSET_Y = -65f;
    private static final float SUB_OFFSET_Y = -38f, BTN_START_Y = -12f;
    private static final float PAR_RANGE = 12f, PAR_LERP = 0.06f;
    private static final long TOGGLE_CD = 350;
    private static final int FADE_MS = 300;
    private static final float INTRO_SPEED = 0.032f, BLUR_IN = 0.07f, BLUR_OUT = 0.05f;
    private static final float TITLE_GLOW_SPEED = 0.02f, SEP_TARGET = 80f, SEP_SPEED = 0.06f;
    private static final double TWO_PI = Math.PI * 2.0;

    // ── Максимум снежинок (защита от накопления) ──
    private static final int MAX_SNOW_PARTICLES = 150;

    // ── Цвета ──
    private static final int C_ACCENT = 0xFF828CFF, C_OUT_IDLE = 0x6E606078, C_OUT_HOVER = 0xE096A0FF;
    private static final int C_FILL_IDLE = 0x38202030, C_FILL_HOVER = 0x7A3C4170;
    private static final int C_TXT_IDLE = 0xFFBBBBCC, C_TXT_HOVER = 0xFFFFFFFF;
    private static final int C_MUTED = 0xFFA0A0B4, C_SUBTLE = 0xFF6E6E82, C_ICON = 0xFFD0D0E0;
    private static final int C_TITLE_GLOW = 0x28828CFF, C_SEPARATOR = 0x30808090;

    // ── Состояние ──
    private int sw, sh, cSW = -1, cSH = -1;
    private float cx, cy, parX, parY, parTX, parTY;
    private float intro = 0f, blur = 0f, titleGlow = 0f, separatorWidth = 0f;
    private boolean altOpen = false;
    private long lastToggle = 0;
    private boolean initialized = false;

    // ── Компоненты ──
    private final TextAnimation welcomeAnim = new TextAnimation();
    private final GifRender bgGif = new GifRender("minecraft:gif/backgrounds/mainmenutype1", 1);
    private final SnowSystem snow = new SnowSystem(MAX_SNOW_PARTICLES, 0.02f);
    private final UserProfileWidget profile = new UserProfileWidget();
    private final Decelerate altFade = new Decelerate();
    private final Decelerate mainFade = new Decelerate();
    private final List<Btn> buttons = new ArrayList<>(5);
    private AltScreen altPanel;

    // ── Кэш цветов alt панели (переиспользуемые объекты) ──
    private final Color[] altColors = new Color[5];
    // Кэш предыдущих ARGB значений чтобы не создавать Color каждый кадр
    private final int[] altColorCache = new int[5];

    public MainMenu() {
        super(Text.of("Lumis"));
        altFade.setMs(FADE_MS).setValue(1.0).setDirection(Direction.BACKWARDS);
        mainFade.setMs(FADE_MS).setValue(1.0).setDirection(Direction.FORWARDS);
        profile.setSnowSystem(snow);
        buildButtons();
        // Инициализируем кэш цветов
        for (int i = 0; i < 5; i++) {
            altColors[i] = new Color(0, 0, 0, 0);
            altColorCache[i] = 0;
        }
    }

    /**
     * Полный сброс состояния экрана — вызывается при каждом открытии.
     * Гарантирует что снег, анимации и параллакс стартуют чисто.
     */
    private void resetState() {
        // Сброс intro анимации — экран появляется СВЕРХУ (slide сверху вниз)
        intro = 0f;
        blur = 0f;
        titleGlow = 0f;
        separatorWidth = 0f;

        // Сброс параллакса в центр чтобы не дёргало
        parX = 0f;
        parY = 0f;
        parTX = 0f;
        parTY = 0f;

        // Сброс alt панели
        altOpen = false;
        lastToggle = 0;
        altFade.setMs(FADE_MS).setValue(1.0).setDirection(Direction.BACKWARDS);
        mainFade.setMs(FADE_MS).setValue(1.0).setDirection(Direction.FORWARDS);
        altPanel = null;

        // Сброс снега — ключевой фикс!
        snow.reset();

        // Сброс hover состояний кнопок
        for (Btn btn : buttons) {
            btn.hover = 0f;
            btn.press = 0f;
            btn.glow = 0f;
            btn.wasHov = false;
        }

        // Сброс layout кэша чтобы пересчитать
        cSW = -1;
        cSH = -1;
    }

    @Override
    protected void init() {
        super.init();
        // Каждый раз при открытии экрана — полный сброс
        resetState();
        initialized = true;
    }

    private void buildButtons() {
        buttons.clear();
        buttons.add(new Btn("Одиночная игра", 0, false, false, () -> mc.setScreen(new SelectWorldScreen(this))));
        buttons.add(new Btn("Сетевая игра", 1, false, false, () -> mc.setScreen(new MultiplayerScreen(this))));
        buttons.add(new Btn("Аккаунты", 2, false, false, this::toggleAlt));
        buttons.add(new Btn("", 3, true, false, mc::stop));
        buttons.add(new Btn("", 3, true, true, () -> mc.setScreen(new OptionsScreen(this, mc.options))));
    }

    private void recalcLayout() {
        cx = sw / 2f;
        cy = sh / 2f;
        float baseX = cx - BTN_WIDTH / 2f;
        for (Btn btn : buttons) {
            btn.layout(baseX, cy + BTN_START_Y);
        }
    }

    private String getWallpaperTexture() {
        int idx = profile.getSelectedWallpaper();
        if (idx == 1) return "textures/mainmenu/backmenu.png";
        return "textures/mainmenu/bg" + idx + ".png";
    }

    @Override
    public void tick() {
        super.tick();
        welcomeAnim.updateText();
        profile.tick();
        if (altPanel != null) altPanel.tick();

        // Снег: обновляем только если экран виден и инициализирован
        if (initialized && sw > 0 && sh > 0) {
            snow.update(sw, sh, 0.016f);
        }

        if (intro < 1f) {
            intro = Math.min(1f, intro + INTRO_SPEED);
        }

        if (altOpen) {
            blur = Math.min(1f, blur + BLUR_IN);
        } else {
            blur = Math.max(0f, blur - BLUR_OUT);
        }

        titleGlow = (float) ((titleGlow + TITLE_GLOW_SPEED) % TWO_PI);

        if (separatorWidth < SEP_TARGET) {
            separatorWidth += (SEP_TARGET - separatorWidth) * SEP_SPEED;
        }
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        mc.options.getGuiScale().setValue(GUI_SCALE);
        sw = window.getScaledWidth();
        sh = window.getScaledHeight();

        // Защита от нулевых размеров
        if (sw <= 0 || sh <= 0) return;

        if (sw != cSW || sh != cSH) {
            cSW = sw;
            cSH = sh;
            recalcLayout();
        }

        updateParallax(mx, my);
        snow.setMousePosition(mx, my);

        // ── Фон ──
        renderBackground(ctx);

        // ── Снег (рендерим только при видимости) ──
        float snowAlpha = 0.75f * intro;
        if (snowAlpha > 0.01f) {
            snow.render(ctx, snowAlpha);
        }

        // ── Основной UI ──
        double mainOp = mainFade.getOutput() * intro;
        if (mainOp > 0.005) {
            renderMainUI(ctx, mx, my, mainOp, delta);
        }

        // ── Blur overlay для alt панели ──
        if (blur > 0.003f) {
            renderBlurOverlay(ctx);
        }

        // ── Alt панель ──
        double altOp = altFade.getOutput();
        if (altOp > 0.003) {
            renderAltPanel(ctx, altOp);
        }

        profile.mouseMoved(mx, my, sw, sh);

        super.render(ctx, mx, my, delta);
    }

    private void updateParallax(int mx, int my) {
        float hW = sw / 2f, hH = sh / 2f;
        parTX = -(mx - hW) / hW * PAR_RANGE;
        parTY = -(my - hH) / hH * PAR_RANGE;
        parX += (parTX - parX) * PAR_LERP;
        parY += (parTY - parY) * PAR_LERP;
    }

    private void renderBackground(DrawContext ctx) {
        float m = PAR_RANGE + 4f;
        float bx = -m + parX, by = -m + parY;
        float bw = sw + m * 2f, bh = sh + m * 2f;
        bgGif.render(ctx.getMatrices(), bx, by, bw, bh);
        image.setTexture(getWallpaperTexture())
                .render(ShapeProperties.create(ctx.getMatrices(), bx, by, bw, bh)
                        .color(-1).build());
    }

    private void renderBlurOverlay(DrawContext ctx) {
        int a = (int) (ease(blur) * 135);
        if (a > 0) {
            ctx.fill(0, 0, sw, sh, clampByte(a) << 24);
        }
    }

    private void renderMainUI(DrawContext ctx, int mx, int my, double op, float dt) {
        int a = c255(op);
        if (a <= 0) return;

        // Slide сверху вниз (а не слева!)
        float slideDown = (1f - ease((float) op)) * 18f;

        renderHeader(ctx, a, slideDown);

        for (int i = 0, size = buttons.size(); i < size; i++) {
            buttons.get(i).render(ctx, mx, my, a, dt);
        }

        renderFooter(ctx, a);
        profile.render(ctx, sw, sh, a, op);
    }

    private void renderHeader(DrawContext ctx, int a, float slide) {
        // Slide идёт сверху вниз: при slide > 0 элементы выше (ближе к верху экрана)
        float iconY = cy + LOGO_OFFSET_Y - slide;
        float titleY = cy + TITLE_OFFSET_Y - slide;
        float subY = cy + SUB_OFFSET_Y - slide;

        // Иконка
        Fonts.getSize(60, Fonts.Type.ICONS)
                .drawCenteredString(ctx.getMatrices(), "", cx, iconY, applyA(C_ICON, a));

        // Свечение заголовка
        float glowPulse = 0.5f + 0.5f * (float) Math.sin(titleGlow);
        int glowAlpha = (int) (a * glowPulse * 0.15f);
        if (glowAlpha > 1) {
            Fonts.getSize(22, Fonts.Type.LUMIS_TITLE)
                    .drawCenteredString(ctx.getMatrices(), "Lumis Client",
                            cx, titleY - 0.5f, applyA(C_TITLE_GLOW, glowAlpha));
        }

        // Заголовок
        Fonts.getSize(23, Fonts.Type.LUMIS_TITLE)
                .drawCenteredString(ctx.getMatrices(), "Lumis Client",
                        cx, titleY, applyA(C_ACCENT, a));

        // Разделитель
        float sepY = titleY + 14f;
        float sepHalf = separatorWidth * ease(intro) / 2f;
        if (sepHalf > 0.5f) {
            int sepMid = applyA(C_SEPARATOR, a);
            int sepEdge = applyA(C_SEPARATOR, clampByte((int) (a * 0.2f)));
            rectangle.render(ShapeProperties.create(ctx.getMatrices(),
                            cx - sepHalf, sepY, sepHalf, 1f)
                    .round(1).color(sepEdge, sepMid, sepEdge, sepMid).build());
            rectangle.render(ShapeProperties.create(ctx.getMatrices(),
                            cx, sepY, sepHalf, 1f)
                    .round(1).color(sepMid, sepEdge, sepMid, sepEdge).build());
        }

        // Подзаголовок
        Fonts.getSize(11, Fonts.Type.DEFAULT)
                .drawCenteredString(ctx.getMatrices(), welcomeAnim.getCurrentText(),
                        cx, subY, applyA(C_MUTED, a));
    }

    private void renderFooter(DrawContext ctx, int a) {
        int c = applyA(C_SUBTLE, a);
        float footerY = sh - 7f;
        var font10 = Fonts.getSize(10, Fonts.Type.DEFAULT);
        font10.drawCenteredString(ctx.getMatrices(), "© 2026 Lumis. Все права защищены.", cx, footerY, c);

        String build = "Build ▸ Lumis 1.0 Beta";
        float bw = font10.getStringWidth(build);
        font10.drawString(ctx.getMatrices(), build, sw - bw - 4, footerY, c);
    }

    private void renderAltPanel(DrawContext ctx, double op) {
        float px = cx - 80, py = cy - 105;
        if (altPanel == null) {
            altPanel = new AltScreen(px, py);
        } else {
            altPanel.updatePosition(px, py);
        }

        int a = c255(op);
        float sc = 0.93f + 0.07f * ease((float) op);

        // Обновляем Color объекты только если значения изменились
        updateAltColor(0, packARGB(c255(op * 0.22), 50, 50, 50));
        updateAltColor(1, packARGB(c255(op * 0.37), 100, 100, 100));
        updateAltColor(2, packARGB(c255(op * 0.37), 80, 80, 80));
        updateAltColor(3, packARGB(a, 200, 200, 200));
        updateAltColor(4, packARGB(a, 30, 30, 30));

        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, cy, 0);
        ctx.getMatrices().scale(sc, sc, 1f);
        ctx.getMatrices().translate(-cx, -cy, 0);
        altPanel.render(ctx, altColors[0], altColors[1], altColors[2], altColors[3], altColors[4]);
        ctx.getMatrices().pop();
    }

    /**
     * Обновляет Color в кэше только если ARGB значение изменилось.
     * Избегаем создания нового Color объекта каждый кадр.
     */
    private void updateAltColor(int idx, int argb) {
        if (altColorCache[idx] != argb) {
            altColorCache[idx] = argb;
            altColors[idx] = new Color(
                    (argb >> 16) & 0xFF,
                    (argb >> 8) & 0xFF,
                    argb & 0xFF,
                    (argb >> 24) & 0xFF
            );
        }
    }

    private void toggleAlt() {
        long now = System.currentTimeMillis();
        if (now - lastToggle < TOGGLE_CD) return;
        lastToggle = now;
        altOpen = !altOpen;

        Direction altDir = altOpen ? Direction.FORWARDS : Direction.BACKWARDS;
        Direction mainDir = altOpen ? Direction.BACKWARDS : Direction.FORWARDS;

        altFade.setDirection(altDir);
        altFade.reset();
        mainFade.setDirection(mainDir);
        mainFade.reset();

        if (altPanel != null) altPanel.reset();
    }

    // ═══════════════════════════════════════════
    //  Ввод
    // ═══════════════════════════════════════════
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (!altOpen && mainFade.getOutput() > 0.5) {
            if (profile.mouseClicked(mx, my, btn, sw, sh)) {
                return true;
            }
        }

        if (altOpen && altFade.getOutput() > 0.5 && altPanel != null) {
            return altPanel.mouseClicked(mx, my, btn);
        }

        if (mainFade.getOutput() > 0.5 && btn == 0) {
            for (int i = 0, size = buttons.size(); i < size; i++) {
                Btn b = buttons.get(i);
                if (b.hit(mx, my)) {
                    b.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (altOpen && altFade.getOutput() > 0.5 && altPanel != null) {
            return altPanel.mouseScrolled(mx, my, v);
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (altOpen && altFade.getOutput() > 0.5 && altPanel != null) {
            return altPanel.mouseDragged(mx, my, b);
        }
        return super.mouseDragged(mx, my, b, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        if (altPanel != null) altPanel.mouseReleased();
        return super.mouseReleased(mx, my, b);
    }

    @Override
    public boolean charTyped(char c, int m) {
        if (altOpen && altFade.getOutput() > 0.5 && altPanel != null) {
            return altPanel.charTyped(c);
        }
        return super.charTyped(c, m);
    }



    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (k == GLFW.GLFW_KEY_ESCAPE && profile.isSettingsPanelOpen()) {
            profile.mouseClicked(-1, -1, 0, sw, sh);
            return true;
        }
        if (k == GLFW.GLFW_KEY_ESCAPE && altOpen) {
            toggleAlt();
            return true;
        }
        if (altOpen && altFade.getOutput() > 0.5 && altPanel != null && altPanel.keyPressed(k))
            return true;
        return super.keyPressed(k, s, m);
    }

    @Override
    public void close() {
        // Очищаем при закрытии экрана
        snow.reset();
        super.close();
    }

    // ═══════════════════════════════════════════
    //  Утилиты
    // ═══════════════════════════════════════════
    private static int applyA(int argb, int ext) {
        int srcA = (argb >>> 24) & 0xFF;
        int a = (srcA * ext + 127) / 255;
        return (clampByte(a) << 24) | (argb & 0x00FFFFFF);
    }

    private static int packARGB(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    private static int clampByte(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static int c255(double v) {
        return clampByte((int) (255.0 * v));
    }

    /**
     * Кубический ease-out: f(t) = 1 - (1-t)^3
     */
    private static float ease(float t) {
        if (t >= 1f) return 1f;
        if (t <= 0f) return 0f;
        float i = 1f - t;
        return 1f - i * i * i;
    }

    // ═══════════════════════════════════════════
    //  Кнопка (inner class)
    // ═══════════════════════════════════════════
    private class Btn {
        final String text, icon;
        final int row;
        final boolean small, right;
        final Runnable action;
        final float w, h;
        float x, y;
        float hover = 0f, press = 0f, glow = 0f;
        boolean wasHov = false;

        Btn(String t, int r, boolean sm, boolean ri, Runnable a) {
            text = t;
            row = r;
            small = sm;
            right = ri;
            action = a;
            w = sm ? BTN_HALF_W : BTN_WIDTH;
            h = BTN_HEIGHT;
            if (sm && !ri) icon = "i";       // выход
            else if (sm) icon = "s";          // настройки
            else icon = null;
        }

        void layout(float baseX, float baseY) {
            y = baseY + row * (BTN_HEIGHT + BTN_GAP);
            x = small ? (right ? baseX + BTN_HALF_W + BTN_GAP : baseX) : baseX;
        }

        boolean hit(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }

        void render(DrawContext ctx, int mx, int my, int ba, float dt) {
            if (ba <= 0) return;

            boolean hov = hit(mx, my);
            float hoverTarget = hov ? 1f : 0f;
            float hoverSpeed = hov ? 0.14f : 0.09f;
            hover += (hoverTarget - hover) * hoverSpeed;

            float glowTarget = hov ? 1f : 0f;
            float glowSpeed = hov ? 0.10f : 0.06f;
            glow += (glowTarget - glow) * glowSpeed;

            if (hov && !wasHov) press = 1f;
            press = Math.max(0f, press - 0.18f);
            wasHov = hov;

            float eh = ease(hover);
            float bounce = (float) Math.sin(press * Math.PI) * 0.8f;
            float bo = bounce * 0.4f;
            float rx = x - bo, ry = y - bo * 0.4f;
            float rw = w + bo * 2f, rh = h + bo * 0.8f;

            int oc = ColorAssist.lerp(eh, C_OUT_IDLE, C_OUT_HOVER);
            int fc = ColorAssist.lerp(eh, C_FILL_IDLE, C_FILL_HOVER);

            // Glow (только если заметный)
            if (glow > 0.02f) {
                int glowA = clampByte((int) (50 * glow));
                int gc = packARGB(glowA, 100, 120, 255);
                rectangle.render(ShapeProperties.create(ctx.getMatrices(),
                                x - 2.5f, y - 1.5f, w + 5f, h + 3f)
                        .round((int) BTN_ROUND + 3).color(applyA(gc, ba)).build());
            }

            // Фон + обводка
            int ao = applyA(oc, ba), af = applyA(fc, ba);
            rectangle.render(ShapeProperties.create(ctx.getMatrices(), rx, ry, rw, rh)
                    .thickness(hov ? 2 : 1).round((int) BTN_ROUND)
                    .outlineColor(ao).color(af, af, af, af).build());

            // Акцентная полоска снизу
            if (eh > 0.02f) {
                float lw = (rw - 24f) * eh;
                int lineAlpha = clampByte((int) (ba * eh * 0.6f));
                rectangle.render(ShapeProperties.create(ctx.getMatrices(),
                                rx + (rw - lw) / 2f, ry + rh - 2f, lw, 1.2f)
                        .round(1).color(applyA(C_ACCENT, lineAlpha)).build());
            }

            // Текст или иконка
            if (!text.isEmpty()) {
                int tc = ColorAssist.lerp(eh, C_TXT_IDLE, C_TXT_HOVER);
                float ty = ry + (rh - 9f) / 2f + 2f;
                Fonts.getSize(14, Fonts.Type.DEFAULT)
                        .drawCenteredString(ctx.getMatrices(), text, rx + rw / 2f, ty, applyA(tc, ba));
            } else if (icon != null) {
                int idleIcon = applyA(C_MUTED, clampByte((int) (ba * 0.6f)));
                int hoverIcon = applyA(C_TXT_HOVER, ba);
                int ic = ColorAssist.lerp(eh, idleIcon, hoverIcon);
                var font = Fonts.getSize(18, Fonts.Type.ICONSTYPENEW);
                float iconH = font.getStringHeight(icon);
                font.drawCenteredString(ctx.getMatrices(), icon,
                        rx + rw / 2f, ry + rh / 2f - iconH / 2f + 7.8f, ic);
            }
        }
    }
}