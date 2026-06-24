package fun.lumis.display.screens.clickgui.components.implement.autobuy.items;

import fun.lumis.display.screens.clickgui.components.implement.autobuy.settings.AutoBuyItemSettings;
import net.minecraft.item.ItemStack;

public interface AutoBuyableItem {
    String getDisplayName();
    ItemStack createItemStack();
    int getPrice();
    boolean isEnabled();
    void setEnabled(boolean enabled);
    AutoBuyItemSettings getSettings();
}
