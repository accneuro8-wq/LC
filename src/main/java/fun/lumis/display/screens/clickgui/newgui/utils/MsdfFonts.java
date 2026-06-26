package fun.lumis.display.screens.clickgui.newgui.utils;

import com.google.common.base.Suppliers;
import fun.lumis.utils.display.atlasfont.msdf.MsdfFont;
import fun.lumis.utils.display.systemrender.builders.Builder;
import fun.lumis.utils.display.font.FontRenderer;
import fun.lumis.utils.display.font.Fonts;
import net.minecraft.client.util.math.MatrixStack;

import java.util.function.Supplier;

/**
 * Font facade for the ClickGui.
 *
 * Text is rendered with the client's main font ("our font", suisseintl via
 * {@link Fonts.Type#INST}) so the new ClickGui matches the rest of the UI.
 * Icons still use the MSDF icon atlas.
 *
 * All text is vertically centered on the line: callers pass {@code y} as the
 * top of a {@code size}-tall line box, and we draw the glyphs centered in it.
 */
public class MsdfFonts {

    public static final Supplier<MsdfFont> ICONS = Suppliers.memoize(() -> MsdfFont.builder()
            .atlas("icons")
            .data("icons")
            .build());

    // MSDF text at a given size renders visually larger than the native font at
    // the same pixel size, so scale up to match the original ClickGui proportions.
    private static final float SCALE = 1.4f;

    private static FontRenderer font(float size) {
        return Fonts.getSize(Math.max(1, Math.round(size * SCALE)), Fonts.Type.INST);
    }

    // ===== Icons (unchanged MSDF rendering) =====
    public static void drawIcon(MatrixStack matrix, String icon, float x, float y, float size, int color) {
        Builder.text()
                .font(ICONS.get())
                .text(icon)
                .size(size)
                .color(color)
                .build()
                .render(matrix.peek().getPositionMatrix(), x, y, 0);
    }

    public static float getIconWidth(String icon, float size) {
        return ICONS.get().getWidth(icon, size);
    }

    // ===== Text — delegated to the main client font, vertically centered =====
    public static void drawText(MatrixStack matrix, String text, float x, float y, float size, int color) {
        FontRenderer fr = font(size);
        // Use a STABLE reference height (not per-string) so every row aligns
        // identically. Center this line box inside the size-tall text box,
        // matching the old MSDF anchor (top=y, box height=size).
        float lineH = fr.getStringHeight("Ag");
        fr.drawString(matrix, text, x, y + (size - lineH) / 2f, color);
    }

    public static void drawRegular(MatrixStack matrix, String text, float x, float y, float size, int color) {
        drawText(matrix, text, x, y, size, color);
    }

    public static void drawSemibold(MatrixStack matrix, String text, float x, float y, float size, int color) {
        drawText(matrix, text, x, y, size, color);
    }

    public static float getTextWidth(String text, float size) {
        return font(size).getStringWidth(text);
    }

    public static float getRegularWidth(String text, float size) {
        return getTextWidth(text, size);
    }

    public static float getSemiboldWidth(String text, float size) {
        return getTextWidth(text, size);
    }
}