package fun.lumis.features.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import fun.lumis.features.module.setting.implement.SliderSettings;
import fun.lumis.features.module.setting.implement.ColorSetting;
import fun.lumis.features.module.setting.implement.MultiSelectSetting;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.utils.client.Instance;

import java.awt.*;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Hud extends Module {
    public static Hud getInstance() {
        return Instance.get(Hud.class);
    }
    public MultiSelectSetting interfaceSettings = new MultiSelectSetting("Элементы", "Настройка элементов интерфейса")
            .value("Вотермарка", "Кейбинды", "Эффекты", "Список стафа", "Таргет худ", "Бинды", "Кулдауны", "Инвентарь", "Инфо игрока", "Уведомления", "Броня", "Хотбар", "Скорборд")
            .selected("Вотермарка", "Кейбинды", "Эффекты", "Список стафа", "Таргет худ", "Бинды", "Кулдауны", "Инвентарь", "Инфо игрока", "Уведомления");

    public MultiSelectSetting notificationSettings = new MultiSelectSetting("Уведомления", "Выберите, когда будут появляться уведомления")
            .value("Переключение модулей", "Staff Join", "Staff Leave", "Item Pick Up", "Auto Armor", "Break Shield")
            .selected("Item Pick Up", "Auto Armor", "Break Shield")
            .visible(() -> interfaceSettings.isSelected("Уведомления"));

    public ColorSetting colorSetting = new ColorSetting("Изменяет цвет некоторых модулей", "Выберите цвет клиента")
            .setColor(new Color(255, 101, 57, 255).getRGB())
            .presets(0xFF6C9AFD, 0xFF8C7FFF, 0xFFFFA576, 0xFFFF7B7B);

    public SliderSettings soundVolumeSetting = new SliderSettings("Sound Volume", "Volume for module switch sounds")
            .range(0.0f, 1.0f)
            .setValue(1.0f)
            .visible(() -> interfaceSettings.isSelected("Уведомления"));

    public float getModuleVolume() {
        return soundVolumeSetting.getValue();
    }

    public Hud() {
        super("Hud", ModuleCategory.RENDER);
        setup(colorSetting, interfaceSettings, notificationSettings, soundVolumeSetting);
    }
}