package fun.lumis.display.screens.clickgui.dropdown;

import fun.lumis.features.impl.render.Hud;
import fun.lumis.features.module.setting.implement.ColorSetting;
import fun.lumis.display.screens.clickgui.newgui.theme.Theme;
import fun.lumis.display.screens.clickgui.newgui.theme.ThemeManager;
import fun.lumis.display.screens.clickgui.newgui.utils.MsdfFonts;
import fun.lumis.utils.display.shape.ShapeProperties;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

import java.awt.Color;

import static fun.lumis.utils.display.interfaces.QuickImports.blur;

/**
 * Right-side customization panel for the dropdown ClickGui:
 *  - theme presets (Minced / Catlavan / Dark / Light)
 *  - a live color palette bound to the global client color
 *    ({@code Hud.colorSetting}), so picking a color recolors the HUD and every
 *    module that uses the client accent.
 */
public class ThemePanel {
    public static final float WIDTH = 132f;
    public static final float HEADER_H = 28f;
    private static final float ROW_H = 19f;
    private static final float PADDING = 10f;
    private static final float SV_H = 66f;
    private static final float HUE_W = 9f;
    private static final float GAP = 5f;

    private static final Theme[] THEMES = { Theme.MINCED, Theme.CATLAVAN, Theme.DARK, Theme.LIGHT };

    public final float x, baseY;
    private final float[] hover = new float[THEMES.length];

    // palette geometry (set each frame)
    private float svX, svY, svW, svH, hueX, hueY, hueH;
    private boolean draggingSV = false;
    private boolean draggingHue = false;

