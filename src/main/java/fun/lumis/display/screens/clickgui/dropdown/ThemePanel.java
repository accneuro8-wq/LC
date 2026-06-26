package fun.lumis.display.screens.clickgui.dropdown;

import fun.lumis.display.screens.clickgui.newgui.theme.Theme;
import fun.lumis.display.screens.clickgui.newgui.theme.ThemeManager;
import fun.lumis.display.screens.clickgui.newgui.utils.MsdfFonts;
import fun.lumis.utils.display.shape.ShapeProperties;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

import static fun.lumis.utils.display.interfaces.QuickImports.blur;

/**
 * Left-side theme picker for the dropdown ClickGui. Lists the available client
 * themes; clicking a row switches the active theme instantly.
 */
public class ThemePanel {
    public static final float WIDTH = 116f;
    public static final float HEADER_H = 28f;
    private static final float ROW_H = 22f;
    private static final float PADDING = 10f;

    private static final Theme[] THEMES = { Theme.MINCED, Theme.CATLAVAN, Theme.DARK, Theme.LIGHT };

    public final float x, baseY;
    private final float[] hover = new float[THEMES.length];

    public ThemePanel(float x, float y) {
        this.x = x;
        this.baseY = y;
    }

    public static float panelHeight() {
        return HEADER_H + THEMES.length * ROW_H + 6f;
    }

    public void render(DrawContext ctx, float mouseX, float mouseY, float alpha, float yOffset) {
        float y = baseY + yOffset;
        MatrixStack matrix = ctx.getMatrices();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        float height = panelHeight();

        // Card
        int bg = Theme.applyAlpha(theme.getForegroundColorInt(), alpha);
        blur.render(ShapeProperties.create(matrix, x, y, WIDTH, height).round(8).color(bg).build());

        // Header
        int white = Theme.applyAlpha(theme.getWhiteInt(), alpha);
        float headerMid = y + HEADER_H / 2f;
        MsdfFonts.drawSemibold(matrix, "Themes", x + PADDING, headerMid - 4.5f, 9, white);

        // Divider
        blur.render(ShapeProperties.create(matrix, x + 6, y + HEADER_H - 1, WIDTH - 12, 0.75f)
                .round(0.5f).color(Theme.applyAlpha(theme.getForegroundStrokeInt(), alpha)).build());

        // Rows
        float ry = y + HEADER_H + 2f;
        for (int i = 0; i < THEMES.length; i++) {
            Theme t = THEMES[i];
            boolean selected = theme == t;
            boolean hovered = mouseX >= x && mouseX <= x + WIDTH && mouseY >= ry && mouseY <= ry + ROW_H;

            hover[i] += ((hovered ? 1f : 0f) - hover[i]) * 0.2f;

            if (selected || hover[i] > 0.01f) {
                float h = selected ? 1f : hover[i] * 0.6f;
                int hl = Theme.applyAlpha(theme.getColorInt(), alpha * 0.16f * h);
                blur.render(ShapeProperties.create(matrix, x + 5, ry + 2, WIDTH - 10, ROW_H - 4)
                        .round(5).color(hl).build());
            }

            // accent swatch
            int accent = Theme.applyAlpha(t.getColorInt(), alpha);
            blur.render(ShapeProperties.create(matrix, x + PADDING, ry + (ROW_H - 9) / 2f, 9, 9)
                    .round(3).color(accent).build());

            // name
            int textCol = Theme.applyAlpha(selected ? theme.getColorInt() : theme.getWhiteInt(), alpha);
            MsdfFonts.drawText(matrix, t.getName(), x + PADDING + 9 + 7, ry + (ROW_H - 8) / 2f, 8, textCol);

            ry += ROW_H;
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        float height = panelHeight();
        if (mx < x || mx > x + WIDTH || my < baseY || my > baseY + height) return false;
        float ry = baseY + HEADER_H + 2f;
        for (Theme t : THEMES) {
            if (my >= ry && my <= ry + ROW_H) {
                ThemeManager.getInstance().setTheme(t);
                return true;
            }
            ry += ROW_H;
        }
        return true;
    }
}
