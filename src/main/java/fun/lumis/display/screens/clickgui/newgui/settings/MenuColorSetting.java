package fun.lumis.display.screens.clickgui.newgui.settings;

import fun.lumis.display.screens.clickgui.newgui.theme.Theme;
import fun.lumis.display.screens.clickgui.newgui.utils.MsdfFonts;
import fun.lumis.display.screens.clickgui.newgui.utils.Rect;
import fun.lumis.features.module.setting.implement.ColorSetting;
import fun.lumis.utils.display.shape.ShapeProperties;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

import java.awt.Color;

import static fun.lumis.utils.display.interfaces.QuickImports.blur;

@Getter
public class MenuColorSetting extends AbstractMenuSetting {
    private final ColorSetting setting;
    private Rect bounds;

    private static final float PALETTE_H = 52f;
    private static final float HUE_W = 9f;
    private static final float GAP = 5f;

    private boolean expanded = false;
    private boolean draggingSV = false;
    private boolean draggingHue = false;

    // live geometry (set every frame in render)
    private float svX, svY, svW, svH;
    private float hueX, hueY, hueH;

    public MenuColorSetting(ColorSetting setting) {
        this.setting = setting;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    @Override
    public void render(DrawContext ctx, float mouseX, float mouseY, float x, float settingY,
                       float moduleWidth, float alpha, float animEnable, int themeColor,
                       int textColor, int descriptionColor, Theme theme) {
        MatrixStack matrix = ctx.getMatrices();

        // ===== Label =====
        float fontHeight = 7f;
        float textY = settingY + (8 - fontHeight) / 2 - 0.5f;
        MsdfFonts.drawText(matrix, setting.getName(), x + 8 + 10, textY, 7f, textColor);

        // ===== Swatch (click target to expand) =====
        float toggleSize = 8f;
        float toggleX = x + moduleWidth - 8 - toggleSize;
        float toggleY = settingY + (8 - toggleSize) / 2f;
        int swatch = Theme.applyAlpha(setting.getColor(), alpha);
        blur.render(ShapeProperties.create(matrix, toggleX, toggleY, toggleSize, toggleSize)
                .round(3).color(swatch).build());
        bounds = new Rect(toggleX, toggleY, toggleSize, toggleSize);

        if (!expanded) return;

        // ===== Live drag updates (render-driven, like sliders) =====
        if (draggingSV) {
            setting.setSaturation(clamp01((mouseX - svX) / svW));
            setting.setBrightness(clamp01(1f - (mouseY - svY) / svH));
        }
        if (draggingHue) {
            setting.setHue(clamp01((mouseY - hueY) / hueH));
        }

        // ===== Palette geometry =====
        float pX = x + 18;
        float pY = settingY + 8 + 3;
        hueX = x + moduleWidth - 8 - HUE_W;
        hueY = pY;
        hueH = PALETTE_H;
        svX = pX;
        svY = pY;
        svW = (hueX - GAP) - pX;
        svH = PALETTE_H;

        float hue = setting.getHue();

        // ===== SV box (saturation x, brightness y) drawn as a small grid =====
        float step = 3f;
        for (float sx = 0; sx < svW; sx += step) {
            float sat = clamp01(sx / svW);
            float cw = Math.min(step, svW - sx);
            for (float sy = 0; sy < svH; sy += step) {
                float bri = clamp01(1f - sy / svH);
                float ch = Math.min(step, svH - sy);
                int col = Theme.applyAlpha(Color.HSBtoRGB(hue, sat, bri), alpha);
                blur.render(ShapeProperties.create(matrix, svX + sx, svY + sy, cw + 0.5f, ch + 0.5f)
                        .round(0).color(col).build());
            }
        }
        // SV handle
        float hx = svX + setting.getSaturation() * svW;
        float hy = svY + (1f - setting.getBrightness()) * svH;
        int handleCol = Theme.applyAlpha(0xFFFFFFFF, alpha);
        blur.render(ShapeProperties.create(matrix, hx - 2.5f, hy - 2.5f, 5f, 5f)
                .round(2.5f).color(handleCol).build());
        blur.render(ShapeProperties.create(matrix, hx - 1.5f, hy - 1.5f, 3f, 3f)
                .round(1.5f).color(Theme.applyAlpha(setting.getColor(), alpha)).build());

        // ===== Hue bar (vertical) =====
        for (float hyy = 0; hyy < hueH; hyy += 2f) {
            float h = clamp01(hyy / hueH);
            float ch = Math.min(2f, hueH - hyy);
            int col = Theme.applyAlpha(Color.HSBtoRGB(h, 1f, 1f), alpha);
            blur.render(ShapeProperties.create(matrix, hueX, hueY + hyy, HUE_W, ch + 0.5f)
                    .round(0).color(col).build());
        }
        // Hue handle
        float hueHandleY = hueY + hue * hueH;
        blur.render(ShapeProperties.create(matrix, hueX - 1f, hueHandleY - 1.5f, HUE_W + 2f, 3f)
                .round(1.5f).color(Theme.applyAlpha(0xFFFFFFFF, alpha)).build());
    }

    private boolean inRect(double mx, double my, float rx, float ry, float rw, float rh) {
        return mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh;
    }

    @Override
    public void onMouseClicked(double mouseX, double mouseY, int button) {
        if (bounds != null && bounds.contains(mouseX, mouseY)) {
            expanded = !expanded;
            return;
        }
        if (!expanded) return;

        if (inRect(mouseX, mouseY, svX, svY, svW, svH)) {
            draggingSV = true;
            setting.setSaturation(clamp01((float) ((mouseX - svX) / svW)));
            setting.setBrightness(clamp01((float) (1f - (mouseY - svY) / svH)));
        } else if (inRect(mouseX, mouseY, hueX, hueY, HUE_W, hueH)) {
            draggingHue = true;
            setting.setHue(clamp01((float) ((mouseY - hueY) / hueH)));
        }
    }

    @Override
    public void onMouseReleased(double mouseX, double mouseY, int button) {
        draggingSV = false;
        draggingHue = false;
    }

    @Override public float getWidth() { return 0; }
    @Override public float getHeight() { return expanded ? 8 + 3 + PALETTE_H + 3 : 8; }
    @Override public boolean isVisible() { return setting.isVisible(); }
}
