package fun.lumis.features.impl.render;

import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.lumis;
import fun.lumis.display.screens.clickgui.newgui.NewMenuScreen;
import org.lwjgl.glfw.GLFW;

public class Menu extends Module {
    public static final Menu INSTANCE = new Menu();

    public Menu() {
        super("Menu", ModuleCategory.RENDER);
        this.setKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    public void activate() {
        if (mc.world == null) return;

        // Modern Minced/Catlavan-styled ClickGui
        if (mc.currentScreen == NewMenuScreen.INSTANCE) return;
        NewMenuScreen.INSTANCE.openGui();
    }

    @Override
    public void deactivate() {
        if (mc.currentScreen instanceof NewMenuScreen) {
            mc.currentScreen.close();
        }
    }
}
