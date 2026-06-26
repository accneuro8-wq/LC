package fun.lumis.display.screens.clickgui.newgui.theme;

import lombok.Getter;
import lombok.Setter;
import java.awt.Color;

@Getter
@Setter
public class Theme {

    // ===== MINCED — signature near-black canvas with a violet accent gradient =====
    public static final Theme MINCED = new Theme("Minced",
            new Color(140, 120, 255),   // color (accent)
            new Color(205, 130, 255),   // secondColor (gradient end)
            new Color(14, 14, 17),      // backgroundColor
            new Color(20, 20, 25),      // foregroundColor (panels)
            new Color(30, 30, 38),      // foregroundLight (selected row)
            new Color(11, 11, 14),      // foregroundDark
            new Color(44, 44, 54),      // foregroundGray
            new Color(244, 244, 250),   // white
            new Color(150, 150, 162),   // whiteGray
            new Color(84, 84, 100),     // gray
            new Color(120, 120, 138),   // grayLight
            new Color(42, 42, 52),      // foregroundLightStroke
            new Color(28, 28, 36)       // foregroundStroke
    );

    // ===== CATLAVAN — deep blue-black with a cyan/teal accent =====
    public static final Theme CATLAVAN = new Theme("Catlavan",
            new Color(86, 178, 255),    // color (accent)
            new Color(120, 230, 220),   // secondColor (gradient end)
            new Color(12, 14, 18),      // backgroundColor
            new Color(18, 21, 27),      // foregroundColor (panels)
            new Color(27, 31, 40),      // foregroundLight (selected row)
            new Color(9, 11, 14),       // foregroundDark
            new Color(40, 46, 58),      // foregroundGray
            new Color(238, 244, 250),   // white
            new Color(148, 156, 168),   // whiteGray
            new Color(80, 88, 102),     // gray
            new Color(116, 126, 142),   // grayLight
            new Color(38, 44, 56),      // foregroundLightStroke
            new Color(26, 30, 40)       // foregroundStroke
    );

    public static final Theme DARK = new Theme("Dark",
            new Color(120, 140, 255),
            new Color(180, 140, 255),
            new Color(22, 22, 24),
            new Color(28, 28, 32),
            new Color(36, 36, 42),
            new Color(18, 18, 20),
            new Color(50, 50, 60),
            new Color(240, 240, 245),
            new Color(155, 155, 165),
            new Color(90, 90, 105),
            new Color(120, 120, 135),
            new Color(48, 48, 56),
            new Color(32, 32, 38)
    );

    public static final Theme LIGHT = new Theme("Light",
            new Color(138, 99, 255),
            new Color(99, 138, 255),
            new Color(240, 240, 245),
            new Color(255, 255, 255),
            new Color(245, 245, 250),
            new Color(235, 235, 240),
            new Color(220, 220, 225),
            new Color(30, 30, 40),
            new Color(80, 80, 90),
            new Color(160, 160, 170),
            new Color(120, 120, 130),
            new Color(200, 200, 210),
            new Color(220, 220, 230)
    );

    private final String name;
    private Color color;
    private Color secondColor;
    private Color backgroundColor;
    private Color foregroundColor;
    private Color foregroundLight;
    private Color foregroundDark;
    private Color foregroundGray;
    private Color white;
    private Color whiteGray;
    private Color gray;
    private Color grayLight;
    private Color foregroundLightStroke;
    private Color foregroundStroke;

    public Theme(String name, Color color, Color secondColor, Color backgroundColor,
                 Color foregroundColor, Color foregroundLight, Color foregroundDark,
                 Color foregroundGray, Color white, Color whiteGray, Color gray,
                 Color grayLight, Color foregroundLightStroke, Color foregroundStroke) {
        this.name = name;
        this.color = color;
        this.secondColor = secondColor;
        this.backgroundColor = backgroundColor;
        this.foregroundColor = foregroundColor;
        this.foregroundLight = foregroundLight;
        this.foregroundDark = foregroundDark;
        this.foregroundGray = foregroundGray;
        this.white = white;
        this.whiteGray = whiteGray;
        this.gray = gray;
        this.grayLight = grayLight;
        this.foregroundLightStroke = foregroundLightStroke;
        this.foregroundStroke = foregroundStroke;
    }

    public int getColorInt() { return color.getRGB(); }
    public int getSecondColorInt() { return secondColor.getRGB(); }
    public int getBackgroundColorInt() { return backgroundColor.getRGB(); }
    public int getForegroundColorInt() { return foregroundColor.getRGB(); }
    public int getForegroundLightInt() { return foregroundLight.getRGB(); }
    public int getForegroundDarkInt() { return foregroundDark.getRGB(); }
    public int getWhiteInt() { return white.getRGB(); }
    public int getWhiteGrayInt() { return whiteGray.getRGB(); }
    public int getGrayInt() { return gray.getRGB(); }
    public int getGrayLightInt() { return grayLight.getRGB(); }
    public int getForegroundStrokeInt() { return foregroundStroke.getRGB(); }
    public int getForegroundLightStrokeInt() { return foregroundLightStroke.getRGB(); }
    public int getForegroundGrayInt() { return foregroundGray.getRGB(); }

    public static int applyAlpha(int color, float alpha) {
        int a = (int) (255 * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    public static int mixColors(int color1, int color2, float ratio) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * ratio);
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
