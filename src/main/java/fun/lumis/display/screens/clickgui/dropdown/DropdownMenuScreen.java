package fun.lumis.display.screens.clickgui.dropdown;

import fun.lumis.lumis;
import fun.lumis.display.screens.clickgui.newgui.animation.Animation;
import fun.lumis.display.screens.clickgui.newgui.animation.Easing;
import fun.lumis.display.screens.clickgui.newgui.elements.AbstractMenuElement;
import fun.lumis.display.screens.clickgui.newgui.elements.MenuModuleElement;
import fun.lumis.display.screens.clickgui.newgui.theme.Theme;
import fun.lumis.display.screens.clickgui.newgui.theme.ThemeManager;
import fun.lumis.display.screens.clickgui.newgui.utils.MsdfFonts;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.utils.client.sound.SoundManager;
import fun.lumis.utils.display.shape.ShapeProperties;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static fun.lumis.utils.display.interfaces.QuickImports.blur;

/**
 * Minced-style dropdown ClickGui: one FIXED panel per category, laid out in a
 * centered horizontal row, with a search bar at the bottom. Reuses
 * {@link MenuModuleElement} for module rendering, toggling, keybinds and
 * collapsible inline settings.
 */
public class DropdownMenuScreen extends Screen {

    public static final DropdownMenuScreen INSTANCE = new DropdownMenuScreen();

    private static final ModuleCategory[] CATEGORIES = {
            ModuleCategory.COMBAT,
            ModuleCategory.MOVEMENT,
            ModuleCategory.RENDER,
            ModuleCategory.PLAYER,
            ModuleCategory.MISC
    };

    private static final float PANEL_GAP = 12f;

    private final List<DropdownPanel> panels = new ArrayList<>();
    private final Animation animOpen = new Animation(280, 0, Easing.CUBIC_OUT);
    private boolean closing = false;
    private String searchText = "";

    public DropdownMenuScreen() {
        super(Text.literal("Dropdown ClickGui"));
    }

    private void build() {
        panels.clear();

        float totalWidth = CATEGORIES.length * DropdownPanel.WIDTH + (CATEGORIES.length - 1) * PANEL_GAP;
        float startX = (this.width - totalWidth) / 2f;
        float startY = (this.height - DropdownPanel.HEIGHT) / 2f - 10f;

        String filter = searchText.toLowerCase().trim();
        int column = 0;
        for (ModuleCategory category : CATEGORIES) {
            final ModuleCategory cat = category;
            List<AbstractMenuElement> mods = new ArrayList<>();
            lumis.getInstance().getModuleRepository().modules().stream()
                    .filter(mod -> mod.getCategory() == cat)
                    .filter(mod -> filter.isEmpty() || mod.getName().toLowerCase().contains(filter))
                    .forEach(mod -> mods.add(new MenuModuleElement(mod)));

            float px = startX + column * (DropdownPanel.WIDTH + PANEL_GAP);
            panels.add(new DropdownPanel(cat, mods, column, px, startY));
            column++;
        }
    }

    @Override
    protected void init() {
        build();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Keep the world crisp behind the GUI: no vanilla full-screen blur.
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        blur.setup();

        animOpen.update();
        float a = animOpen.getValue();
        if (closing && a <= 0.02f) {
            MinecraftClient.getInstance().setScreen(null);
            return;
        }
        float yOffset = (1f - a) * -18f;

        for (DropdownPanel panel : panels) {
            panel.render(context, mouseX, mouseY, a, yOffset);
        }

        // Search bar (bottom center)
        MatrixStack matrix = context.getMatrices();
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        float sw = 220f, sh = 24f;
        float sx = (this.width - sw) / 2f;
        float sy = this.height - sh - 28f;
        blur.render(ShapeProperties.create(matrix, sx, sy + yOffset, sw, sh).round(8).color(Theme.applyAlpha(theme.getForegroundColorInt(), a)).build());

        String shown = searchText.isEmpty() ? "Поиск..." : searchText;
        int color = Theme.applyAlpha(searchText.isEmpty() ? theme.getGrayLightInt() : theme.getColorInt(), a);
        MsdfFonts.drawText(matrix, shown, sx + 12f, sy + yOffset + 7f, 12, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (DropdownPanel panel : panels) {
            panel.mouseClicked(mouseX, mouseY, button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (DropdownPanel panel : panels) {
            panel.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        for (DropdownPanel panel : panels) {
            panel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Let module keybinding take priority
        boolean handled = false;
        for (DropdownPanel panel : panels) {
            if (panel.keyPressed(keyCode, scanCode, modifiers)) handled = true;
        }
        if (handled) return true;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            startClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!searchText.isEmpty()) {
                searchText = searchText.substring(0, searchText.length() - 1);
                build();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (chr >= ' ') {
            searchText += chr;
            build();
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (DropdownPanel panel : panels) {
            panel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        searchText = "";
        super.close();
    }

    private void startClose() {
        closing = true;
        animOpen.animateTo(0f);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    public void openGui() {
        searchText = "";
        closing = false;
        animOpen.setValue(0f);
        animOpen.animateTo(1f);
        MinecraftClient.getInstance().setScreen(this);
        SoundManager.playSound(SoundManager.OPEN_GUI);
    }
}