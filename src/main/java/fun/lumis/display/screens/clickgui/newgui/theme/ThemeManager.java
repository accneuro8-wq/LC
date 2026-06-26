package fun.lumis.display.screens.clickgui.newgui.theme;

import lombok.Getter;

@Getter
public class ThemeManager {
    private static ThemeManager instance;

    // Available presets, cycled by switchTheme(). Minced is the default look.
    private static final Theme[] PRESETS = {
            Theme.MINCED, Theme.CATLAVAN, Theme.DARK, Theme.LIGHT
    };

    private Theme currentTheme = Theme.MINCED;

    public static ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }

    public void switchTheme() {
        int idx = 0;
        for (int i = 0; i < PRESETS.length; i++) {
            if (PRESETS[i] == currentTheme) { idx = i; break; }
        }
        currentTheme = PRESETS[(idx + 1) % PRESETS.length];
    }

    public void setTheme(Theme theme) {
        this.currentTheme = theme;
    }

    public boolean isDark() {
        return currentTheme != Theme.LIGHT;
    }
}
