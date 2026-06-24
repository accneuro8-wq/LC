package fun.lumis.features.impl.movement;

import fun.lumis.events.player.TickEvent;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.SelectSetting;
import fun.lumis.features.module.setting.implement.SliderSettings;
import fun.lumis.utils.client.Instance;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.utils.interactions.interact.PlayerInteractionHelper;
import fun.lumis.utils.interactions.simulate.Simulations;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.Blocks;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class NoWeb extends Module {

    public static NoWeb getInstance() {
        return Instance.get(NoWeb.class);
    }

    public final SelectSetting webMode = new SelectSetting("Режим", "Выберите режим обхода")
            .value("Grim", "Vanilla")
            .selected("Grim");

    public final SliderSettings speedSetting = new SliderSettings("Скорость", "Скорость передвижения в паутине")
            .setValue(0.35f).range(0.1f, 1.0f);

    public final SliderSettings verticalSpeed = new SliderSettings("Верт. скорость", "Скорость вверх/вниз")
            .setValue(0.65f).range(0.1f, 1.0f);

    public NoWeb() {
        super("NoWeb", "No Web", ModuleCategory.MOVEMENT);
        setup(webMode, speedSetting, verticalSpeed);
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) return;
        if (!PlayerInteractionHelper.isPlayerInBlock(Blocks.COBWEB)) return;

        String mode = webMode.getSelected();

        if (mode.equals("Grim")) {
            handleGrim();
        } else if (mode.equals("Vanilla")) {
            handleVanilla();
        }
    }

    private void handleGrim() {
        double spd = speedSetting.getValue();
        double vSpd = verticalSpeed.getValue();

        double[] direction = Simulations.calculateDirection(spd);
        mc.player.addVelocity(direction[0], 0, direction[1]);

        if (mc.options.jumpKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, vSpd, mc.player.getVelocity().z);
        } else if (mc.options.sneakKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, -vSpd, mc.player.getVelocity().z);
        } else {
            mc.player.setVelocity(mc.player.getVelocity().x, 0, mc.player.getVelocity().z);
        }
    }

    private void handleVanilla() {
        double spd = speedSetting.getValue() * 0.5;
        double vSpd = verticalSpeed.getValue() * 0.4;

        double[] direction = Simulations.calculateDirection(spd);

        mc.player.setVelocity(direction[0], mc.player.getVelocity().y, direction[1]);

        if (mc.options.jumpKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, vSpd, mc.player.getVelocity().z);
        } else if (mc.options.sneakKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, -vSpd, mc.player.getVelocity().z);
        }
    }
}