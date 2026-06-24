package fun.lumis.display.screens.clickgui.components.implement.panels;

import fun.lumis.lumis;
import fun.lumis.display.screens.clickgui.components.AbstractComponent;
import fun.lumis.display.screens.clickgui.components.implement.module.PanelsModuleComponent;
import fun.lumis.display.screens.clickgui.MenuScreen;
import fun.lumis.features.impl.render.Hud;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.utils.display.color.ColorAssist;
import fun.lumis.utils.display.font.Fonts;
import fun.lumis.utils.display.scissor.ScissorAssist;
import fun.lumis.utils.display.shape.ShapeProperties;
import fun.lumis.utils.math.calc.Calculate;
import fun.lumis.common.animation.Animation;
import fun.lumis.common.animation.Direction;
import fun.lumis.common.animation.implement.Decelerate;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class CategoryPanelComponent extends AbstractComponent {
    private static final float HEADER_HEIGHT_RENDER = 18f;
    private static final float HEADER_HEIGHT_INPUT = 20f;

    private static final float CONTENT_PADDING_X = 6f;
    private static final float CONTENT_PADDING_TOP_RENDER = 4f;
    private static final float CONTENT_PADDING_TOP_INPUT = 5f;
    private static final float CONTENT_PADDING_BOTTOM_RENDER = 10f;
    private static final float CONTENT_PADDING_BOTTOM_INPUT = 11f;

    private static final float CONTENT_WIDTH_PADDING = 12f;

    private static final float TITLE_ICON_GAP = 4f;
    private static final float TITLE_CENTER_Y_DIV = 3f;
    private static final float TITLE_BASELINE_Y_OFFSET = 2.5f;

    private static final float LETTER_BLOCK_HEIGHT = 6f;
    private static final float MODULE_GAP_Y = 2f;

    private static final float SCROLL_STEP = 20f;
    private static final long SCROLLBAR_HIDE_DELAY_MS = 650L;

    private static final float SCROLLBAR_WIDTH = 3f;
    private static final float SCROLLBAR_X_OFFSET = 3.5f;
    private static final float SCROLLBAR_MIN_HANDLE_HEIGHT = 18f;

    private static final Map<ModuleCategory, String> CATEGORY_ICONS = Map.of(
            ModuleCategory.COMBAT, "b",
            ModuleCategory.MOVEMENT, "c",
            ModuleCategory.RENDER, "d",
            ModuleCategory.PLAYER, "e",
            ModuleCategory.MISC, "f",
            ModuleCategory.CONFIGS, "G",
            ModuleCategory.AUTOBUY, "H"
    );

    private final ModuleCategory category;
    private final List<PanelsModuleComponent> modules = new ArrayList<>();

    private float scroll = 0f;
    private float smoothedScroll = 0f;

    private final Animation scrollbarAnimation = new Decelerate().setMs(180).setValue(1);
    private long lastScrollMs = 0L;

    public CategoryPanelComponent(ModuleCategory category) {
        this.category = category;

        List<Module> all = lumis.getInstance().getModuleRepository().modules();
        for (Module m : all) {
            if (m.getCategory() == category) {
                modules.add(new PanelsModuleComponent(m));
            }
        }
        modules.sort(Comparator.comparing(o -> o.getModule().getVisibleName().toLowerCase()));
    }

    private String getCategoryIcon() {
        return CATEGORY_ICONS.getOrDefault(category, "");
    }

    private float getContentX() {
        return x + CONTENT_PADDING_X;
    }

    private float getContentYForRender() {
        return y + HEADER_HEIGHT_RENDER + CONTENT_PADDING_TOP_RENDER;
    }

    private float getContentYForInput() {
        return y + HEADER_HEIGHT_INPUT + CONTENT_PADDING_TOP_INPUT;
    }

    private float getContentWidth() {
        return width - CONTENT_WIDTH_PADDING;
    }

    private float getContentHeightForRender() {
        return height - HEADER_HEIGHT_RENDER - CONTENT_PADDING_BOTTOM_RENDER;
    }

    private float getContentHeightForInput() {
        return height - HEADER_HEIGHT_INPUT - CONTENT_PADDING_BOTTOM_INPUT;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrix = context.getMatrices();
        Matrix4f positionMatrix = matrix.peek().getPositionMatrix();

        blur.render(ShapeProperties.create(matrix, x, y, width, height).round(7.5f).quality(32)
                .softness(25)
                .color(new Color(11, 12, 18, 80).getRGB())
                .build());

        float headerH = HEADER_HEIGHT_RENDER;
        rectangle.render(ShapeProperties.create(matrix, x, y + headerH, width, 0.5f)
                .color(new Color(55, 55, 70, 180).getRGB(), new Color(55, 55, 70, 15).getRGB(), new Color(55, 55, 70, 180).getRGB(), new Color(55, 55, 70, 15).getRGB())
                .build());

        String title = category.getReadableName().toUpperCase();
        String icon = getCategoryIcon();
        float gap = icon.isEmpty() ? 0f : TITLE_ICON_GAP;
        float iconW = icon.isEmpty() ? 0f : Fonts.getSize(18, Fonts.Type.ICONSCATEGORY).getStringWidth(icon);
        float titleW = Fonts.getSize(14, Fonts.Type.DEFAULT).getStringWidth(title);
        float totalW = iconW + gap + titleW;
        float startX = x + (width - totalW) / 2f;
        float centerY = y + headerH / TITLE_CENTER_Y_DIV;
        int themeColor = Hud.getInstance().colorSetting.getColor();
        if (!icon.isEmpty()) {
            Fonts.getSize(18, Fonts.Type.ICONSCATEGORY).drawString(matrix, icon, startX, centerY + TITLE_BASELINE_Y_OFFSET, themeColor);
        }
        Fonts.getSize(14, Fonts.Type.DEFAULT).drawString(matrix, title, startX + iconW + gap, centerY + TITLE_BASELINE_Y_OFFSET, themeColor);

        float contentX = getContentX();
        float contentY = getContentYForRender();
        float contentW = getContentWidth();
        float contentH = getContentHeightForRender();

        ScissorAssist scissor = lumis.getInstance().getScissorManager();
        scissor.push(positionMatrix, contentX, contentY, contentW, contentH);

        String search = MenuScreen.INSTANCE.getSearchComponent().getText();
        boolean hasSearch = search != null && !search.isEmpty();
        String searchLower = hasSearch ? search.toLowerCase() : "";

        float renderScroll = Math.round(smoothedScroll);

        float yOff = 0f;
        float totalH = 0f;
        for (PanelsModuleComponent m : modules) {
            if (hasSearch && !m.getModule().getVisibleName().toLowerCase().contains(searchLower)) {
                continue;
            }

            float itemH = m.getComponentHeight();
            float itemY = contentY + yOff + renderScroll;
            m.position(contentX, itemY).size(contentW, itemH);
            m.render(context, mouseX, mouseY, delta);
            yOff += itemH + MODULE_GAP_Y;
            totalH += itemH + MODULE_GAP_Y;
        }

        scissor.pop();

        float maxScroll = Math.max(0f, (float) Math.ceil(totalH - contentH));
        scroll = MathHelper.clamp(scroll, -maxScroll, 0f);
        smoothedScroll = (float) Calculate.interpolateSmooth(2, smoothedScroll, scroll);

        if (maxScroll > 0f) {
            if (System.currentTimeMillis() - lastScrollMs > SCROLLBAR_HIDE_DELAY_MS) {
                scrollbarAnimation.setDirection(Direction.BACKWARDS);
            }

            float sbA = scrollbarAnimation.getOutput().floatValue();
            if (sbA <= 0.01f) {
                return;
            }

            float barW = SCROLLBAR_WIDTH;
            float barX = x + width - SCROLLBAR_X_OFFSET;
            float barY = contentY;
            float barH = contentH;

            int trackA = MathHelper.clamp((int) (100f * sbA), 0, 255);
            int handleA = MathHelper.clamp((int) (180f * sbA), 0, 255);

            rectangle.render(ShapeProperties.create(matrix, barX, barY, barW, barH)
                    .round(2)
                    .color(new Color(30, 30, 30, trackA).getRGB())
                    .build());

            float handleH = Math.max(SCROLLBAR_MIN_HANDLE_HEIGHT, barH * (barH / (barH + maxScroll)));
            float ratio = (-renderScroll) / maxScroll;
            float handleY = barY + (barH - handleH) * ratio;

            rectangle.render(ShapeProperties.create(matrix, barX, handleY, barW, handleH)
                    .round(2)
                    .color(new Color(100, 100, 100, handleA).getRGB())
                    .build());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float contentX = getContentX();
        float contentY = getContentYForInput();
        float contentW = getContentWidth();
        float contentH = getContentHeightForInput();

        if (!Calculate.isHovered(mouseX, mouseY, contentX, contentY, contentW, contentH)) {
            return false;
        }

        String search = MenuScreen.INSTANCE.getSearchComponent().getText();
        boolean hasSearch = search != null && !search.isEmpty();
        String searchLower = hasSearch ? search.toLowerCase() : "";

        for (PanelsModuleComponent m : modules) {
            if (hasSearch && !m.getModule().getVisibleName().toLowerCase().contains(searchLower)) {
                continue;
            }
            if (m.isHover(mouseX, mouseY)) {
                return m.mouseClicked(mouseX, mouseY, button);
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (PanelsModuleComponent m : modules) {
            m.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        float contentX = getContentX();
        float contentY = getContentYForInput();
        float contentW = getContentWidth();
        float contentH = getContentHeightForInput();

        if (!Calculate.isHovered(mouseX, mouseY, contentX, contentY, contentW, contentH)) {
            return false;
        }

        scroll += amount * SCROLL_STEP;
        lastScrollMs = System.currentTimeMillis();
        scrollbarAnimation.setDirection(Direction.FORWARDS);
        for (PanelsModuleComponent m : modules) {
            m.mouseScrolled(mouseX, mouseY, amount);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (PanelsModuleComponent m : modules) {
            if (m.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        for (PanelsModuleComponent m : modules) {
            m.charTyped(chr, modifiers);
        }
        return false;
    }
}
