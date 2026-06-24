package fun.lumis.display.screens.mainmenu;

public class ClientSettings {

    private static final ClientSettings INSTANCE = new ClientSettings();

    private int selectedWallpaper = 1;
    private boolean snowEnabled = true;
    private SettingsListener listener;

    private ClientSettings() {}

    public static ClientSettings get() { return INSTANCE; }

    public int getSelectedWallpaper() { return selectedWallpaper; }

    public void setSelectedWallpaper(int w) {
        if (w < 1 || w > 8) return;
        selectedWallpaper = w;
        if (listener != null) listener.onWallpaperChanged(w);
    }

    public boolean isSnowEnabled() { return snowEnabled; }

    public void setSnowEnabled(boolean e) {
        snowEnabled = e;
        if (listener != null) listener.onSnowChanged(e);
    }

    public void toggleSnow() { setSnowEnabled(!snowEnabled); }

    public void setListener(SettingsListener l) { this.listener = l; }

    public interface SettingsListener {
        void onWallpaperChanged(int wallpaper);
        void onSnowChanged(boolean enabled);
    }
}