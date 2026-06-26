package fun.lumis.display.screens.clickgui.newgui.elements;

import fun.lumis.lumis;
import fun.lumis.display.screens.clickgui.newgui.animation.Animation;
import fun.lumis.display.screens.clickgui.newgui.animation.Easing;
import fun.lumis.display.screens.clickgui.newgui.settings.*;
import fun.lumis.display.screens.clickgui.newgui.theme.Theme;
import fun.lumis.display.screens.clickgui.newgui.theme.ThemeManager;
import fun.lumis.display.screens.clickgui.newgui.utils.Rect;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.Setting;
import fun.lumis.features.module.setting.implement.*;
import fun.lumis.display.screens.clickgui.newgui.utils.MsdfFonts;
import fun.lumis.display.screens.clickgui.dropdown.ThemePanel;
import fun.lumis.utils.display.color.ColorAssist;
import fun.lumis.utils.display.shape.ShapeProperties;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static fun.lumis.utils.display.interfaces.QuickImports.blur;

/**
 * Flat module row for the Minced-style dropdown: just the module name on the
 * left and a three-dot settings handle on the right. No per-row background box;
 * the whole row lives inside the panel's single dark card. Clicking the row
 * toggles the module; clicking the dots (or right-click) expands inline
 * settings.
 */
@Getter
public class MenuModuleElement extends AbstractMenuElement {
    public static final float ROW_H = 22f;

    private final Module module;
    private final List<AbstractMenuSetting> settings = new ArrayList<>();
    private final Animation animation;
    private final Animation expandAnim;
    private Rect bounds;
    private Rect boundsDots;
    private boolean binding = false;
    private boolean expanded = false;

    public MenuModuleElement(Module module) {
        this.module = module;
        animation = new Animation(200, module.isState() ? 1 : 0, Easing.LINEAR);
        expandAnim = new Animation(220, 0, Easing.QUARTIC_OUT);

        for (Setting setting : module.settings()) {
            if (setting instanceof SliderSettings slider) {
                settings.add(new MenuSliderSetting(slider));
            } else if (setting instanceof SelectSetting select) {
                settings.add(new MenuModeSetting(select));
            } else if (setting instanceof MultiSelectSetting multi) {
                settings.add(new MenuSelectSetting(multi));
            } else if (setting instanceof BooleanSetting bool) {
                settings.add(new MenuBooleanSetting(bool));
            } else if (setting instanceof ColorSetting color) {
                settings.add(new MenuColorSetting(color));
            } else if (setting instanceof BindSetting bind) {
                settings.add(new MenuKeySetting(bind));
            } else if (setting instanceof ButtonSetting button) {
                settings.add(new MenuButtonSetting(button));
            }
        }
    }

