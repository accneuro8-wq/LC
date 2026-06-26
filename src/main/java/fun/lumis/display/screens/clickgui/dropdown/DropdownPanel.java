package fun.lumis.display.screens.clickgui.dropdown;

import fun.lumis.display.screens.clickgui.newgui.elements.AbstractMenuElement;
import fun.lumis.display.screens.clickgui.newgui.elements.MenuModuleElement;
import fun.lumis.display.screens.clickgui.newgui.theme.Theme;
import fun.lumis.display.screens.clickgui.newgui.theme.ThemeManager;
import fun.lumis.display.screens.clickgui.newgui.utils.MsdfFonts;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.utils.display.font.Fonts;
import fun.lumis.utils.display.shape.ShapeProperties;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

import java.util.List;

import static fun.lumis.utils.display.interfaces.QuickImports.blur;

/**
 * One FIXED dark category card for the Minced-style dropdown ClickGui:
 * a single rounded panel with a header (icon + name) and a scrollable list of
 * flat module rows. Open/close offset comes from the screen; scrolling is
 * smoothly interpolated.
 */
public class DropdownPanel {
    public static final float WIDTH = 150f;
    public static final float HEADER_H = 28f;
    public static final int VISIBLE_ROWS = 11;
    public static final float LIST_H = VISIBLE_ROWS * MenuModuleElement.ROW_H;
    public static final float HEIGHT = HEADER_H + LIST_H + 4f;
    private static final float PADDING = 10f;

    private final ModuleCategory category;
    private final List<AbstractMenuElement> modules;
    private final int column;

    public final float x, baseY;
    private float scrollTarget = 0f;
    private float scroll = 0f;
    private float maxScroll = 0f;

    public DropdownPanel(ModuleCategory category, List<AbstractMenuElement> modules, int column, float x, float y) {
        this.category = category;
        this.modules = modules;
        this.column = column;
        this.x = x;
        this.baseY = y;
    }

    private String categoryIcon() {
        return switch (category) {
            case COMBAT -> "b";
            case MOVEMENT -> "c";
            case RENDER -> "d";
            case PLAYER -> "e";
            case MISC -> "f";
            default -> "F";
        };
    }

    public void render(DrawContext ctx, float mouseX, float mouseY, float alpha, float yOffset) {
        // Smooth scroll interpolation
        scroll += (scrollTarget - scroll) * 0.25f;
        if (Math.abs(scrollTarget - scroll) < 0.1f) scroll = scrollTarget;

        float y = baseY + yOffset;
        MatrixStack matrix = ctx.getMatrices();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // Single dark card
        int bg = Theme.applyAlpha(theme.getForegroundColorInt(), alpha);
        blur.render(ShapeProperties.create(matrix, x, y, WIDTH, HEIGHT).round(8).color(bg).build());

        // Header: icon + name, both vertically centered on the same midline
        int white = Theme.applyAlpha(theme.getWhiteInt(), alpha);
        float headerMid = y + HEADER_H / 2f;
        float iconSize = 10f;
        float nameSize = 9f;
        Fonts.getSize((int) iconSize, Fonts.Type.ICONSCATEGORY)
                .drawString(matrix, categoryIcon(), x + PADDING, headerMid - iconSize / 2f, white);
        float nameX = x + PADDING + iconSize + 7f;
        MsdfFonts.drawSemibold(matrix, category.getReadableName(), nameX, headerMid - nameSize / 2f, (int) nameSize, white);

        // Divider
        blur.render(ShapeProperties.create(matrix, x + 6, y + HEADER_H - 1, WIDTH - 12, 0.75f)
                .round(0.5f).color(Theme.applyAlpha(theme.getForegroundStrokeInt(), alpha)).build());

        // Scrollable list (clipped)
        float listTop = y + HEADER_H + 2f;
        float listBottom = y + HEIGHT - 2f;
        ctx.enableScissor((int) x, (int) listTop, (int) (x + WIDTH), (int) listBottom);

        float my = listTop - scroll;
        float total = 0f;
        for (AbstractMenuElement element : modules) {
            element.render(ctx, mouseX, mouseY, x, my, WIDTH, alpha, column);
            float h = element.getHeight() + 1f;
            my += h;
            total += h;
        }
        ctx.disableScissor();

        maxScroll = Math.max(0, total - (listBottom - listTop));
        if (scrollTarget > maxScroll) scrollTarget = maxScroll;

        // Scrollbar
        if (maxScroll > 0) {
            float trackH = listBottom - listTop;
            float thumbH = Math.max(18f, trackH * (trackH / total));
            float thumbY = listTop + (trackH - thumbH) * (scroll / maxScroll);
            blur.render(ShapeProperties.create(matrix, x + WIDTH - 3.5f, thumbY, 2f, thumbH)
                    .round(1f).color(Theme.applyAlpha(theme.getGrayInt(), alpha)).build());
        }
    }

    private boolean inList(double my) {
        return my >= baseY + HEADER_H && my <= baseY + HEIGHT;
    }

    private boolean inPanel(double mx, double my) {
        return mx >= x && mx <= x + WIDTH && my >= baseY && my <= baseY + HEIGHT;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!inList(my) || !inPanel(mx, my)) return false;
        for (AbstractMenuElement element : modules) {
            element.onMouseClicked(mx, my, button);
        }
        return true;
    }

    public void mouseReleased(double mx, double my, int button) {
        for (AbstractMenuElement element : modules) {
            element.onMouseReleased(mx, my, button);
        }
    }

    public void mouseDragged(double mx, double my, int button, double dx, double dy) {
        for (AbstractMenuElement element : modules) {
            element.onMouseDragged(mx, my, button, dx, dy);
        }
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = false;
        for (AbstractMenuElement element : modules) {
            if (element.keyPressed(keyCode, scanCode, modifiers)) handled = true;
        }
        return handled;
    }

    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!inPanel(mx, my)) return false;
        scrollTarget = (float) Math.max(0, Math.min(maxScroll, scrollTarget - vertical * 28));
        return true;
    }
}
