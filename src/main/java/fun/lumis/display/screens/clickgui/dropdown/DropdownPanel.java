package fun.lumis.display.screens.clickgui.dropdown;

import fun.lumis.display.screens.clickgui.newgui.elements.AbstractMenuElement;
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
 * A single FIXED category panel for the Minced-style dropdown ClickGui.
 * Panels cannot be moved or collapsed: header shows the category icon + name,
 * and the module list is always visible. Modules are rendered with the shared
 * {@link AbstractMenuElement} so toggles, keybinds and inline settings work.
 */
public class DropdownPanel {
    public static final float WIDTH = 150f;
    public static final float HEADER_H = 26f;
    private static final float PADDING = 10f;
    private static final float GAP = 5f;

    private final ModuleCategory category;
    private final List<AbstractMenuElement> modules;
    private final int column;

    public final float x, y;

    public DropdownPanel(ModuleCategory category, List<AbstractMenuElement> modules, int column, float x, float y) {
        this.category = category;
        this.modules = modules;
        this.column = column;
        this.x = x;
        this.y = y;
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

    public void render(DrawContext ctx, float mouseX, float mouseY, float alpha) {
        MatrixStack matrix = ctx.getMatrices();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // Header bar
        int headerColor = Theme.applyAlpha(theme.getForegroundColorInt(), alpha);
        blur.render(ShapeProperties.create(matrix, x, y, WIDTH, HEADER_H).round(8).color(headerColor).build());

        int textColor = Theme.applyAlpha(theme.getColorInt(), alpha);

        // Category icon + name
        float iconSize = 14f;
        float iconY = y + (HEADER_H - iconSize) / 2f + 1f;
        Fonts.getSize((int) iconSize, Fonts.Type.ICONSCATEGORY)
                .drawString(matrix, categoryIcon(), x + PADDING, iconY, textColor);

        float nameX = x + PADDING + iconSize + 6f;
        MsdfFonts.drawSemibold(matrix, category.getReadableName(), nameX, y, 10f, textColor);

        // Module list (always shown)
        float moduleY = y + HEADER_H + GAP;
        for (AbstractMenuElement element : modules) {
            element.render(ctx, mouseX, mouseY, x, moduleY, WIDTH, alpha, column);
            moduleY += element.getHeight() + GAP;
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        for (AbstractMenuElement element : modules) {
            element.onMouseClicked(mx, my, button);
        }
        return false;
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
        boolean handled = false;
        for (AbstractMenuElement element : modules) {
            if (element.mouseScrolled(mx, my, horizontal, vertical)) handled = true;
        }
        return handled;
    }
}
