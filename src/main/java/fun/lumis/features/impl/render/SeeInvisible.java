package fun.lumis.features.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.SliderSettings;
import fun.lumis.utils.display.color.ColorAssist;
import fun.lumis.events.render.EntityColorEvent;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SeeInvisible extends Module {
    SliderSettings alphaSetting = new SliderSettings("Прозрачность", "Прозрачность игрока").setValue(0.5f).range(0.1F, 1);
    public SeeInvisible() {
        super("SeeInvisible", "See Invisible", ModuleCategory.RENDER);
        setup(alphaSetting);
    }

    @EventHandler
    public void onEntityColor(EntityColorEvent e) {
        e.setColor(ColorAssist.multAlpha(e.getColor(), alphaSetting.getValue()));
        e.cancel();
    }

}
