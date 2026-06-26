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
 * A single draggable, collapsible category panel for the Minced-style
 * dropdown ClickGui. The header shows the category name; clicking it toggles
 * the dropdown (show/hide the modules), and dragging it moves the panel.
 * Each module is rendered with the shared {@link AbstractMenuElement} so
 * toggles, keybinds and inline settings all keep working.
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

    public float x, y;
    private boolean extended = true;

    private boolean dragging = false;
    private boolean moved = false;
    private double dragOffsetX, dragOffsetY;

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

        // Collapse indicator (arrow icon from the MSDF icon atlas)
        String arrow = extended ? "9" : ";";
        float iconSize = 9f;
        MsdfFonts.drawIcon(matrix, arrow, x + WIDTH - PADDING - MsdfFonts.getIconWidth(arrow, iconSize),
                y + (HEADER_H - iconSize) / 2f, iconSize, textColor);

        if (!extended) return;

        // Module list
        float moduleY = y + HEADER_H + GAP;
        for (AbstractMenuElement element : modules) {
            element.render(ctx, mouseX, mouseY, x, moduleY, WIDTH, alpha, column);
            moduleY += element.getHeight() + GAP;
        }
    }

    private boolean inHeader(double mx, double my) {
        return mx >= x && mx <= x + WIDTH && my >= y && my <= y + HEADER_H;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (inHeader(mx, my)) {
            if (button == 0) {
                dragging = true;
                moved = false;
                dragOffsetX = mx - x;
                dragOffsetY = my - y;
                return true;
            }
            if (button == 1) {
                extended = !extended;
                return true;
            }
        }
        if (extended) {
            for (AbstractMenuElement element : modules) {
                element.onMouseClicked(mx, my, button);
            }
        }
        return false;
    }

    public void mouseReleased(double mx, double my, int button) {
        if (dragging && button == 0) {
            dragging = false;
            if (!moved && inHeader(mx, my)) {
                extended = !extended;
            }
        }
        if (extended) {
            for (AbstractMenuElement element : modules) {
                element.onMouseReleased(mx, my, button);
            }
        }
    }

    public void mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            x = (float) (mx - dragOffsetX);
            y = (float) (my - dragOffsetY);
            if (Math.abs(dx) > 1 || Math.abs(dy) > 1) moved = true;
            return;
        }
        if (extended) {
            for (AbstractMenuElement element : modules) {
                element.onMouseDragged(mx, my, button, dx, dy);
            }
        }
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!extended) return false;
        boolean handled = false;
        for (AbstractMenuElement element : modules) {
            if (element.keyPressed(keyCode, scanCode, modifiers)) handled = true;
        }
        return handled;
    }

    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!extended) return false;
        boolean handled = false;
        for (AbstractMenuElement element : modules) {
            if (element.mouseScrolled(mx, my, horizontal, vertical)) handled = true;
        }
        return handled;
    }
}
