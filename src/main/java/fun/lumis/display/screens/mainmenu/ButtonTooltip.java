package fun.lumis.display.screens.mainmenu;

import fun.lumis.utils.display.font.Fonts;
import fun.lumis.utils.display.interfaces.QuickImports;
import fun.lumis.utils.display.shape.ShapeProperties;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public class ButtonTooltip implements QuickImports {

    private String text = "";
    private float targetX, targetY;
    private float currentX, currentY;
    private float opacity = 0f;
    private boolean visible = false;
    private int hoverFrames = 0;

    private static final float FADE_SPEED = 0.12f;
    private static final float MOVE_SMOOTH = 0.15f;
    private static final int DELAY_FRAMES = 15;

    public void show(String newText, float x, float y) {
        this.text = newText;
        this.targetX = x;
        this.targetY = y - 20f;
        this.visible = true;
    }

    public void hide() {
        this.visible = false;
        this.hoverFrames = 0;
    }

    public void update() {
        if (visible) {
            hoverFrames++;
            if (hoverFrames > DELAY_FRAMES) {
                opacity = Math.min(1f, opacity + FADE_SPEED);
            }
        } else {
            opacity = Math.max(0f, opacity - FADE_SPEED * 1.5f);
            hoverFrames = 0;
        }

        currentX += (targetX - currentX) * MOVE_SMOOTH;
        currentY += (targetY - currentY) * MOVE_SMOOTH;
    }

    public void render(DrawContext ctx) {
        if (opacity < 0.01f || text.isEmpty()) return;

        int alpha = (int) (255 * opacity);
        var font = Fonts.getSize(11, Fonts.Type.DEFAULT);
        float textW = font.getStringWidth(text);
        float padX = 8f;
        float padY = 5f;

        float bgX = currentX - textW / 2f - padX;
        float bgY = currentY - padY;
        float bgW = textW + padX * 2f;
        float bgH = 15f + padY;

        // Фон тултипа
        int bgAlpha = (int) (230 * opacity);
        int borderAlpha = (int) (160 * opacity);
        int bgColor = packColor(20, 22, 30, bgAlpha);
        int borderColor = packColor(100, 110, 200, borderAlpha);

        rectangle.render(ShapeProperties.create(ctx.getMatrices(),
                        bgX, bgY, bgW, bgH)
                .thickness(1)
                .round(5)
                .outlineColor(borderColor)
                .color(bgColor)
                .build());

        // Стрелка
        int arrowColor = packColor(20, 22, 30, bgAlpha);
        rectangle.render(ShapeProperties.create(ctx.getMatrices(),
                        currentX - 4f, bgY + bgH - 1f, 8f, 5f)
                .round(2)
                .color(arrowColor)
                .build());

        // Текст
        int textColor = packColor(225, 230, 255, alpha);
        font.drawCenteredString(ctx.getMatrices(), text,
                currentX, bgY + padY + 1f, textColor);
    }

    private static int packColor(int r, int g, int b, int a) {
        return ((Math.min(255, Math.max(0, a)) & 0xFF) << 24)
                | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}