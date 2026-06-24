package fun.lumis.utils.features.aura.striking;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import fun.lumis.features.module.setting.implement.SelectSetting;
import fun.lumis.utils.display.interfaces.QuickImports;
import fun.lumis.events.item.UsingItemEvent;
import fun.lumis.events.packet.PacketEvent;
import fun.lumis.utils.features.aura.warp.Turns;

import java.util.List;

@Getter
public class StrikerConstructor implements QuickImports {
    StrikeManager attackHandler = new StrikeManager();

    public void tick() {
        attackHandler.tick();
    }

    public void onPacket(PacketEvent e) {
        attackHandler.onPacket(e);
    }

    public void onUsingItem(UsingItemEvent e) {
        attackHandler.onUsingItem(e);
    }

    public void performAttack(AttackPerpetratorConfigurable configurable) {
        attackHandler.handleAttack(configurable);
    }

    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class AttackPerpetratorConfigurable {
        LivingEntity target;
        Turns angle;
        float maximumRange;
        boolean onlyCritical, shouldBreakShield, shouldUnPressShield, eatAndAttack;
        Box box;
        SelectSetting aimMode;

        public AttackPerpetratorConfigurable(LivingEntity target, Turns angle, float maximumRange, List<String> options, SelectSetting aimMode, Box box) {
            this.target = target;
            this.angle = angle;
            this.maximumRange = maximumRange;
            // Опции хранятся русскими строками (см. Aura.attackSetting) — сверяем с ними,
            // иначе все флаги всегда false (англ. строки сюда никогда не приходят).
            this.onlyCritical = options.contains("Только криты");
            this.shouldBreakShield = options.contains("Пробитие щита");
            this.shouldUnPressShield = options.contains("Опускать щит");
            this.eatAndAttack = options.contains("Пауза при еде");
            this.box = box;
            this.aimMode = aimMode;
        }
    }
}
