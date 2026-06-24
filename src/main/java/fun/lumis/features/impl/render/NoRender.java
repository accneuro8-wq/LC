package fun.lumis.features.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.MultiSelectSetting;
import fun.lumis.utils.client.Instance;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NoRender extends Module {
    public static NoRender getInstance() {
        return Instance.get(NoRender.class);
    }

    public MultiSelectSetting modeSetting = new MultiSelectSetting("Элементы", "Выберите элементы для игнорирования")
            .value("Fire", "Bad Effects", "Block Overlay", "Darkness", "Damage", "NoFog")
            .selected("Fire", "Bad Effects", "Block Overlay", "Darkness", "Damage");

    public NoRender() {
        super("NoRender", "No Render", ModuleCategory.RENDER);
        setup(modeSetting);
    }
}
