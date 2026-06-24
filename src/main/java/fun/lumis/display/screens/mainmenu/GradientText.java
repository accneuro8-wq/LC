package fun.lumis.display.screens.mainmenu;

import fun.lumis.utils.display.font.Fonts;
import net.minecraft.client.util.math.MatrixStack;

import java.awt.Color;

public class GradientText {

    /**
     * Двухцветный градиент с волновым эффектом по символам
     */
    public static void drawDualGradient(MatrixStack matrices, String text,
                                        float x, float y, int fontSize,
                                        Color colorA, Color colorB,
                                        int baseAlpha, float time) {
        var font = Fonts.getSize(fontSize, Fonts.Type.DEFAULT);
        float totalW = font.getStringWidth(text);
        float charX = x - totalW / 2f;
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            String charStr = String.valueOf(c);
            float charW = font.getStringWidth(charStr);

            float progress = len > 1 ? (float) i / (len - 1) : 0f;
            float shifted = (progress + time * 0.008f) % 1f;
            float t = (float) (Math.sin(shifted * Math.PI * 2) * 0.5 + 0.5);

            int r = (int) (colorA.getRed() + (colorB.getRed() - colorA.getRed()) * t);
            int g = (int) (colorA.getGreen() + (colorB.getGreen() - colorA.getGreen()) * t);
            int b = (int) (colorA.getBlue() + (colorB.getBlue() - colorA.getBlue()) * t);

            int finalColor = applyAlpha(packColor(r, g, b, 255), baseAlpha);

            float wave = (float) Math.sin(time * 0.04f + i * 0.25f) * 1.2f;

            font.drawString(matrices, charStr, charX, y + wave, finalColor);
            charX += charW;
        }
    }

    /**
     * Радужный градиент
     */
    public static void drawRainbow(MatrixStack matrices, String text,
                                   float x, float y, int fontSize,
                                   int baseAlpha, float time) {
        var font = Fonts.getSize(fontSize, Fonts.Type.DEFAULT);
        float charX = x - font.getStringWidth(text) / 2f;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String charStr = String.valueOf(c);
            float charW = font.getStringWidth(charStr);

            float hue = (time * 0.015f + i * 0.05f) % 1f;
            Color col = Color.getHSBColor(hue, 0.35f, 0.95f);

            float wave = (float) Math.sin(time * 0.05f + i * 0.35f) * 1.5f;
            int finalColor = applyAlpha(col.getRGB(), baseAlpha);

            font.drawString(matrices, charStr, charX, y + wave, finalColor);
            charX += charW;
        }
    }

    private static int applyAlpha(int rgb, int alpha) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int a = ((rgb >> 24) & 0xFF);
        int fa = (int) ((a / 255.0) * alpha);
        return ((Math.min(255, Math.max(0, fa)) & 0xFF) << 24) | (r << 16) | (g << 8) | b;
    }

    private static int packColor(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}