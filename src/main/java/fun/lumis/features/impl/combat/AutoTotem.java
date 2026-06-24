package fun.lumis.features.impl.combat;

import fun.lumis.events.player.TickEvent;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.SliderSettings;
import fun.lumis.utils.client.managers.event.EventHandler;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AutoTotem extends Module {

    final SliderSettings healthSetting = new SliderSettings("Хп", "HP for totem").range(1.0f, 20.0f).setValue(10.0f);
    final SliderSettings delaySetting = new SliderSettings("Задержка", "Ticks between swaps").range(0.0f, 5.0f).setValue(0.0f);

    int delayTimer = 0;

    // Флаг блокировки ауры во время перекладывания
    @Setter
    @Getter
    private static boolean swapping = false;

    // Стадия перекладывания (0 = не перекладываем, 1-3 = шаги)
    int swapStage = 0;
    int swapSlot = -1;

    public AutoTotem() {
        super("AutoTotem", ModuleCategory.COMBAT);
        setup(healthSetting, delaySetting);
    }

    @Override
    public void deactivate() {
        delayTimer = 0;
        swapStage = 0;
        swapSlot = -1;
        swapping = false;
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.interactionManager == null) return;

        // Если уже есть тотем в оффхенде — всё ок
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            delayTimer = 0;
            swapStage = 0;
            swapSlot = -1;
            swapping = false;
            return;
        }

        // Если идёт процесс перекладывания — выполняем по шагам (1 клик за тик)
        if (swapStage > 0) {
            executeSwapStage();
            return;
        }

        // Задержка между попытками
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        if (hp <= healthSetting.getValue()) {
            int slot = findTotemSlot();
            if (slot != -1) {
                // Начинаем перекладывание — блокируем ауру
                swapSlot = slot;
                swapStage = 1;
                swapping = true;
            }
        }
    }

    private void executeSwapStage() {
        if (mc.player == null || mc.interactionManager == null) {
            resetSwap();
            return;
        }

        // Проверяем что тотем ещё не в оффхенде (мог появиться другим способом)
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            resetSwap();
            return;
        }

        switch (swapStage) {
            case 1 -> {
                // Шаг 1: Подбираем тотем из слота
                mc.interactionManager.clickSlot(
                        mc.player.playerScreenHandler.syncId,
                        swapSlot, 0, SlotActionType.PICKUP, mc.player
                );
                swapStage = 2;
            }
            case 2 -> {
                // Шаг 2: Кладём в оффхенд (слот 45)
                mc.interactionManager.clickSlot(
                        mc.player.playerScreenHandler.syncId,
                        45, 0, SlotActionType.PICKUP, mc.player
                );
                swapStage = 3;
            }
            case 3 -> {
                // Шаг 3: Если в курсоре остался предмет из оффхенда — возвращаем обратно
                if (!mc.player.playerScreenHandler.getCursorStack().isEmpty()) {
                    mc.interactionManager.clickSlot(
                            mc.player.playerScreenHandler.syncId,
                            swapSlot, 0, SlotActionType.PICKUP, mc.player
                    );
                }
                resetSwap();
                delayTimer = (int) delaySetting.getValue();
            }
        }
    }

    private void resetSwap() {
        swapStage = 0;
        swapSlot = -1;
        swapping = false;
    }

    private int findTotemSlot() {
        for (int i = 9; i < 45; i++) {
            ItemStack stack = mc.player.playerScreenHandler.getSlot(i).getStack();
            if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
                return i;
            }
        }
        return -1;
    }
}