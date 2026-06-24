package fun.lumis.features.impl.combat;

import antidaunleak.api.annotation.Native;
import fun.lumis.events.player.RotationUpdateEvent;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.BooleanSetting;
import fun.lumis.features.module.setting.implement.MultiSelectSetting;
import fun.lumis.features.module.setting.implement.SelectSetting;
import fun.lumis.features.module.setting.implement.SliderSettings;
import fun.lumis.utils.client.Instance;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.utils.client.managers.event.types.EventType;
import fun.lumis.utils.features.aura.point.MultiPoint;
import fun.lumis.utils.features.aura.rotations.constructor.LinearConstructor;
import fun.lumis.utils.features.aura.striking.StrikerConstructor;
import fun.lumis.utils.features.aura.target.TargetFinder;
import fun.lumis.utils.features.aura.utils.MathAngle;
import fun.lumis.utils.features.aura.warp.Turns;
import fun.lumis.utils.features.aura.warp.TurnsConnection;
import fun.lumis.utils.interactions.interact.PlayerInteractionHelper;
import fun.lumis.lumis;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Objects;

public class TriggerBot extends Module {

    private static final float RANGE_MARGIN = 0.253F;
    private final TargetFinder targetSelector = new TargetFinder();
    private final MultiPoint pointFinder = new MultiPoint();
    public LivingEntity target;
    private long lastAttackTime;

    public SelectSetting pvpMode = new SelectSetting("Режим", "Версия PvP")
            .value("1.9+", "1.8.9").selected("1.9+");

    public SliderSettings cps = new SliderSettings("CPS", "Скорость ударов")
            .setValue(10).range(1, 20).visible(() -> pvpMode.isSelected("1.8.9"));

    public SliderSettings attackRange = new SliderSettings("Дистанция удара", "Дальность атаки")
            .setValue(3.0F).range(1.0F, 6.0F);

    public MultiSelectSetting targetType = new MultiSelectSetting("Тип таргета", "Цели")
            .value("Players", "Mobs", "Animals", "Armor Stand")
            .selected("Players");

    public MultiSelectSetting attackSetting = new MultiSelectSetting("Параметры атаки", "Настройки")
            .value("Только криты", "Сквозь стены", "Шанс попадания")
            .selected("Только криты");

    public SliderSettings hitChance = new SliderSettings("Шанс удара в %", "Шанс")
            .setValue(100).range(1F, 100F).visible(() -> attackSetting.isSelected("Шанс попадания"));

    public SelectSetting sprintReset = new SelectSetting("Сброс спринта", "Выбор сброса")
            .value("Legit", "Packet", "None").selected("Legit").visible(() -> pvpMode.isSelected("1.9+"));

    public BooleanSetting smartCrits = new BooleanSetting("Удары на земле", "Криты на земле")
            .setValue(true).visible(() -> attackSetting.isSelected("Только криты"));

    public TriggerBot() {
        super("TriggerBot", ModuleCategory.COMBAT);
        setup(pvpMode, cps, attackRange, targetType, attackSetting, hitChance, sprintReset, smartCrits);
    }

    public static TriggerBot getInstance() {
        return Instance.get(TriggerBot.class);
    }

    private LivingEntity updateTarget() {
        if (mc.world == null) return null;
        TargetFinder.EntityFilter filter = new TargetFinder.EntityFilter(targetType.getSelected());
        float range = attackRange.getValue() + RANGE_MARGIN;
        targetSelector.searchTargets(mc.world.getEntities(), range, 360, attackSetting.isSelected("Сквозь стены"));
        targetSelector.validateTarget(filter::isValid);
        return targetSelector.getCurrentTarget();
    }

    @EventHandler
    @Native(type = Native.Type.VMProtectBeginMutation)
    public void onRotationUpdate(RotationUpdateEvent e) {
        if (PlayerInteractionHelper.nullCheck() || mc.player == null) return;

        if (e.getType() == EventType.PRE) {
            target = updateTarget();
        } else if (e.getType() == EventType.POST && target != null) {

            if (mc.targetedEntity == null || !mc.targetedEntity.equals(target)) return;

            long currentTime = System.currentTimeMillis();

            if (pvpMode.isSelected("1.8.9")) {
                long delay = (long) (1000.0 / Math.max(1, cps.getValue()));
                if (currentTime - lastAttackTime >= delay) {
                    if (mc.interactionManager != null) {
                        mc.interactionManager.attackEntity(mc.player, target);
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }
                    lastAttackTime = currentTime;
                }
            } else {
                StrikerConstructor.AttackPerpetratorConfigurable config = getConfig();
                if (config == null) return;

                if (lumis.getInstance().getAttackPerpetrator().getAttackHandler().canAttack(config, 0)) {
                    lumis.getInstance().getAttackPerpetrator().performAttack(config);
                    lastAttackTime = currentTime;
                }
            }
        }
    }

    public StrikerConstructor.AttackPerpetratorConfigurable getConfig() {
        if (mc.player == null || target == null) return null;

        float baseRange = attackRange.getValue() + RANGE_MARGIN;
        Pair<Vec3d, Box> pointData = pointFinder.computeVector(
                target,
                baseRange,
                TurnsConnection.INSTANCE.getRotation(),
                new LinearConstructor().randomValue(),
                attackSetting.isSelected("Сквозь стены")
        );

        Vec3d eyePos = mc.player.getEyePos();
        if (eyePos == null) return null;

        Turns angle = MathAngle.fromVec3d(pointData.getLeft().subtract(eyePos));

        return new StrikerConstructor.AttackPerpetratorConfigurable(
                target,
                angle,
                baseRange,
                attackSetting.getSelected(),
                null,
                pointData.getRight()
        );
    }
}