package fun.lumis.features.impl.movement;

import fun.lumis.events.packet.PacketEvent;
import fun.lumis.events.player.TickEvent;
import fun.lumis.events.render.WorldRenderEvent;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.utils.display.color.ColorAssist;
import fun.lumis.utils.display.geometry.Render3D;
import fun.lumis.utils.interactions.interact.PlayerInteractionHelper;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class Blink extends Module {

    private final List<Packet<?>> storedPackets = new ArrayList<>();
    private Box startBox;
    public static int tickStop = -1;
    private boolean flushing = false;
    private int flushIndex = 0;

    public Blink() {
        super("Blink", ModuleCategory.MOVEMENT);
        setup();
    }

    @Override
    public void activate() {
        if (mc.player == null) return;
        startBox = mc.player.getBoundingBox();
        storedPackets.clear();
        tickStop = -1;
        flushing = false;
        flushIndex = 0;
    }

    @Override
    public void deactivate() {
        startFlushing();
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onPacket(PacketEvent e) {
        if (PlayerInteractionHelper.nullCheck()) return;
        if (mc.getNetworkHandler() == null) return;

        Packet<?> packet = e.getPacket();

        if (packet instanceof KeepAliveS2CPacket s2c) {
            mc.getNetworkHandler().sendPacket(new KeepAliveC2SPacket(s2c.getId()));
            e.cancel();
            return;
        }

        if (packet instanceof CommonPingS2CPacket ping) {
            mc.getNetworkHandler().sendPacket(new CommonPongC2SPacket(ping.getParameter()));
            e.cancel();
            return;
        }

        if (packet instanceof PlayerPositionLookS2CPacket teleport) {
            mc.getNetworkHandler().sendPacket(new TeleportConfirmC2SPacket(teleport.teleportId()));
            return;
        }

        if (packet instanceof PlayerRespawnS2CPacket
                || packet instanceof GameJoinS2CPacket) {
            hardReset();
            setState(false);
            return;
        }

        if (packet instanceof ClientStatusC2SPacket status
                && status.getMode() == ClientStatusC2SPacket.Mode.PERFORM_RESPAWN) {
            hardReset();
            setState(false);
            return;
        }

        if (e.isSend() && !flushing && tickStop < 0) {
            if (packet instanceof KeepAliveC2SPacket) return;
            if (packet instanceof CommonPongC2SPacket) return;
            if (packet instanceof TeleportConfirmC2SPacket) return;

            storedPackets.add(packet);
            e.cancel();
        }
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onTick(TickEvent e) {
        if (PlayerInteractionHelper.nullCheck()) return;

        tickStop--;

        if (tickStop >= 0 && !storedPackets.isEmpty() && !flushing) {
            startBox = mc.player.getBoundingBox();
            startFlushing();
            return;
        }

        if (flushing) {
            processFlushTick();
        }
    }

    private void startFlushing() {
        if (storedPackets.isEmpty()) {
            flushing = false;
            return;
        }
        flushing = true;
        flushIndex = 0;
    }

    private void processFlushTick() {
        if (flushIndex >= storedPackets.size()) {
            hardReset();
            return;
        }

        int sent = 0;
        int movesSent = 0;

        while (flushIndex < storedPackets.size() && sent < 5) {
            Packet<?> packet = storedPackets.get(flushIndex);

            if (packet instanceof PlayerMoveC2SPacket) {
                if (movesSent >= 1) break;
                movesSent++;
            }

            PlayerInteractionHelper.sendPacketWithOutEvent(packet);
            flushIndex++;
            sent++;
        }

        if (flushIndex >= storedPackets.size()) {
            hardReset();
        }
    }

    private void hardReset() {
        storedPackets.clear();
        flushing = false;
        flushIndex = 0;
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onWorldRender(WorldRenderEvent e) {
        if (startBox != null) {
            Render3D.drawBox(startBox, ColorAssist.getClientColor(), 1);
        }
    }
}