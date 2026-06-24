package fun.lumis.features.impl.combat;

import fun.lumis.events.player.TickEvent;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.SliderSettings;
import fun.lumis.utils.client.managers.event.EventHandler;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.TridentItem;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class FastBow extends Module {

    final SliderSettings minCharge = new SliderSettings("Мин. натяжение", "Тики")
            .setValue(3F).range(1F, 20F);

    public FastBow() {
        super("FastBow", "Fast Bow", ModuleCategory.COMBAT);
        setup(minCharge);
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (!mc.player.isUsingItem()) return;

        var item = mc.player.getActiveItem().getItem();

        if (!(item instanceof BowItem) && !(item instanceof CrossbowItem) && !(item instanceof TridentItem)) return;

        if (mc.player.getItemUseTime() < (int) minCharge.getValue()) return;

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.RELEASE_USE_ITEM,
                BlockPos.ORIGIN,
                Direction.DOWN
        ));

        mc.player.stopUsingItem();
    }
}