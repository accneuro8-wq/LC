package fun.lumis.main.listener.impl;

import fun.lumis.utils.interactions.inv.InventoryFlowManager;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.utils.client.managers.api.draggable.AbstractDraggable;
import fun.lumis.utils.client.packet.network.Network;
import fun.lumis.lumis;
import fun.lumis.main.listener.Listener;
import fun.lumis.events.item.UsingItemEvent;
import fun.lumis.events.packet.PacketEvent;
import fun.lumis.events.player.TickEvent;

public class EventListener implements Listener {
    public static boolean serverSprint;
    public static int selectedSlot;

    @EventHandler
    public void onTick(TickEvent e) {
        Network.tick();
        lumis.getInstance().getAttackPerpetrator().tick();
        InventoryFlowManager.tick();
        lumis.getInstance().getDraggableRepository().draggable().forEach(AbstractDraggable::tick);
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        switch (e.getPacket()) {
            case ClientCommandC2SPacket command -> serverSprint = switch (command.getMode()) {
                case ClientCommandC2SPacket.Mode.START_SPRINTING -> true;
                case ClientCommandC2SPacket.Mode.STOP_SPRINTING -> false;
                default -> serverSprint;
            };
            case UpdateSelectedSlotC2SPacket slot -> selectedSlot = slot.getSelectedSlot();
            default -> {}
        }
        Network.packet(e);
        lumis.getInstance().getAttackPerpetrator().onPacket(e);
        lumis.getInstance().getDraggableRepository().draggable().forEach(drag -> drag.packet(e));
    }

    @EventHandler
    public void onUsingItemEvent(UsingItemEvent e) {
        lumis.getInstance().getAttackPerpetrator().onUsingItem(e);
    }
}
