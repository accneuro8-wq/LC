package fun.lumis.display.screens.clickgui.newgui.theme;

import lombok.Getter;

@Getter
public class ThemeManager {
    private static ThemeManager instance;
    private Theme currentTheme = Theme.DARK; // Убедись, что тут DARK

    public static ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }

    public void switchTheme() {
        currentTheme = currentTheme == Theme.DARK ? Theme.LIGHT : Theme.DARK;
    }

    public void setTheme(Theme theme) {
        this.currentTheme = theme;
    }

    public boolean isDark() {
        return currentTheme == Theme.DARK;
    }
}
