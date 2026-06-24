package fun.lumis.display.screens.clickgui;

import fun.lumis.features.impl.misc.SelfDestruct;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.common.animation.Easy.Direction;
import fun.lumis.common.animation.Easy.EaseBackIn;
import fun.lumis.utils.client.sound.SoundManager;
import fun.lumis.utils.display.interfaces.QuickImports;
import fun.lumis.display.screens.clickgui.components.AbstractComponent;
import fun.lumis.display.screens.clickgui.components.implement.panels.PanelsContainerComponent;
import fun.lumis.display.screens.clickgui.components.implement.other.SearchComponent;
import fun.lumis.display.screens.clickgui.components.implement.other.ThemeComponent;
import fun.lumis.display.screens.clickgui.components.implement.other.ClientColorPickerComponent;
import fun.lumis.display.screens.clickgui.components.implement.other.ConfigManagerComponent;
import fun.lumis.display.screens.clickgui.components.implement.settings.TextComponent;
import fun.lumis.utils.math.calc.Calculate;
import fun.lumis.utils.interactions.inv.InventoryFlowManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static fun.lumis.common.animation.Easy.Direction.BACKWARDS;
import static fun.lumis.common.animation.Easy.Direction.FORWARDS;

@Setter
@Getter
public class MenuScreen extends Screen implements QuickImports {
    public static MenuScreen INSTANCE = new MenuScreen();

    private static final int GUI_WIDTH = 400;
    private static final int GUI_HEIGHT = 280;
    private static final float PANELS_X_PADDING = 20f;

    private static final int OPEN_ANIMATION_MS = 325;
    private static final int CLOSE_ANIMATION_MS = 150;

    private static final float SEARCH_WIDTH = 80f;
    private static final float SEARCH_HEIGHT = 15f;
    private static final float SEARCH_Y_OFFSET = 19f;

    private static final float THEME_WIDTH = 140f;
    private static final float THEME_HEIGHT = 16f;
    private static final float THEME_Y_OFFSET = 1f;

    private static final float CONFIG_PANEL_WIDTH = 100f;
    private static final float CONFIG_PANEL_HEIGHT = 120f;
    private static final float CONFIG_PANEL_X_OFFSET = 10f;

    private final List<AbstractComponent> components = new ArrayList<>();
    private final SearchComponent searchComponent = new SearchComponent();
    private final ThemeComponent themeComponent = new ThemeComponent();
    private final ClientColorPickerComponent colorPickerComponent = new ClientColorPickerComponent();
    private final PanelsContainerComponent panelsContainerComponent = new PanelsContainerComponent();
    private final ConfigManagerComponent configManagerComponent = new ConfigManagerComponent();

    public final EaseBackIn animation = new EaseBackIn(OPEN_ANIMATION_MS, 1f, 1.5f);
    public ModuleCategory category = ModuleCategory.COMBAT;

    public int x, y, width, height;
    private double lastTransformedMouseX = 0;
    private double lastTransformedMouseY = 0;

    public void initialize() {
        animation.setDuration(OPEN_ANIMATION_MS);
        animation.setDirection(FORWARDS);
        components.clear();
        components.addAll(Arrays.asList(searchComponent, themeComponent, colorPickerComponent, panelsContainerComponent, configManagerComponent));
    }

    public MenuScreen() {
        super(Text.of("MenuScreen"));
        initialize();
    }

    @Override
    public void tick() {
        close();

        InventoryFlowManager.canMove = true;
        if (TextComponent.typing || SearchComponent.typing || ConfigManagerComponent.typing) {
            InventoryFlowManager.unPressMoveKeys();
        } else {
            InventoryFlowManager.updateMoveKeys();
        }

        components.forEach(AbstractComponent::tick);
        super.tick();
    }

    private double[] transformMouseCoords(double mouseX, double mouseY) {
        float scale = getScaleAnimation();
        if (scale <= 0.01f) scale = 1f;

        float centerX = x + width / 2f;
        float centerY = y + height / 2f;
        double transformedX = (mouseX - centerX) / scale + centerX;
        double transformedY = (mouseY - centerY) / scale + centerY;
        return new double[]{transformedX, transformedY};
    }

