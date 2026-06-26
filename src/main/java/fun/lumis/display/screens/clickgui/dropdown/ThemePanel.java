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
 * Right-side color palette for the dropdown ClickGui, bound to the global client
 * color ({@code Hud.colorSetting}). Picking a color recolors the HUD and every
 * module that uses the client accent.
 */
public class ThemePanel {
    public static final float WIDTH = 132f;
    public static final float HEADER_H = 28f;
    private static final float PADDING = 10f;
    private static final float SV_H = 96f;
    private static final float HUE_W = 9f;
    private static final float GAP = 5f;

    public final float x, baseY;

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
        return HEADER_H + 8f + SV_H + 10f;
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

        // Header
        MsdfFonts.drawSemibold(matrix, "Color", x + PADDING, y + HEADER_H / 2f - 4.5f, 9, white);
        blur.render(ShapeProperties.create(matrix, x + 6, y + HEADER_H - 1, WIDTH - 12, 0.75f)
                .round(0.5f).color(Theme.applyAlpha(theme.getForegroundStrokeInt(), alpha)).build());

        // ---- Color palette (bound to client/HUD color) ----
        float cy = y + HEADER_H + 8f;
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
