package fun.lumis.features.impl.player;

import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.BooleanSetting;
import fun.lumis.utils.client.Instance;

public class NoEntityTrace extends Module {

    private final BooleanSetting noSword = new BooleanSetting("Без меча", "Не работает с мечом в руке").setValue(true);

    public NoEntityTrace() {
        super("NoEntityTrace", "No Entity Trace", ModuleCategory.PLAYER);
        setup(noSword);
    }

    public static NoEntityTrace getInstance() {
        return Instance.get(NoEntityTrace.class);
    }

    public boolean shouldIgnoreEntityTrace() {
        return isState() && !(mc.player.getMainHandStack().getItem() instanceof net.minecraft.item.SwordItem && noSword.isValue());
    }
}
