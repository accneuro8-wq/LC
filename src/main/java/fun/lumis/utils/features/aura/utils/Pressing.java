package fun.lumis.utils.features.aura.utils;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.item.ItemStack;
import fun.lumis.utils.display.interfaces.QuickImports;
import fun.lumis.utils.client.packet.network.Network;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class Pressing implements QuickImports {
    long lastClickTime = System.currentTimeMillis();

    public boolean isCooldownComplete(boolean dynamicCooldown, int ticks) {
        boolean isMace = isHoldingMace();

        boolean cooldownReady = isMace || mc.player.getAttackCooldownProgress(ticks) > 0.9F;
        boolean minimumDelayPassed = lastClickPassed() >= 500;

        return cooldownReady && minimumDelayPassed;
    }

    public boolean hasTicksElapsedSinceLastClick(int ticks) {
        return lastClickPassed() >= (ticks * 50L * (20F / Network.TPS));
    }

    public long lastClickPassed() {
        return System.currentTimeMillis() - lastClickTime;
    }

    public void recalculate() {
        lastClickTime = System.currentTimeMillis();
    }

    private boolean isHoldingMace() {
        ItemStack mainHand = mc.player.getMainHandStack();

        return mainHand.getItem().getTranslationKey().toLowerCase().contains("mace");
    }
}