    private void layout(int windowWidth, int windowHeight) {
        width = GUI_WIDTH;
        height = GUI_HEIGHT;

        x = windowWidth / 2 - width / 2;
        y = windowHeight / 2 - height / 2;

        // Intentionally no full-screen dim/background rectangle behind panels.
        panelsContainerComponent.position(x - PANELS_X_PADDING, y).size(width + (PANELS_X_PADDING * 2f), height);

        searchComponent.size(SEARCH_WIDTH, SEARCH_HEIGHT);
        searchComponent.position(x + width / 2f - (SEARCH_WIDTH / 2f), y + height + SEARCH_Y_OFFSET);

        // Кружки тем над поиском
        themeComponent.size(THEME_WIDTH, THEME_HEIGHT);
        themeComponent.position(x + width / 2f - (THEME_WIDTH / 2f), y + height + THEME_Y_OFFSET);

        // ColorPicker в правом нижнем углу экрана
        colorPickerComponent.size(windowWidth, windowHeight);
        colorPickerComponent.position(0, 0);

        // Config Manager в левом нижнем углу экрана
        configManagerComponent.size(CONFIG_PANEL_WIDTH, CONFIG_PANEL_HEIGHT);
        configManagerComponent.position(CONFIG_PANEL_X_OFFSET, windowHeight - CONFIG_PANEL_HEIGHT - 10f);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        layout(window.getScaledWidth(), window.getScaledHeight());

        double[] transformed = transformMouseCoords(mouseX, mouseY);
        lastTransformedMouseX = transformed[0];
        lastTransformedMouseY = transformed[1];

        Calculate.scale(context.getMatrices(), x + (float) width / 2, y + (float) height / 2, getScaleAnimation(), () -> {
            // Рендерим все компоненты кроме colorpicker
            for (AbstractComponent component : components) {
                if (component != colorPickerComponent) {
                    component.render(context, (int)lastTransformedMouseX, (int)lastTransformedMouseY, delta);
                }
            }
            windowManager.render(context, (int)lastTransformedMouseX, (int)lastTransformedMouseY, delta);
        });
        
        // ColorPicker рендерится отдельно без scale
        colorPickerComponent.render(context, mouseX, mouseY, delta);
        
        super.render(context, mouseX, mouseY, delta);
    }

    public void openGui() {
        if (SelfDestruct.unhooked) return;

        animation.setDuration(OPEN_ANIMATION_MS);
        animation.setDirection(Direction.FORWARDS);
        animation.reset();
        mc.setScreen(this);
        SoundManager.playSound(SoundManager.OPEN_GUI);
    }

    public float getScaleAnimation() {
        return (float) animation.getOutput();
    }

    private double[] getTransformedMouseCoords(double mouseX, double mouseY) {
        return transformMouseCoords(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // ColorPicker обрабатывается без трансформации
        if (colorPickerComponent.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        
        double[] transformed = getTransformedMouseCoords(mouseX, mouseY);

        boolean windowHandled = windowManager.mouseClicked(transformed[0], transformed[1], button);
        if (!windowHandled) {
            for (AbstractComponent component : components) {
                if (component != colorPickerComponent) {
                    component.mouseClicked(transformed[0], transformed[1], button);
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // ColorPicker обрабатывается без трансформации
        colorPickerComponent.mouseReleased(mouseX, mouseY, button);
        
        double[] transformed = getTransformedMouseCoords(mouseX, mouseY);

        for (AbstractComponent component : components) {
            if (component != colorPickerComponent) {
                component.mouseReleased(transformed[0], transformed[1], button);
            }
        }
        windowManager.mouseReleased(transformed[0], transformed[1], button);

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        double[] transformed = getTransformedMouseCoords(mouseX, mouseY);

        boolean windowHandled = windowManager.mouseDragged(transformed[0], transformed[1], button, deltaX, deltaY);
        if (!windowHandled) {
            for (AbstractComponent component : components) {
                component.mouseDragged(transformed[0], transformed[1], button, deltaX, deltaY);
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        double[] transformed = getTransformedMouseCoords(mouseX, mouseY);

        boolean windowHandled = windowManager.mouseScrolled(transformed[0], transformed[1], vertical);
        if (!windowHandled) {
            for (AbstractComponent component : components) {
                component.mouseScrolled(transformed[0], transformed[1], vertical);
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = windowManager.keyPressed(keyCode, scanCode, modifiers);
        if (!handled) {
            for (AbstractComponent component : components) {
                if (component.keyPressed(keyCode, scanCode, modifiers)) {
                    handled = true;
                    break;
                }
            }
        }

        if (handled) {
            return true;
        }

        if (keyCode == 256 && shouldCloseOnEsc()) {
            SoundManager.playSound(SoundManager.CLOSE_GUI);
            animation.setDuration(CLOSE_ANIMATION_MS);
            animation.setDirection(BACKWARDS);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!windowManager.charTyped(chr, modifiers)) {
            for (AbstractComponent component : components) {
                component.charTyped(chr, modifiers);
            }
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (animation.finished(BACKWARDS)) {
            TextComponent.typing = false;
            SearchComponent.typing = false;
            ConfigManagerComponent.typing = false;
            animation.setDuration(OPEN_ANIMATION_MS);
            super.close();
        }
    }
}