    public ThemePanel(float x, float y) {
        this.x = x;
        this.baseY = y;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private ColorSetting clientColor() {
        return Hud.getInstance().colorSetting;
    }

    public static float panelHeight() {
        // header + theme label + theme rows + divider + color label + palette
        return HEADER_H + 12f + THEMES.length * ROW_H + 10f + 12f + SV_H + 10f;
    }

    public void render(DrawContext ctx, float mouseX, float mouseY, float alpha, float yOffset) {
        float y = baseY + yOffset;
        MatrixStack matrix = ctx.getMatrices();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        ColorSetting cs = clientColor();
        float height = panelHeight();

        // Live drag updates (render-driven)
        if (draggingSV) {
            cs.setSaturation(clamp01((mouseX - svX) / svW));
            cs.setBrightness(clamp01(1f - (mouseY - svY) / svH));
        }
        if (draggingHue) {
            cs.setHue(clamp01((mouseY - hueY) / hueH));
        }

        // Card
        int bg = Theme.applyAlpha(theme.getForegroundColorInt(), alpha);
        blur.render(ShapeProperties.create(matrix, x, y, WIDTH, height).round(8).color(bg).build());

        int white = Theme.applyAlpha(theme.getWhiteInt(), alpha);
        int gray = Theme.applyAlpha(theme.getGrayLightInt(), alpha);

        // Header
        MsdfFonts.drawSemibold(matrix, "Customize", x + PADDING, y + HEADER_H / 2f - 4.5f, 9, white);
        blur.render(ShapeProperties.create(matrix, x + 6, y + HEADER_H - 1, WIDTH - 12, 0.75f)
                .round(0.5f).color(Theme.applyAlpha(theme.getForegroundStrokeInt(), alpha)).build());

        // ---- Theme presets ----
        float cy = y + HEADER_H + 6f;
        MsdfFonts.drawText(matrix, "Theme", x + PADDING, cy, 7, gray);
        cy += 12f;

        for (int i = 0; i < THEMES.length; i++) {
            Theme t = THEMES[i];
            boolean selected = theme == t;
            boolean hovered = mouseX >= x && mouseX <= x + WIDTH && mouseY >= cy && mouseY <= cy + ROW_H;
            hover[i] += ((hovered ? 1f : 0f) - hover[i]) * 0.2f;

            if (selected || hover[i] > 0.01f) {
                float h = selected ? 1f : hover[i] * 0.6f;
                int hl = Theme.applyAlpha(theme.getColorInt(), alpha * 0.16f * h);
                blur.render(ShapeProperties.create(matrix, x + 5, cy + 1, WIDTH - 10, ROW_H - 2)
                        .round(5).color(hl).build());
            }

            int accent = Theme.applyAlpha(t.getColorInt(), alpha);
            blur.render(ShapeProperties.create(matrix, x + PADDING, cy + (ROW_H - 9) / 2f, 9, 9)
                    .round(3).color(accent).build());

            int textCol = Theme.applyAlpha(selected ? theme.getColorInt() : theme.getWhiteInt(), alpha);
            MsdfFonts.drawText(matrix, t.getName(), x + PADDING + 9 + 7, cy + (ROW_H - 7) / 2f, 7, textCol);
            cy += ROW_H;
        }

        // ---- Divider ----
        cy += 4f;
        blur.render(ShapeProperties.create(matrix, x + 6, cy, WIDTH - 12, 0.75f)
                .round(0.5f).color(Theme.applyAlpha(theme.getForegroundStrokeInt(), alpha)).build());
        cy += 8f;

        // ---- Color palette (bound to client/HUD color) ----
        MsdfFonts.drawText(matrix, "Client Color", x + PADDING, cy, 7, gray);
        cy += 11f;

        svX = x + PADDING;
        svY = cy;
        svH = SV_H;
        hueX = x + WIDTH - PADDING - HUE_W;
        hueY = cy;
        hueH = SV_H;
        svW = (hueX - GAP) - svX;

        float hue = cs.getHue();

        // SV box grid
        float step = 3f;
        for (float sx = 0; sx < svW; sx += step) {
            float sat = clamp01(sx / svW);
            float cw = Math.min(step, svW - sx);
            for (float sy = 0; sy < svH; sy += step) {
                float bri = clamp01(1f - sy / svH);
                float ch = Math.min(step, svH - sy);
                int col = Theme.applyAlpha(Color.HSBtoRGB(hue, sat, bri), alpha);
                blur.render(ShapeProperties.create(matrix, svX + sx, svY + sy, cw + 0.6f, ch + 0.6f)
                        .round(0).color(col).build());
            }
        }
        // SV handle
        float hx = svX + cs.getSaturation() * svW;
        float hy = svY + (1f - cs.getBrightness()) * svH;
        blur.render(ShapeProperties.create(matrix, hx - 2.5f, hy - 2.5f, 5f, 5f)
                .round(2.5f).color(Theme.applyAlpha(0xFFFFFFFF, alpha)).build());
        blur.render(ShapeProperties.create(matrix, hx - 1.5f, hy - 1.5f, 3f, 3f)
                .round(1.5f).color(Theme.applyAlpha(cs.getColor(), alpha)).build());

        // Hue bar
        for (float hyy = 0; hyy < hueH; hyy += 2f) {
            float h = clamp01(hyy / hueH);
            float ch = Math.min(2f, hueH - hyy);
            int col = Theme.applyAlpha(Color.HSBtoRGB(h, 1f, 1f), alpha);
            blur.render(ShapeProperties.create(matrix, hueX, hueY + hyy, HUE_W, ch + 0.6f)
                    .round(0).color(col).build());
        }
        float hueHandleY = hueY + hue * hueH;
        blur.render(ShapeProperties.create(matrix, hueX - 1f, hueHandleY - 1.5f, HUE_W + 2f, 3f)
                .round(1.5f).color(Theme.applyAlpha(0xFFFFFFFF, alpha)).build());
    }

    private boolean inRect(double mx, double my, float rx, float ry, float rw, float rh) {
        return mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        float height = panelHeight();
        if (mx < x || mx > x + WIDTH || my < baseY || my > baseY + height) return false;

        // theme rows
        float cy = baseY + HEADER_H + 6f + 12f;
        for (Theme t : THEMES) {
            if (my >= cy && my <= cy + ROW_H) {
                ThemeManager.getInstance().setTheme(t);
                return true;
            }
            cy += ROW_H;
        }

        // palette
        ColorSetting cs = clientColor();
        if (inRect(mx, my, svX, svY, svW, svH)) {
            draggingSV = true;
            cs.setSaturation(clamp01((float) ((mx - svX) / svW)));
            cs.setBrightness(clamp01((float) (1f - (my - svY) / svH)));
            return true;
        }
        if (inRect(mx, my, hueX, hueY, HUE_W, hueH)) {
            draggingHue = true;
            cs.setHue(clamp01((float) ((my - hueY) / hueH)));
            return true;
        }
        return true; // consume clicks inside the panel
    }

    public void mouseReleased(double mx, double my, int button) {
        draggingSV = false;
        draggingHue = false;
    }
}
