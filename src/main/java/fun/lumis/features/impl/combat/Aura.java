package fun.lumis.features.impl.combat;

import antidaunleak.api.annotation.Native;
import fun.lumis.display.hud.Notifications;
import fun.lumis.events.packet.PacketEvent;
import fun.lumis.events.player.RotationUpdateEvent;
import fun.lumis.events.player.TickEvent;
import fun.lumis.events.render.WorldRenderEvent;
import fun.lumis.features.impl.movement.ElytraTarget;
import fun.lumis.features.impl.movement.TargetStrafe;
import fun.lumis.features.impl.render.Hud;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.BooleanSetting;
import fun.lumis.features.module.setting.implement.MultiSelectSetting;
import fun.lumis.features.module.setting.implement.SelectSetting;
import fun.lumis.features.module.setting.implement.SliderSettings;
import fun.lumis.utils.client.Instance;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.utils.client.managers.event.types.EventType;
import fun.lumis.utils.display.color.ColorAssist;
import fun.lumis.utils.display.geometry.Render3D;
import fun.lumis.utils.features.aura.point.MultiPoint;
import fun.lumis.utils.features.aura.rotations.constructor.LinearConstructor;
import fun.lumis.utils.features.aura.rotations.constructor.RotateConstructor;
import fun.lumis.utils.features.aura.rotations.impl.*;
import fun.lumis.utils.features.aura.striking.StrikeManager;
import fun.lumis.utils.features.aura.striking.StrikerConstructor;
import fun.lumis.utils.features.aura.target.TargetFinder;
import fun.lumis.utils.features.aura.utils.MathAngle;
import fun.lumis.utils.features.aura.warp.Turns;
import fun.lumis.utils.features.aura.warp.TurnsConfig;
import fun.lumis.utils.features.aura.warp.TurnsConnection;
import fun.lumis.utils.interactions.interact.PlayerInteractionHelper;
import fun.lumis.utils.interactions.inv.InventoryToolkit;
import fun.lumis.utils.math.task.TaskPriority;
import fun.lumis.lumis;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

