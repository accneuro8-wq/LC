package fun.lumis.display.screens.clickgui.components.implement.panels;

import fun.lumis.display.screens.clickgui.components.AbstractComponent;
import fun.lumis.features.module.ModuleCategory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class PanelsContainerComponent extends AbstractComponent {
    private static final float PANEL_WIDTH = 126f;
    private static final float PANEL_HEIGHT = 265f;
    private static final float PANEL_GAP = 6f;
    private static final float TOP_PADDING = 10f;

    private final List<CategoryPanelComponent> panels = new ArrayList<>();

    public PanelsContainerComponent() {
        for (ModuleCategory category : EnumSet.of(ModuleCategory.COMBAT, ModuleCategory.MOVEMENT, ModuleCategory.RENDER, ModuleCategory.PLAYER, ModuleCategory.MISC)) {
            panels.add(new CategoryPanelComponent(category));
        }
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        float totalWidth = panels.size() * PANEL_WIDTH + (panels.size() - 1) * PANEL_GAP;
        float startX = x + (width - totalWidth) / 2f;
        float startY = y + TOP_PADDING;

        for (int i = 0; i < panels.size(); i++) {
            CategoryPanelComponent panel = panels.get(i);
            panel.position(startX + i * (PANEL_WIDTH + PANEL_GAP), startY).size(PANEL_WIDTH, PANEL_HEIGHT);
            panel.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (CategoryPanelComponent panel : panels) {
            if (panel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (CategoryPanelComponent panel : panels) {
            if (panel.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        for (CategoryPanelComponent panel : panels) {
            if (panel.mouseScrolled(mouseX, mouseY, amount)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (CategoryPanelComponent panel : panels) {
            if (panel.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        for (CategoryPanelComponent panel : panels) {
            if (panel.charTyped(chr, modifiers)) {
                return true;
            }
        }
        return false;
    }
}
