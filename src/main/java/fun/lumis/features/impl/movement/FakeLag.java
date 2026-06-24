package fun.lumis.features.impl.movement;

import fun.lumis.events.packet.PacketEvent;
import fun.lumis.events.player.TickEvent;
import fun.lumis.events.render.WorldRenderEvent;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.SliderSettings;
import fun.lumis.features.module.setting.implement.BooleanSetting;
import fun.lumis.features.module.setting.implement.SelectSetting;
import fun.lumis.features.module.setting.implement.ColorSetting;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.utils.display.geometry.Render3D;
import fun.lumis.utils.interactions.interact.PlayerInteractionHelper;
import fun.lumis.utils.client.Instance;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class FakeLag extends Module {

    public static FakeLag getInstance() {
        return Instance.get(FakeLag.class);
    }

    // ── Основные настройки ──
    final SelectSetting mode = new SelectSetting("Mode", "Режим работы")
            .value("Normal", "Pulse")
            .selected("Normal");

    final SliderSettings delay = new SliderSettings("Delay", "Задержка пакетов (ms)")
            .setValue(400)
            .range(50, 2000);

    final SliderSettings maxPackets = new SliderSettings("MaxPackets", "Макс. пакетов за тик")
            .setValue(5)
            .range(1, 50);

    // ── Pulse режим ──
    final SliderSettings pulseDuration = new SliderSettings("PulseDuration", "Длительность пульса (ms)")
            .setValue(500)
            .range(50, 3000)
            .visible(() -> mode.isSelected("Pulse"));

    final SliderSettings pulseCooldown = new SliderSettings("PulseCooldown", "Кулдаун между пульсами (ms)")
            .setValue(1500)
            .range(0, 5000)
            .visible(() -> mode.isSelected("Pulse"));

    // ── Условия ──
    final BooleanSetting onlyNearPlayers = new BooleanSetting("OnlyNearPlayers", "Только рядом с игроками")
            .setValue(false);

    final SliderSettings playerRadius = new SliderSettings("PlayerRadius", "Радиус поиска игроков")
            .setValue(20f)
            .range(5f, 50f)
            .visible(() -> onlyNearPlayers.isValue());

    final BooleanSetting flushOnDisable = new BooleanSetting("FlushOnDisable", "Отправить пакеты при выключении")
            .setValue(true);

    final BooleanSetting flushOnStop = new BooleanSetting("FlushOnStop", "Отправить при остановке")
            .setValue(false);

    // ── Рендер ──
    final SelectSetting render = new SelectSetting("Render", "Отображение")
            .value("Off", "Ghost", "Box")
            .selected("Ghost");

    final ColorSetting renderColor = new ColorSetting("RenderColor", "Цвет отображения")
            .value(0x80FFFFFF)
            .visible(() -> !render.isSelected("Off"));

    // ── Внутреннее состояние ──
    final Deque<TimedPacket> packetQueue = new ArrayDeque<>();
    final Deque<PositionSnapshot> posHistory = new ArrayDeque<>();

    boolean pulsing = false;
    long pulseStart = 0L;
    long lastPulseEnd = 0L;

    Vec3d ghostPos = null;
    float ghostYaw = 0f;

    public FakeLag() {
        super("FakeLag", "Fake Lag", ModuleCategory.MOVEMENT);
        setup(
                mode, delay, maxPackets,
                pulseDuration, pulseCooldown,
                onlyNearPlayers, playerRadius,
                flushOnDisable, flushOnStop,
                render, renderColor
        );
    }

    // ══════════════════════════════════════════════
    // Жизненный цикл
    // ══════════════════════════════════════════════

    @Override
    public void activate() {
        packetQueue.clear();
        posHistory.clear();
        pulsing = false;
        pulseStart = 0L;
        lastPulseEnd = 0L;
        ghostPos = null;
        savePosition();
    }

    @Override
    public void deactivate() {
        if (flushOnDisable.isValue()) {
            sendAllPackets();
        } else {
            packetQueue.clear();
        }
        posHistory.clear();
        ghostPos = null;
    }

    // ══════════════════════════════════════════════
    // Тик
    // ══════════════════════════════════════════════

    @EventHandler
    public void onTick(TickEvent ignoredE) {
        if (mc.player == null || mc.world == null) return;

        // Проверка условий
        if (!shouldLag()) {
            sendAllPackets();
            savePosition();
            return;
        }

        savePosition();

        long now = System.currentTimeMillis();

        if (mode.isSelected("Pulse")) {
            tickPulse(now);
        } else {
            tickNormal(now);
        }
    }

    private void tickNormal(long now) {
        long delayMs = (long) delay.getValue();
        int limit = (int) maxPackets.getValue();
        int sent = 0;

        while (!packetQueue.isEmpty() && sent < limit) {
            TimedPacket first = packetQueue.peekFirst();
            if (first == null) break;

            if (now - first.time >= delayMs) {
                packetQueue.pollFirst();
                PlayerInteractionHelper.sendPacketWithOutEvent(first.packet);
                sent++;
            } else {
                break;
            }
        }

        // Блинк при остановке
        if (flushOnStop.isValue() && isPlayerStopped()) {
            sendAllPackets();
        }
    }

    private void tickPulse(long now) {
        long duration = (long) pulseDuration.getValue();
        long cooldown = (long) pulseCooldown.getValue();

        if (pulsing) {
            // Пульс идёт — проверяем не истёк ли
            if (now - pulseStart >= duration) {
                sendAllPackets();
                pulsing = false;
                lastPulseEnd = now;
            }
        } else {
            // Пульс не идёт — отправляем пакеты нормально (не копим)
            // Пакеты начнут копиться только когда начнётся новый пульс
            if (now - lastPulseEnd >= cooldown) {
                pulsing = true;
                pulseStart = now;
            }
        }
    }

    // ══════════════════════════════════════════════
    // Пакеты
    // ══════════════════════════════════════════════

    @EventHandler
    public void onPacket(PacketEvent e) {
        if (mc.player == null || mc.world == null) return;

        Packet<?> packet = e.getPacket();

        // Входящие пакеты — только респаун/присоединение
        if (!e.isSend()) {
            if (packet instanceof PlayerRespawnS2CPacket || packet instanceof GameJoinS2CPacket) {
                sendAllPackets();
                packetQueue.clear();
            }
            return;
        }

        // Не блокируем респаун
        if (packet instanceof ClientStatusC2SPacket status
                && status.getMode() == ClientStatusC2SPacket.Mode.PERFORM_RESPAWN) {
            return;
        }

        // Проверяем нужно ли задерживать
        if (!shouldLag()) return;

        // В pulse режиме задерживаем только во время пульса
        if (mode.isSelected("Pulse") && !pulsing) return;

        packetQueue.addLast(new TimedPacket(System.currentTimeMillis(), packet));
        e.cancel();
    }

    // ══════════════════════════════════════════════
    // Рендер
    // ══════════════════════════════════════════════

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (mc.player == null || mc.world == null) return;
        if (render.isSelected("Off")) return;
        if (packetQueue.isEmpty()) return;

        PositionSnapshot serverPos = getDelayedPosition();
        if (serverPos == null) return;

        // Не рендерим если слишком близко
        if (serverPos.pos.squaredDistanceTo(mc.player.getPos()) < 0.1) return;

        int color = renderColor.getColor();

        if (render.isSelected("Ghost")) {
            Render3D.drawEntity(
                    mc.player,
                    serverPos.pos,
                    serverPos.yaw,
                    color,
                    e.getStack(),
                    e.getPartialTicks()
            );
        } else if (render.isSelected("Box")) {
            Vec3d offset = serverPos.pos.subtract(mc.player.getPos());
            Box box = mc.player.getBoundingBox().offset(offset);
            Render3D.drawBox(box, color, 1.5f);
        }
    }

    // ══════════════════════════════════════════════
    // Утилиты
    // ══════════════════════════════════════════════

    private boolean shouldLag() {
        if (mc.player == null) return false;

        // Проверка радиуса игроков
        if (onlyNearPlayers.isValue()) {
            double radius = playerRadius.getValue();
            boolean found = false;

            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player || p.isDead() || p.isRemoved()) continue;
                if (p.distanceTo(mc.player) <= radius) {
                    found = true;
                    break;
                }
            }

            if (!found) return false;
        }

        return true;
    }

    private void sendAllPackets() {
        while (!packetQueue.isEmpty()) {
            TimedPacket tp = packetQueue.pollFirst();
            if (tp != null && tp.packet != null) {
                PlayerInteractionHelper.sendPacketWithOutEvent(tp.packet);
            }
        }
    }

    private void savePosition() {
        if (mc.player == null) return;

        long now = System.currentTimeMillis();
        posHistory.addLast(new PositionSnapshot(
                now,
                mc.player.getPos(),
                mc.player.getYaw(),
                mc.player.getPitch()
        ));

        // Чистим старые записи (держим максимум delay + 2 секунды)
        long keepTime = (long) delay.getValue() + 2000L;
        while (!posHistory.isEmpty()) {
            PositionSnapshot first = posHistory.peekFirst();
            if (first == null || now - first.time > keepTime) {
                posHistory.pollFirst();
            } else {
                break;
            }
        }
    }

    private PositionSnapshot getDelayedPosition() {
        if (posHistory.isEmpty()) return null;

        long targetTime = System.currentTimeMillis() - (long) delay.getValue();
        PositionSnapshot best = null;
        long bestDiff = Long.MAX_VALUE;

        for (PositionSnapshot snap : posHistory) {
            long diff = Math.abs(snap.time - targetTime);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = snap;
            }
        }

        return best;
    }

    private boolean isPlayerStopped() {
        if (mc.player == null) return false;
        Vec3d vel = mc.player.getVelocity();
        return vel.x * vel.x + vel.z * vel.z < 0.0001;
    }

    public boolean isLagActive() {
        return shouldLag() && !packetQueue.isEmpty();
    }

    // ══════════════════════════════════════════════
    // Записи
    // ══════════════════════════════════════════════

    private record TimedPacket(long time, Packet<?> packet) {}
    private record PositionSnapshot(long time, Vec3d pos, float yaw, float pitch) {}
}