@Setter
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Aura extends Module {

    // ====== Constants ======
    private static final float RANGE_MARGIN = 0.253F;
    private static final double MAX_SPOOF_STEP = 3.0;

    private static final String ONLY_CRITS = "Только криты";
    private static final String SMART_CRITS = "Умные криты";
    private static final String SHIELD_BREAK = "Пробитие щита";
    private static final String SHIELD_LOWER = "Опускать щит";
    private static final String PAUSE_EATING = "Пауза при еде";
    private static final String THROUGH_WALLS = "Сквозь стены";
    private static final String FAKE_LAG = "Фейк-лаги";
    private static final String HIT_CHANCE_KEY = "Шанс попадания";

    private static final double PREDICT_MIN = 0.7;
    private static final double PREDICT_MAX_GROUND = 1.4;
    private static final double PREDICT_MAX_GLIDING = 5.0;
    private static final float FAR_DISTANCE_THRESHOLD = 3.0f;
    private static final double VERTICAL_PREDICT_FACTOR = 0.45;

    private static final double HIT_HEIGHT_FAR = 0.72;
    private static final double HIT_HEIGHT_FAR_JITTER = 0.12;
    private static final double HIT_HEIGHT_NEAR = 0.78;
    private static final double HIT_HEIGHT_NEAR_JITTER = 0.16;
    private static final double HIT_HEIGHT_GLIDING = 0.5;

    // Crit — fallDistance порог снижен, ванилла считает крит при fallDistance > 0
    private static final float CRIT_MIN_FALL_DISTANCE = 0.01f;
    private static final float CRIT_COOLDOWN_THRESHOLD = 0.9f;

    private static final double MAX_HORIZONTAL_VELOCITY = 0.8;
    private static final double MAX_VERTICAL_VELOCITY = 1.2;

    private static final double HVH_BASE_PREDICT = 1.15;
    private static final double HVH_DISTANCE_FACTOR = 0.05;
    private static final double HVH_VERTICAL_FACTOR = 0.4;

    private static final int REACH_RESTORE_COOLDOWN_TICKS = 2;

    // Сколько тиков ждать крит перед тем как ударить без крита
    private static final int CRIT_WAIT_TIMEOUT = 8;

    private static final Map<String, Integer> LEGIT_SPRINT_MAP = Map.of(
            "FunTime", 1, "Matrix", 1, "Snap", 1, "HolyWorld", 2
    );

    public static Aura getInstance() {
        return Instance.get(Aura.class);
    }

    // ====== Core ======
    TargetFinder targetSelector = new TargetFinder();
    MultiPoint pointFinder = new MultiPoint();

    // Кэш инстансов режимов ротации — состояние (last-углы/захват цели/sway) должно
    // переживать тики, иначе вся плавность ломается. Один инстанс на режим.
    private final Map<String, RotateConstructor> smoothModeCache = new HashMap<>();
    private final LinearConstructor glidingMode = new LinearConstructor();

    @NonFinal LivingEntity target, lastTarget;
    @NonFinal long shiftTapEndTime = 0;
    @NonFinal int originalSlot = -1;
    @NonFinal int maceSwitchTimer = 0;
    @NonFinal boolean needsReachRestore = false;
    @NonFinal Vec3d reachRestorePos = null;
    @NonFinal int reachRestoreCooldown = 0;
    @NonFinal long lastAttackTime = 0;
    @NonFinal int noTargetTicks = 0;
    @NonFinal int cachedMaceSlot = -1;
    @NonFinal int maceSearchCooldown = 0;

    // Счётчик тиков ожидания крита — если слишком долго ждём, бьём без крита
    @NonFinal int critWaitTicks = 0;

    @NonFinal @Getter boolean fakeRotateFlag = false;
    @NonFinal @Getter boolean shouldRotateFlag = false;
    @NonFinal @Getter public static float legitSprintNeed;
    public static int tickStop = -1;

    public static boolean fakeRotate;
    public static boolean shouldRotate;

    private final Queue<Packet<?>> packets = new ConcurrentLinkedQueue<>();
    @NonFinal Box box;

    // ====== Settings ======
    SelectSetting aimMode = new SelectSetting("Наводка", "Выберите тип наводки")
            .value("None", "SpookytimeDuel", "ReallyWorld", "Vodkacraft")
            .selected("SpookytimeDuel");

    MultiSelectSetting targetType = new MultiSelectSetting("Тип таргета", "Фильтрует весь список целей по типу")
            .value("Players", "Mobs", "Animals", "Friends", "Armor Stand")
            .selected("Players", "Mobs", "Animals");

    SliderSettings fovSetting = new SliderSettings("Угол обзора (FOV)", "Ограничивает радиус атаки ауры")
            .setValue(360).range(0F, 360F);

    SliderSettings attackRange = new SliderSettings("Дистанция удара", "Дальность атаки до цели")
            .setValue(3).range(1F, 6F);

    SliderSettings lookRange = new SliderSettings("Дополнительная дистанция поиска", "Диапазон поиска до цели")
            .setValue(1.5f).range(0F, 2F);

    MultiSelectSetting attackSetting = new MultiSelectSetting("Параметры атаки", "Тонкая настройка поведения модуля в бою")
            .value(ONLY_CRITS, SMART_CRITS, SHIELD_BREAK, SHIELD_LOWER,
                    PAUSE_EATING, THROUGH_WALLS, FAKE_LAG, HIT_CHANCE_KEY)
            .selected(SMART_CRITS, SHIELD_BREAK);
    // ^^^ По умолчанию только SMART_CRITS, не оба сразу

    SliderSettings hitChance = new SliderSettings("Шанс удара в %", "Шанс удара по цели")
            .setValue(100).range(1F, 100F).visible(() -> attackSetting.isSelected(HIT_CHANCE_KEY));

    SelectSetting correctionType = new SelectSetting("Коррекции движения", "Выбор коррекции движения игрока")
            .value("Free", "Focused", "Target", "Not visible").selected("Free");

    SelectSetting sprintReset = new SelectSetting("Сброс спринта", "Выбор сброса спринта перед ударом")
            .value("Legit", "Packet").selected("Legit");

    BooleanSetting groundCrits = new BooleanSetting("Удары на земле", "Бьёт на земле при полном кулдауне (без ожидания крита)")
            .setValue(true).visible(() -> attackSetting.isSelected(ONLY_CRITS) || attackSetting.isSelected(SMART_CRITS));

    BooleanSetting autoMace = new BooleanSetting("AutoMace", "Автоматически бьет булавой на определенной высоте")
            .setValue(false);

    SliderSettings maceHeight = new SliderSettings("Высота для AutoMace", "Высота для автоматического использования булавы")
            .setValue(50).range(3F, 200F).visible(() -> autoMace.isValue());

    BooleanSetting reachEnabled = new BooleanSetting("Reach", "Спуфит позицию для увеличения дистанции атаки")
            .setValue(false);

    SliderSettings reachSpoofDistance = new SliderSettings("Reach Distance", "Дистанция спуфа позиции")
            .setValue(3.3f).range(3.0F, 6F).visible(() -> reachEnabled.isValue());

    public Aura() {
        super("Aura", ModuleCategory.COMBAT);
        setup(aimMode, correctionType, sprintReset,
                targetType, fovSetting, attackRange, lookRange, hitChance,
                attackSetting, groundCrits, autoMace, maceHeight,
                reachEnabled, reachSpoofDistance);
    }

    public BooleanSetting getGroundCrits() {
        return groundCrits;
    }

    // ====== Lifecycle ======
    @Override
    public void deactivate() {
        super.deactivate();
        if (mc.player != null && mc.getNetworkHandler() != null) {
            targetSelector.releaseTarget();
            resetRotations();
        }
        packets.clear();
        originalSlot = -1;
        maceSwitchTimer = 0;
        needsReachRestore = false;
        reachRestorePos = null;
        reachRestoreCooldown = 0;
        lastAttackTime = 0;
        noTargetTicks = 0;
        critWaitTicks = 0;
        cachedMaceSlot = -1;
        maceSearchCooldown = 0;
        lastTarget = target;
        target = null;
        box = null;
        fakeRotate = false;
        shouldRotate = false;
    }

    private void resetRotations() {
        shouldRotate = false;
        fakeRotate = false;
        TurnsConnection controller = TurnsConnection.INSTANCE;
        controller.releaseTarget();
        controller.clear();
        if (mc.player != null) {
            controller.setRotation(new Turns(mc.player.getYaw(), mc.player.getPitch()));
            controller.setFakeRotation(null);
        }
        getSmoothMode().reset();
        controller.setRotation(null);
    }

    // ====== Packets ======
    @EventHandler
    public void onPacket(PacketEvent e) {
        if (e.getPacket() instanceof EntityStatusS2CPacket status) {
            handleEntityStatus(status);
        }
        if (!isFakeLag() || target == null || PlayerInteractionHelper.nullCheck()) return;

        Packet<?> pkt = e.getPacket();
        if (isResetPacket(pkt)) {
            setState(false);
            return;
        }
        if (e.isSend() && tickStop < 0) {
            packets.add(pkt);
            e.cancel();
        }
    }

    private void handleEntityStatus(EntityStatusS2CPacket status) {
        Entity entity = status.getEntity(mc.world);
        if (entity == null || !entity.equals(target)) return;

        if (status.getStatus() == 3) {
            resetRotations();
            target = null;
            lastTarget = null;
        }
        if (status.getStatus() == 30 && Hud.getInstance().notificationSettings.isSelected("Break Shield")) {
            Notifications.getInstance().addList(
                    Text.literal("Сломали щит игроку - ").append(entity.getDisplayName()), 5000);
        }
    }

    private boolean isResetPacket(Packet<?> pkt) {
        return pkt instanceof PlayerRespawnS2CPacket
                || pkt instanceof GameJoinS2CPacket
                || (pkt instanceof ClientStatusC2SPacket cs
                && cs.getMode() == ClientStatusC2SPacket.Mode.PERFORM_RESPAWN);
    }

    // ====== Render ======
    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (box != null && isFakeLag() && target != null) {
            Render3D.drawBox(box, ColorAssist.getClientColor(), 1);
        }
    }

    // ====== Tick ======
    @EventHandler
    public void tick(TickEvent e) {
        if (PlayerInteractionHelper.nullCheck() || mc.player == null || target == null) return;

        handleReachRestore();
        handleMaceSwitchTimer();

        tickStop--;
        if (tickStop < -1) tickStop = -1;
        if (reachRestoreCooldown > 0) reachRestoreCooldown--;
        if (maceSearchCooldown > 0) maceSearchCooldown--;

        boolean shouldFlush = false;
        if (tickStop >= 0 && !packets.isEmpty() && isFakeLag()) {
            box = mc.player.getBoundingBox();
            shouldFlush = true;
        }
        if (mc.player.distanceTo(target) > attackRange.getValue() && isFakeLag()) {
            shouldFlush = true;
        }
        if (shouldFlush) flushPackets();
    }

    private void handleReachRestore() {
        if (!needsReachRestore || reachRestorePos == null || mc.player == null) return;
        if (reachRestoreCooldown > 0) return;

        PlayerInteractionHelper.sendPacketWithOutEvent(
                new PlayerMoveC2SPacket.PositionAndOnGround(
                        reachRestorePos.x, reachRestorePos.y, reachRestorePos.z,
                        mc.player.isOnGround(), mc.player.horizontalCollision));
        needsReachRestore = false;
        reachRestorePos = null;
        reachRestoreCooldown = REACH_RESTORE_COOLDOWN_TICKS;
    }

    private void handleMaceSwitchTimer() {
        if (maceSwitchTimer > 0 && --maceSwitchTimer == 0 && originalSlot != -1) {
            InventoryToolkit.switchTo(originalSlot);
            originalSlot = -1;
        }
    }

    private void flushPackets() {
        if (packets.isEmpty()) return;
        List<Packet<?>> snapshot = new ArrayList<>();
        Packet<?> p;
        while ((p = packets.poll()) != null) snapshot.add(p);
        snapshot.forEach(PlayerInteractionHelper::sendPacketWithOutEvent);
    }

    private boolean isFakeLag() {
        return attackSetting.isSelected(FAKE_LAG);
    }

    // ====== Main Loop ======
    @EventHandler
    public void onRotationUpdate(RotationUpdateEvent e) {
        try {
            if (aimMode.isSelected("FunTime") && lumis.getInstance().getFtCheckClient() != null) {
                lumis.getInstance().getFtCheckClient().checkAndWarnFunTime();
            }
        } catch (Exception ignored) {}

        if (e.getType() == EventType.PRE) handlePreRotation();
        else if (e.getType() == EventType.POST) handlePostAttack();
    }

    private void handlePreRotation() {
        LivingEntity newTarget = updateTarget();

        if (newTarget == null) {
            noTargetTicks++;
            if (noTargetTicks > 2) {
                if (target != null || lastTarget != null || TurnsConnection.INSTANCE.getRotation() != null) {
                    resetRotations();
                }
                target = null;
                lastTarget = null;
                critWaitTicks = 0;
            }
            return;
        }

        noTargetTicks = 0;
        target = newTarget;

        if (target.isAlive()) {
            if (!aimMode.isSelected("None")) rotateToTarget(getConfig());
            lastTarget = target;
        } else {
            resetRotations();
        }
    }

    @Native(type = Native.Type.VMProtectBeginMutation)
    private void handlePostAttack() {
        if (mc.player == null || target == null || !target.isAlive()) {
            resetRotations();
            critWaitTicks = 0;
            return;
        }
        if (AutoTotem.isSwapping()) return;
        if (mc.player.isUsingItem()
                && attackSetting.isSelected(PAUSE_EATING)) return;

        boolean elytraMode = ElytraTarget.getInstance().isState() && mc.player.isGliding();

        // Проверка кулдауна — без полного кулдауна вообще не бьём
        float cooldown = mc.player.getAttackCooldownProgress(0.5f);
        if (cooldown < CRIT_COOLDOWN_THRESHOLD) return;

        if (!elytraMode && !passesCritCheck()) return;

        // Если дошли сюда — будем бить, сбрасываем счётчик ожидания
        critWaitTicks = 0;

        handleShieldBreak();
        handleAutoMace();

        if (autoMace.isValue() && target != null
                && mc.player.getY() - target.getY() >= maceHeight.getValue()
                && findMaceSlot() != -1) return;

        if (aimMode.isSelected("None")) {
            performTriggerAttack(getConfig());
            return;
        }

        float realDistance = mc.player.distanceTo(target);
        float maxDist = elytraMode ? attackRange.getValue() + 1.2f : attackRange.getValue() + 0.08f;
        if (realDistance > maxDist) return;

        if (attackSetting.isSelected(HIT_CHANCE_KEY)
                && ThreadLocalRandom.current().nextInt(100) >= hitChance.getValue()) return;

        long now = System.currentTimeMillis();
        long minDelay = (long) (50 * (1.0f / Math.max(cooldown, 0.1f)));
        if (now - lastAttackTime < Math.min(minDelay, 50)) return;

        lastAttackTime = now;
        lumis.getInstance().getAttackPerpetrator().performAttack(getConfig());
    }

    // ====== Crits — полностью переработано ======
    /**
     * Логика:
     *
     * 1. Ни один крит-режим не включён → бьём всегда (только кулдаун проверяется выше)
     *
     * 2. "Умные криты" (SMART_CRITS):
     *    - Если падаем → бьём (крит гарантирован)
     *    - Если на земле + groundCrits включён → бьём (без крита, но не стоим)
     *    - Если в воздухе но ещё не падаем (фаза подъёма прыжка) → ждём до CRIT_WAIT_TIMEOUT тиков
     *    - Если ждали слишком долго → бьём без крита чтобы не стоять
     *
     * 3. "Только криты" (ONLY_CRITS):
     *    - Строго только при падении
     *    - groundCrits разрешает бить на земле
     *    - Таймаут ожидания тоже работает
     */
    private boolean passesCritCheck() {
        if (mc.player == null) return false;

        boolean wantOnlyCrits = attackSetting.isSelected(ONLY_CRITS);
        boolean wantSmartCrits = attackSetting.isSelected(SMART_CRITS);

        // Ничего не выбрано — бьём всегда
        if (!wantOnlyCrits && !wantSmartCrits) return true;

        boolean falling = isCriticalFall();
        boolean onGround = mc.player.isOnGround();

        // Крит-падение → бьём всегда, любой режим
        if (falling) {
            critWaitTicks = 0;
            return true;
        }

        // На земле
        if (onGround) {
            critWaitTicks = 0;
            // groundCrits разрешает бить на земле при полном кулдауне
            return groundCrits.isValue();
        }

        // В воздухе но не падаем (фаза подъёма прыжка)
        critWaitTicks++;

        // Ждали слишком долго — бьём чтобы не тупить
        if (critWaitTicks >= CRIT_WAIT_TIMEOUT) {
            critWaitTicks = 0;
            return true;
        }

        // Ещё ждём падения
        return false;
    }

    /**
     * Проверка реального крит-падения.
     * Ванилла: fallDistance > 0, !onGround, !climbing, !inWater, !blind, !onVehicle, !sprinting_with_riptide
     * Мы упрощаем: fallDistance > порог + базовые проверки
     */
    private boolean isCriticalFall() {
        if (mc.player == null) return false;
        return mc.player.fallDistance > CRIT_MIN_FALL_DISTANCE
                && !mc.player.isOnGround()
                && !mc.player.isClimbing()
                && !mc.player.isTouchingWater()
                && !mc.player.isInLava()
                && !mc.player.hasVehicle();
    }

    // ====== Shield Break ======
    private void handleShieldBreak() {
        if (!attackSetting.isSelected(SHIELD_BREAK) && !attackSetting.isSelected(SHIELD_LOWER)) return;
        if (!(target instanceof PlayerEntity player) || !player.isBlocking()) return;

        int axeSlot = InventoryToolkit.getAxe();
        if (axeSlot == -1) return;

        if (originalSlot == -1) originalSlot = mc.player.getInventory().selectedSlot;
        InventoryToolkit.switchTo(axeSlot);
        maceSwitchTimer = 1;
    }

    // ====== Target ======
    private LivingEntity updateTarget() {
        TargetFinder.EntityFilter filter = new TargetFinder.EntityFilter(targetType.getSelected());
        float range = ElytraTarget.getInstance().isState()
                ? 30.0f
                : attackRange.getValue() + RANGE_MARGIN + lookRange.getValue();

        targetSelector.searchTargets(mc.world.getEntities(), range,
                fovSetting.getValue(), attackSetting.isSelected(THROUGH_WALLS));
        targetSelector.validateTarget(filter::isValid);
        return targetSelector.getCurrentTarget();
    }

    // ====== Rotations ======
    @Native(type = Native.Type.VMProtectBeginMutation)
    private void rotateToTarget(StrikerConstructor.AttackPerpetratorConfigurable config) {
        if (target == null || !target.isAlive() || mc.player == null) {
            TurnsConnection.INSTANCE.setRotation(null);
            TurnsConnection.INSTANCE.clear();
            return;
        }

        TurnsConnection controller = TurnsConnection.INSTANCE;
        Turns.VecRotation rotation = new Turns.VecRotation(config.getAngle(), config.getAngle().toVector());

        if (fakeRotate) {
            FakeAngle fake = new FakeAngle();
            controller.setFakeRotation(
                    fake.limitAngleChange(controller.getRotation(), rotation.getAngle(), rotation.getVec(), target));
        } else {
            controller.setFakeRotation(null);
        }
        fakeRotate = false;

        if (!aimMode.isSelected("None")) {
            controller.rotateTo(rotation, target, 1, getRotationConfig(),
                    TaskPriority.HIGH_IMPORTANCE_1, this);
        }
    }

    // ====== Attack Config ======
    @NonFinal public boolean elytraStateForward = false;

    public StrikerConstructor.AttackPerpetratorConfigurable getConfig() {
        float baseRange = attackRange.getValue() + RANGE_MARGIN;
        float distanceToTarget = mc.player.distanceTo(target);

        float dynamicRange = distanceToTarget > baseRange - 0.3f
                ? Math.max(baseRange * 0.88f, baseRange - 0.25f)
                : baseRange;

        Vec3d eyePos = mc.player.getEyePos();
        Box targetBox = target.getBoundingBox();

        if (aimMode.isSelected("HvH")) {
            return buildHvHConfig(eyePos, targetBox, distanceToTarget, dynamicRange);
        }
        return buildStandardConfig(eyePos, targetBox, distanceToTarget, dynamicRange);
    }

    private StrikerConstructor.AttackPerpetratorConfigurable buildHvHConfig(
            Vec3d eyePos, Box targetBox, float distanceToTarget, float dynamicRange) {

        Vec3d bestPoint = new Vec3d(
                MathHelper.clamp(eyePos.x, targetBox.minX, targetBox.maxX),
                MathHelper.clamp(eyePos.y,
                        targetBox.minY + target.getHeight() * 0.15,
                        targetBox.maxY - target.getHeight() * 0.15),
                MathHelper.clamp(eyePos.z, targetBox.minZ, targetBox.maxZ));

        double hvhPredict = HVH_BASE_PREDICT + distanceToTarget * HVH_DISTANCE_FACTOR;

        double dx = clampVelocity(target.getX() - target.prevX);
        double dy = clampVerticalVelocity(target.getY() - target.prevY);
        double dz = clampVelocity(target.getZ() - target.prevZ);

        Vec3d computedPoint = bestPoint.add(
                dx * hvhPredict,
                dy * HVH_VERTICAL_FACTOR,
                dz * hvhPredict);

        if (eyePos.distanceTo(computedPoint) > dynamicRange) computedPoint = bestPoint;

        return new StrikerConstructor.AttackPerpetratorConfigurable(
                target, MathAngle.fromVec3d(computedPoint.subtract(eyePos)),
                dynamicRange, attackSetting.getSelected(), aimMode, targetBox);
    }

    private StrikerConstructor.AttackPerpetratorConfigurable buildStandardConfig(
            Vec3d eyePos, Box targetBox, float distanceToTarget, float dynamicRange) {

        Pair<Vec3d, Box> pointData = pointFinder.computeVector(
                target, dynamicRange, TurnsConnection.INSTANCE.getRotation(),
                getSmoothMode().randomValue(), attackSetting.isSelected(THROUGH_WALLS));

        Vec3d basePoint = pointData.getLeft();
        Box hitbox = pointData.getRight();

        Vec3d predicted = predictTargetPosition(basePoint, distanceToTarget);

        if (mc.player.isGliding() && target.isGliding()) {
            Vec3d targetVel = target.getVelocity();
            if (targetVel.horizontalLength() > 0.35) {
                Vec3d offset = targetVel.multiply(1.1);
                predicted = target.getPos().add(offset).add(0, target.getHeight() * HIT_HEIGHT_GLIDING, 0);
                hitbox = target.getBoundingBox().offset(offset);
            }
        }

        if (eyePos.distanceTo(predicted) > dynamicRange + 0.1) {
            predicted = target.getPos().add(0, target.getHeight() * 0.6, 0);
        }

        return new StrikerConstructor.AttackPerpetratorConfigurable(
                target, MathAngle.fromVec3d(predicted.subtract(eyePos)),
                dynamicRange, attackSetting.getSelected(), aimMode, hitbox);
    }

    private Vec3d predictTargetPosition(Vec3d basePoint, float distanceToTarget) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        double dx = clampVelocity(target.getX() - target.prevX);
        double dz = clampVelocity(target.getZ() - target.prevZ);
        double dy = clampVerticalVelocity(target.getY() - target.prevY);

        double horizSpeed = Math.hypot(dx, dz);
        double distanceFactor = distanceToTarget > 3.2f ? 0.75 : 1.0;

        double velocityPredict = 1.0;
        if (ElytraTarget.getInstance().isState() && mc.player.isGliding()) {
            velocityPredict = MathHelper.clamp(mc.player.getVelocity().horizontalLength() * 2.5, 1.0, 4.5);
        }

        double predictFactor = (0.95 + horizSpeed * (0.85 + rng.nextDouble() * 0.2) * distanceFactor
                + distanceToTarget * 0.03) * velocityPredict;
        double maxPredict = mc.player.isGliding() ? PREDICT_MAX_GLIDING : PREDICT_MAX_GROUND;
        predictFactor = MathHelper.clamp(predictFactor, PREDICT_MIN, maxPredict);

        double hitHeight;
        if (mc.player.isGliding()) {
            hitHeight = HIT_HEIGHT_GLIDING;
        } else if (distanceToTarget > FAR_DISTANCE_THRESHOLD) {
            hitHeight = HIT_HEIGHT_FAR + rng.nextDouble() * HIT_HEIGHT_FAR_JITTER;
        } else {
            hitHeight = HIT_HEIGHT_NEAR + rng.nextDouble() * HIT_HEIGHT_NEAR_JITTER;
        }

        float jitterAmp = 0.03f;
        return basePoint.add(
                dx * predictFactor + (rng.nextFloat() - 0.5f) * jitterAmp,
                dy * VERTICAL_PREDICT_FACTOR + (target.getHeight() * hitHeight - target.getHeight() / 2)
                        + (rng.nextFloat() - 0.5f) * jitterAmp,
                dz * predictFactor + (rng.nextFloat() - 0.5f) * jitterAmp);
    }

    private double clampVelocity(double v) {
        return MathHelper.clamp(v, -MAX_HORIZONTAL_VELOCITY, MAX_HORIZONTAL_VELOCITY);
    }

    private double clampVerticalVelocity(double v) {
        return MathHelper.clamp(v, -MAX_VERTICAL_VELOCITY, MAX_VERTICAL_VELOCITY);
    }

    // ====== Rotation Config ======
    @Native(type = Native.Type.VMProtectBeginMutation)
    public TurnsConfig getRotationConfig() {
        boolean visibleCorrection = !correctionType.isSelected("Not visible");
        boolean freeCorrection = !aimMode.isSelected("Legit") && correctionType.isSelected("Free");
        boolean targetCorrection = correctionType.isSelected("Target");

        if (TargetStrafe.getInstance().isState()
                && TargetStrafe.getInstance().isMode("Grim") && target != null) {
            freeCorrection = false;
            targetCorrection = false;
        }

        if (targetCorrection) return new TurnsConfig(getSmoothMode(), true, false);
        return new TurnsConfig(getSmoothMode(), visibleCorrection, freeCorrection);
    }

    // ====== Trigger Attack ======
    private void performTriggerAttack(StrikerConstructor.AttackPerpetratorConfigurable config) {
        StrikeManager attackHandler = lumis.getInstance().getAttackPerpetrator().getAttackHandler();
        if (!attackHandler.canAttack(config, 0)) return;

        if (mc.player.distanceTo(config.getTarget()) <= config.getMaximumRange() + 0.08f) {
            mc.interactionManager.attackEntity(mc.player, config.getTarget());
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    // ====== Smooth Mode ======
    public RotateConstructor getSmoothMode() {
        if (mc.player != null && mc.player.isGliding()) return glidingMode;
        return smoothModeCache.computeIfAbsent(aimMode.getSelected(), k -> switch (k) {
            case "SpookytimeDuel" -> new SpookytimeDuel();
            case "FunTime" -> new FTAngle();
            case "HolyWorld" -> new HWAngle();
            case "HvH" -> new HAngle();
            case "Snap" -> new SnapAngle();
            case "Spooky" -> new SpookyAngle();
            case "ReallyWorld" -> new RWAngle();
            case "Vodkacraft" -> new VodkacraftAngle();
            case "Legit" -> new LegitAngle();
            case "Matrix" -> new MatrixAngle();
            default -> new LinearConstructor();
        });
    }

    // ====== AutoMace ======
    private int findMaceSlot() {
        if (mc.player == null) return -1;
        if (maceSearchCooldown > 0) return cachedMaceSlot;
        maceSearchCooldown = 10;

        cachedMaceSlot = -1;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof MaceItem) {
                if (i < 9) { cachedMaceSlot = i; return i; }
                if (cachedMaceSlot == -1) cachedMaceSlot = i;
            }
        }
        return cachedMaceSlot;
    }

    private void handleAutoMace() {
        if (!autoMace.isValue() || target == null || mc.player == null) return;
        if (mc.player.getY() - target.getY() < maceHeight.getValue()) return;
        if (mc.player.distanceTo(target) > attackRange.getValue() + RANGE_MARGIN + 1.0f) return;
        if (mc.player.getAttackCooldownProgress(0.5f) < CRIT_COOLDOWN_THRESHOLD) return;

        int maceSlot = findMaceSlot();
        if (maceSlot == -1) return;

        if (originalSlot == -1) originalSlot = mc.player.getInventory().selectedSlot;

        if (mc.player.getInventory().selectedSlot != maceSlot) {
            if (maceSlot < 9) {
                InventoryToolkit.switchTo(maceSlot);
            } else {
                InventoryToolkit.quickMoveFromTo(maceSlot, originalSlot);
                InventoryToolkit.switchTo(originalSlot);
            }
        }

        if (mc.interactionManager != null) {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
            maceSwitchTimer = 2;
        }
    }

    // ====== Reach ======
    public float reach() {
        if (!reachEnabled.isValue() || target == null || mc.player == null) {
            return attackRange.getValue() + RANGE_MARGIN;
        }

        float baseRange = attackRange.getValue() + RANGE_MARGIN;
        float maxReach = reachSpoofDistance.getValue();
        float distanceToTarget = mc.player.distanceTo(target);

        if (distanceToTarget <= maxReach && distanceToTarget > baseRange) {
            if (reachRestoreCooldown > 0) return baseRange;

            Vec3d playerPos = mc.player.getPos();
            Vec3d direction = target.getPos().subtract(playerPos).normalize();
            double moveDistance = Math.min(distanceToTarget - baseRange + 0.1, MAX_SPOOF_STEP);
            if (moveDistance <= 0) return baseRange;

            Vec3d spoofedPos = playerPos.add(direction.multiply(moveDistance));

            PlayerInteractionHelper.sendPacketWithOutEvent(
                    new PlayerMoveC2SPacket.PositionAndOnGround(
                            spoofedPos.x, spoofedPos.y, spoofedPos.z,
                            mc.player.isOnGround(), mc.player.horizontalCollision));

            needsReachRestore = true;
            reachRestorePos = playerPos;
            return maxReach;
        }

        return baseRange;
    }

    public float getReachSpoofDistance() {
        return reachEnabled.isValue() ? reachSpoofDistance.getValue() : 0;
    }
}