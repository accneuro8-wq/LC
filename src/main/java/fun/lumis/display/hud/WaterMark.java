package fun.lumis.display.hud;

import fun.lumis.features.impl.render.Hud;
import fun.lumis.utils.client.managers.api.draggable.AbstractDraggable;
import fun.lumis.utils.display.font.Fonts;
import fun.lumis.utils.display.font.FontRenderer;
import fun.lumis.utils.display.shape.ShapeProperties;
import fun.lumis.utils.display.color.ColorAssist;
import fun.lumis.utils.display.geometry.Render2D;
import fun.lumis.utils.math.calc.Calculate;
import fun.lumis.utils.interactions.simulate.Simulations;
import net.minecraft.client.gui.DrawContext;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class WaterMark extends AbstractDraggable {

    public WaterMark() {
        super("Вотермарка", 10, 10, 200, 35, true);
    }

    @Override
    public boolean visible() {
        return Hud.getInstance().isState() && Hud.getInstance().interfaceSettings.isSelected("Вотермарка");
    }

    @Override
    public void drawDraggable(DrawContext context) {
        if (mc.player == null || mc.world == null) return;

        var matrix = context.getMatrices();
        FontRenderer font = Fonts.getSize(16, Fonts.Type.DEFAULT);
        FontRenderer iconFont = Fonts.getSize(18, Fonts.Type.ICONlumisREG);
        FontRenderer logoFont = Fonts.getSize(22, Fonts.Type.LOGO);

        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String fps = mc.getCurrentFps() + " Fps";
        String user = mc.getSession().getUsername();
        String bps = Calculate.round(Simulations.getSpeedSqrt(mc.player) * 20.0F, 1) + " Bps";
        String coords = mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ();

        float x = getX();
        float y = getY();
        float h = 15.0f;
        float gap = 6.0f;
        int accent = ColorAssist.getClientColor();

        float w1 = font.getStringWidth("Lumis") + 28;
        drawBeautifulPlate(context, x, y, w1, h, accent);
        logoFont.drawString(matrix, "V", x + 5.0f, y + 5.0f, accent);
        drawSeparator(context, x + 18.5f, y + 4.0f, 8.0f);
        font.drawString(matrix, "Lumis", x + 23.5f, y + 5.5f, -1);

        float userW = font.getStringWidth(user);
        float fpsW = font.getStringWidth(fps);
        float timeW = font.getStringWidth(time);
        float w2 = userW + fpsW + timeW + 56;
        float x2 = x + w1 + gap;
        drawBeautifulPlate(context, x2, y, w2, h, accent);

        iconFont.drawString(matrix, "W", x2 + 5.0f, y + 6.5f, accent);
        font.drawString(matrix, user, x2 + 18.0f, y + 5.5f, -1);
        drawSeparator(context, x2 + userW + 19.5f, y + 4.0f, 8.0f);

        iconFont.drawString(matrix, "X", x2 + userW + 23.5f, y + 6.5f, accent);
        font.drawString(matrix, fps, x2 + userW + 34.5f, y + 5.5f, -1);
        drawSeparator(context, x2 + userW + fpsW + 35.5f, y + 4.0f, 8.0f);

        iconFont.drawString(matrix, "V", x2 + userW + fpsW + 39.5f, y + 6.5f, accent);
        font.drawString(matrix, time, x2 + userW + fpsW + 50.5f, y + 5.5f, -1);

        float row2Y = y + 19.0f;

        float bpsW = font.getStringWidth(bps);
        float w3 = bpsW + 26;
        drawBeautifulPlate(context, x, row2Y, w3, h, accent);
        iconFont.drawString(matrix, "S", x + 5.0f, row2Y + 6.5f, accent);
        drawSeparator(context, x + 16.5f, row2Y + 4.0f, 8.0f);
        font.drawString(matrix, bps, x + 21.5f, row2Y + 5.5f, -1);

        float crdW = font.getStringWidth(coords);
        float w4 = crdW + 28;
        float x4 = x + w3 + gap;
        drawBeautifulPlate(context, x4, row2Y, w4, h, accent);
        iconFont.drawString(matrix, "F", x4 + 5.0f, row2Y + 6.5f, accent);
        drawSeparator(context, x4 + 17.5f, row2Y + 4.0f, 8.0f);
        font.drawString(matrix, coords, x4 + 22.5f, row2Y + 5.5f, -1);

        setWidth((int) (x2 + w2 - x));
        setHeight(40);
    }

    private void drawBeautifulPlate(DrawContext context, float x, float y, float w, float h, int color) {
        var ms = context.getMatrices();

        blur.render(ShapeProperties.create(ms, x, y, w, h)
                .round(4).softness(5.0f).color(ColorAssist.getRect(0.5f)).build());

        Render2D.rectangle.render(ShapeProperties.create(ms, x, y, w, h)
                .round(3).color(new Color(12, 12, 12, 210).getRGB()).build());

        Render2D.rectangle.render(ShapeProperties.create(ms, x - 0.8f, y - 0.8f, w + 1.6f, h + 1.6f)
                .round(4).thickness(2.2f).color(0).outlineColor(color).build());
    }

    private void drawSeparator(DrawContext context, float x, float y, float height) {
        Render2D.rectangle.render(ShapeProperties.create(context.getMatrices(), x, y, 0.8f, height)
                .color(new Color(255, 255, 255, 40).getRGB()).build());
    }
}