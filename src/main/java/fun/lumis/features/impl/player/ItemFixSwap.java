package fun.lumis.features.impl.player;

import fun.lumis.events.keyboard.HotBarScrollEvent;
import fun.lumis.events.player.HotBarUpdateEvent;
import fun.lumis.events.player.TickEvent;
import fun.lumis.events.render.ItemRendererEvent;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.utils.interactions.inv.InventoryTask;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class ItemFixSwap extends Module {

    // Состояние блокировки
    boolean locked;
    int lockedSlot = -1;
    int pendingSlot = -1;
    ItemStack lockedStack;

    // Синхронизация
    int lastSentSlot = -1;
    int serverSlot = -1;
    ClientWorld lastWorld;
    boolean needsSync;

    public ItemFixSwap() {
        super("ItemFixSwap", "ItemFixSwap", ModuleCategory.PLAYER);
    }

    @Override
    public void deactivate() {
        reset();
        super.deactivate();
    }

    private void reset() {
        locked = false;
        lockedSlot = -1;
        pendingSlot = -1;
        lockedStack = null;
        lastSentSlot = -1;
        serverSlot = -1;
        needsSync = false;
    }

    @EventHandler
    public void onItemRenderer(ItemRendererEvent e) {
        if (!locked || lockedStack == null) return;
        if (e.getHand() != Hand.MAIN_HAND) return;
        if (mc.player == null || e.getPlayer() != mc.player) return;

        e.setStack(lockedStack);
    }

    @EventHandler
    public void onHotBarUpdate(HotBarUpdateEvent e) {
        e.cancel();

        // Восстанавливаем серверный слот когда не заблокированы
        if (mc.player != null && !locked && serverSlot != -1) {
            mc.player.getInventory().selectedSlot = serverSlot;
        }
    }

    @EventHandler
    public void onHotBarScroll(HotBarScrollEvent e) {
        if (isUsingMainHand()) {
            e.cancel();
        }
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) return;

        handleWorldChange();
        handleSlotLocking();
    }

    private void handleWorldChange() {
        if (lastWorld == mc.world) return;

        lastWorld = mc.world;
        lastSentSlot = -1;
        serverSlot = mc.player.getInventory().selectedSlot;
        needsSync = true;
    }

    private void handleSlotLocking() {
        // Синхронизация после смены мира
        if (needsSync) {
            int slot = pendingSlot != -1 ? pendingSlot : mc.player.getInventory().selectedSlot;
            syncSlot(slot);
            needsSync = false;
        }

        boolean using = isUsingMainHand();
        int currentSlot = mc.player.getInventory().selectedSlot;

        if (using) {
            handleUsingItem(currentSlot);
        } else {
            handleNotUsingItem();
        }
    }

    private void handleUsingItem(int currentSlot) {
        if (!locked) {
            // Начинаем блокировку
            locked = true;
            lockedSlot = currentSlot;
            lockedStack = mc.player.getMainHandStack().copy();
            sendSlot(lockedSlot);
            return;
        }

        // Уже заблокированы - запоминаем попытку сменить слот
        if (currentSlot != lockedSlot) {
            pendingSlot = currentSlot;
            mc.player.getInventory().selectedSlot = lockedSlot;
        }
    }

    private void handleNotUsingItem() {
        if (!locked) return;

        // Снимаем блокировку
        locked = false;
        lockedStack = null;

        if (pendingSlot != -1 && pendingSlot != lockedSlot) {
            // Применяем отложенный слот
            syncSlot(pendingSlot);
            InventoryTask.updateSlots();
        } else {
            // Возвращаем серверный слот
            mc.player.getInventory().selectedSlot = serverSlot;
        }

        lockedSlot = -1;
        pendingSlot = -1;
    }

    private boolean isUsingMainHand() {
        return mc.player != null
                && mc.player.isUsingItem()
                && mc.player.getActiveHand() == Hand.MAIN_HAND;
    }

    private void syncSlot(int slot) {
        if (mc.player == null) return;

        slot = MathHelper.clamp(slot, 0, 8);
        mc.player.getInventory().selectedSlot = slot;
        sendSlot(slot);
    }

    private void sendSlot(int slot) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (lastSentSlot == slot) return;

        slot = MathHelper.clamp(slot, 0, 8);
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        lastSentSlot = slot;
        serverSlot = slot;
    }
}