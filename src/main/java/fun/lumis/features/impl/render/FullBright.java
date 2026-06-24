package fun.lumis.features.impl.render;

import fun.lumis.events.player.TickEvent;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.utils.client.managers.event.EventHandler;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public class FullBright extends Module {

    public FullBright() {
        super("FullBright", "FullBright", ModuleCategory.RENDER);
    }

    @Override
    public void deactivate() {
        if (mc.player != null) {
            mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
        super.deactivate();
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (mc.player != null) {
            mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 500, 255, true, false));
        }
    }
}
