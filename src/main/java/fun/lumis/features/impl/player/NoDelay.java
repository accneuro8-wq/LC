package fun.lumis.features.impl.player;

import fun.lumis.events.player.TickEvent;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.MultiSelectSetting;
import fun.lumis.mixins.client.IMinecraftClient;
import fun.lumis.utils.client.Instance;
import fun.lumis.utils.client.managers.event.EventHandler;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NoDelay extends Module {

    MultiSelectSetting delaySetting = new MultiSelectSetting("Убрать задержку", "Выберите задержки для отключения")
            .value(
                    "Прыжок",
                    "Использование",
                    "Разрушение",
                    "Установка"
            );

    public NoDelay() {
        super("NoDelay", "No Delay", ModuleCategory.PLAYER);
        setup(delaySetting);
    }

    public static NoDelay getInstance() {
        return Instance.get(NoDelay.class);
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onTick(TickEvent e) {
        if (mc.player == null) return;

        IMinecraftClient accessor = (IMinecraftClient) mc;

        if (delaySetting.isSelected("Прыжок")) {
            mc.player.jumpingCooldown = 0;
        }

        if (delaySetting.isSelected("Использование")) {
            accessor.setUseCooldown(0);
        }

        if (delaySetting.isSelected("Разрушение") && mc.interactionManager != null) {
            mc.interactionManager.blockBreakingCooldown = 0;
        }

        if (delaySetting.isSelected("Установка")) {
            accessor.setUseCooldown(0);
        }
    }
}