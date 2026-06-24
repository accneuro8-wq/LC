package fun.lumis.display.screens.clickgui.components.implement.settings;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import fun.lumis.features.module.setting.Setting;
import fun.lumis.display.screens.clickgui.components.AbstractComponent;

@Getter
@RequiredArgsConstructor
public abstract class AbstractSettingComponent extends AbstractComponent {
    protected final Setting setting;
}