    @Override
    public void render(DrawContext ctx, float mouseX, float mouseY, float x, float y, float moduleWidth, float alpha, int column) {
        MatrixStack matrix = ctx.getMatrices();

        animation.animateTo(module.isState() ? 1 : 0);
        animation.update();
        expandAnim.animateTo(expanded && hasSettings() ? 1 : 0);
        expandAnim.update();

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        boolean hasSettings = hasSettings();
        boolean showSettings = expanded && hasSettings;

        bounds = new Rect(x, y, moduleWidth, ROW_H);
        boolean hovered = bounds.contains(mouseX, mouseY);

        // Subtle highlight on hover / when expanded
        if (hovered || showSettings) {
            blur.render(ShapeProperties.create(matrix, x + 3, y, moduleWidth - 6, ROW_H)
                    .round(5).color(Theme.applyAlpha(theme.getForegroundLightInt(), alpha)).build());
        }

        // Name: enabled -> accent color, disabled -> muted gray
        int accentInt = ThemePanel.colorText ? ColorAssist.getClientColor() : theme.getColorInt();
        int nameColor = Theme.mixColors(theme.getWhiteGrayInt(), accentInt, animation.getValue());
        nameColor = Theme.applyAlpha(nameColor, alpha);
        MsdfFonts.drawText(matrix, module.getVisibleName(), x + 12, y + (ROW_H - 9) / 2f, 9, nameColor);

        // Three-dot settings handle on the right
        float dotsZoneW = 16f;
        float dotsX = x + moduleWidth - dotsZoneW;
        boundsDots = new Rect(dotsX, y, dotsZoneW, ROW_H);
        if (hasSettings) {
            int dotColor = Theme.applyAlpha(expanded ? theme.getColorInt() : theme.getGrayLightInt(), alpha);
            float dot = 1.5f;
            float cx = dotsX + dotsZoneW / 2f - dot / 2f;
            float cy = y + ROW_H / 2f;
            for (int i = -1; i <= 1; i++) {
                blur.render(ShapeProperties.create(matrix, cx, cy + i * 3.6f - dot / 2f, dot, dot)
                        .round(dot / 2f).color(dotColor).build());
            }
        }

        float expand = expandAnim.getValue();
        if (expand <= 0.001f || !hasSettings) return;

        // Inline settings (animated open/close, clipped to the animated height)
        int enabledColor = Theme.applyAlpha(Theme.mixColors(theme.getGrayInt(), theme.getColorInt(), animation.getValue()), alpha);
        int textColor = Theme.applyAlpha(Theme.mixColors(theme.getGrayLightInt(), theme.getWhiteInt(), animation.getValue()), alpha);
        int descriptionColor = Theme.applyAlpha(Theme.mixColors(theme.getWhiteGrayInt(), theme.getGrayLightInt(), animation.getValue()), alpha);

        float settingsAlpha = alpha * expand;
        float fullH = settingsHeight();
        float animH = fullH * expand;
        float top = y + ROW_H;

        ctx.enableScissor((int) x, (int) top, (int) (x + moduleWidth), (int) Math.ceil(top + animH));
        float padding = 6;
        // slide content up slightly while collapsing
        float startY = top + padding - (1f - expand) * 8f;
        for (AbstractMenuSetting setting : settings) {
            if (!setting.isVisible()) continue;
            setting.render(ctx, mouseX, mouseY, x, startY, moduleWidth, settingsAlpha, animation.getValue(), enabledColor, textColor, descriptionColor, theme);
            startY += setting.getHeight() + 8;
        }
        ctx.disableScissor();
    }

    private float settingsHeight() {
        return (float) settings.stream()
                .filter(AbstractMenuSetting::isVisible)
                .mapToDouble(m -> m.getHeight() + 8)
                .sum() + 8;
    }

    @Override
    public float getHeight() {
        if (!hasSettings()) return ROW_H;
        return ROW_H + settingsHeight() * expandAnim.getValue();
    }

    public boolean hasSettings() {
        return !settings.isEmpty() && settings.stream().anyMatch(AbstractMenuSetting::isVisible);
    }

    @Override
    public void onMouseClicked(double mouseX, double mouseY, int button) {
        if (bounds != null && bounds.contains(mouseX, mouseY)) {
            if (button > 2 && binding) {
                binding = false;
                module.setKey(button);
                return;
            }
            if (button == 0) {
                if (hasSettings() && boundsDots != null && boundsDots.contains(mouseX, mouseY)) {
                    expanded = !expanded;
                } else {
                    module.switchState();
                }
            } else if (button == 1) {
                if (hasSettings()) expanded = !expanded;
            } else if (button == 2) {
                binding = !binding;
            }
        }

        if (expanded) {
            for (AbstractMenuSetting setting : settings) {
                setting.onMouseClicked(mouseX, mouseY, button);
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (binding) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                module.setKey(-1);
            } else {
                module.setKey(keyCode);
            }
            binding = false;
            return true;
        }

        boolean result = false;
        if (expanded) {
            for (AbstractMenuSetting setting : settings) {
                if (setting.keyPressed(keyCode, scanCode, modifiers)) {
                    result = true;
                }
            }
        }
        return result;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return false;
    }

    @Override
    public ModuleCategory getCategory() {
        return module.getCategory();
    }

    @Override
    public String getName() {
        return module.getName();
    }

    @Override
    public void onMouseReleased(double mouseX, double mouseY, int button) {
        if (expanded) {
            for (AbstractMenuSetting setting : settings) {
                setting.onMouseReleased(mouseX, mouseY, button);
            }
        }
    }

    @Override
    public void onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
    }
}