package fun.lumis.features.impl.misc;

import antidaunleak.api.annotation.Native;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;



import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.events.packet.PacketEvent;
import fun.lumis.events.player.TickEvent;
import fun.lumis.utils.math.time.TimerUtil;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServerRPSpoofer extends Module {
    private ResourcePackAction currentAction = ResourcePackAction.WAIT;
    private final TimerUtil counter = TimerUtil.create();

    public ServerRPSpoofer() {
        super("ServerRPSpoof", "Server RP Spoof", ModuleCategory.MISC);
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        if (e.getPacket() instanceof ResourcePackSendS2CPacket) {
            currentAction = ResourcePackAction.ACCEPT;
            e.cancel();
        }
    }
    
    @EventHandler
    @Native(type = Native.Type.VMProtectBeginMutation)
    public void onTick(TickEvent e) {
        ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
        if (networkHandler != null) {
            if (currentAction == ResourcePackAction.ACCEPT) {
                networkHandler.sendPacket(new ResourcePackStatusC2SPacket(mc.player.getUuid(), ResourcePackStatusC2SPacket.Status.ACCEPTED));
                currentAction = ResourcePackAction.SEND;
                counter.resetCounter();
            } else if (currentAction == ResourcePackAction.SEND && counter.isReached(300L)) {
                networkHandler.sendPacket(new ResourcePackStatusC2SPacket(mc.player.getUuid(), ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED));
                currentAction = ResourcePackAction.WAIT;
            }
        }
    }

    @Override
    public void deactivate() {
        currentAction = ResourcePackAction.WAIT;
        super.deactivate();
    }

    public enum ResourcePackAction {
        ACCEPT, SEND, WAIT;
    }
}
