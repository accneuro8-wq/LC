package fun.lumis.display.screens.clickgui.dropdown;

import fun.lumis.display.screens.clickgui.newgui.elements.AbstractMenuElement;
import fun.lumis.display.screens.clickgui.newgui.theme.Theme;
import fun.lumis.display.screens.clickgui.newgui.theme.ThemeManager;
import fun.lumis.display.screens.clickgui.newgui.utils.MsdfFonts;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.utils.display.shape.ShapeProperties;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

import java.util.List;

import static fun.lumis.utils.display.interfaces.QuickImports.blur;

/**
 * A single FIXED category panel for the Minced-style dropdown ClickGui.
 * Panels cannot be moved or collapsed: the header just shows the category
 * name, and the module list is always visible. Modules are rendered with the
 * shared {@link AbstractMenuElement} so toggles, keybinds and inline settings
 * keep working.
 */
public class DropdownPanel {
    public static final float WIDTH = 132f;
    private static final float HEADER_H = 22f;
    private static final float PADDING = 8f;
    private static final float GAP = 6f;
    private static final float TEXT_SIZE = 9.5f;

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

    public void render(DrawContext ctx, float mouseX, float mouseY, float alpha) {
        MatrixStack matrix = ctx.getMatrices();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        // Header bar
        int headerColor = Theme.applyAlpha(theme.getColorInt(), alpha);
        blur.render(ShapeProperties.create(matrix, x, y, WIDTH, HEADER_H).round(6).color(headerColor).build());

        int textColor = Theme.applyAlpha(theme.getForegroundColorInt(), alpha);
        MsdfFonts.drawSemibold(matrix, category.getReadableName(), x + PADDING,
                y + (HEADER_H - TEXT_SIZE) / 2f, TEXT_SIZE, textColor);

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
