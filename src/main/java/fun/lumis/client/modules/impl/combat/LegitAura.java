package fun.lumis.client.modules.impl.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.passive.CodEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import fun.lumis.Lumis;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.storages.implement.RotationStorage;
import fun.lumis.api.utils.rotate.Rotation;
import fun.lumis.api.utils.rotate.RotationUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.impl.combat.components.interpolation.BestPoint;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ListSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static net.minecraft.util.math.MathHelper.wrapDegrees;

public class LegitAura extends Module {

    public static LegitAura INSTANCE = new LegitAura();

    private final ListSetting targets = new ListSetting("Таргеты",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("В броне", true),
            new BooleanSetting("Без брони", true),
            new BooleanSetting("Невидимки", true),
            new BooleanSetting("Мобы", false)
    );

    private final FloatSetting range = new FloatSetting("Дистанция", 4f, 1f, 8f, 0.1f);
    private final FloatSetting fov = new FloatSetting("Угол наводки", 60f, 5f, 180f, 1f);
    private final FloatSetting speed = new FloatSetting("Скорость", 30f, 1f, 180f, 1f);
    private final BooleanSetting silent = new BooleanSetting("Тихие повороты", false);
    private final BooleanSetting onlyWeapon = new BooleanSetting("Только с оружием", true);
    private final BooleanSetting onlyWhenAttacking = new BooleanSetting("Только при атаке", false);

    private LivingEntity target;

    public LegitAura() {
        super("LegitAura", "Плавно доводит прицел до таргета как аим-ассист", ModuleCategory.COMBAT);
        addSettings(targets, range, fov, speed, silent, onlyWeapon, onlyWhenAttacking);
    }

    @EventLink
    public void onTick(EventUpdate e) {
        if (mc.player == null || mc.world == null) {
            target = null;
            return;
        }

        if (onlyWeapon.isState() && !isWeapon()) {
            target = null;
            return;
        }

        if (onlyWhenAttacking.isState() && !mc.options.attackKey.isPressed()) {
            target = null;
            return;
        }

        updateTarget();
        if (target == null) return;

        Vec2f desired = getDesiredRotation(target);
        float assistSpeed = speed.getValue().floatValue();

        RotationStorage.update(
                new Rotation(desired.x, desired.y),
                assistSpeed, assistSpeed,
                45f, 45f,
                2, 1,
                !silent.isState()
        );
    }

    private void updateTarget() {
        if (target != null && isValidTarget(target)) {
            return;
        }
        target = findTarget();
    }

    private LivingEntity findTarget() {
        List<LivingEntity> entities = new ArrayList<>();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!isValidTarget(living)) continue;
            entities.add(living);
        }

        if (entities.isEmpty()) return null;

        entities.sort(Comparator.comparingDouble(this::getAngleTo));
        return entities.get(0);
    }

    private double getAngleTo(LivingEntity entity) {
        Vec2f rot = getDesiredRotation(entity);
        double dy = Math.abs(wrapDegrees(rot.x - mc.player.getYaw()));
        double dp = Math.abs(rot.y - mc.player.getPitch());
        return Math.hypot(dy, dp);
    }

    private Vec2f getDesiredRotation(LivingEntity entity) {
        Vec3d point = BestPoint.getNearestPoint(entity);
        if (point == null) {
            point = entity.getBoundingBox().getCenter();
        }
        return RotationUtils.getRotations(point);
    }

    private boolean isWithinFov(LivingEntity entity) {
        return getAngleTo(entity) <= fov.getValue().floatValue();
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == null || entity == mc.player) return false;
        if (!entity.isAlive() || entity.getHealth() <= 0) return false;
        if (entity instanceof ArmorStandEntity) return false;
        if (entity instanceof IronGolemEntity || entity instanceof BatEntity) return false;
        if (AntiBot.checkBot(entity)) return false;

        if (entity instanceof PlayerEntity player) {
            if (!targets.is("Игроки")) return false;
            if (Lumis.INSTANCE.friendStorage.isFriend(player.getName().getString())) return false;
            if (player.hasStatusEffect(StatusEffects.INVISIBILITY) && !targets.is("Невидимки")) return false;

            boolean naked = isNaked(player);
            if (naked && !targets.is("Без брони")) return false;
            if (!naked && !targets.is("В броне")) return false;
        } else if (entity instanceof PassiveEntity || entity instanceof CodEntity) {
            return false;
        } else if (entity instanceof HostileEntity) {
            if (!targets.is("Мобы")) return false;
        } else {
            return false;
        }

        Vec3d point = BestPoint.getNearestPoint(entity);
        if (point == null) point = entity.getBoundingBox().getCenter();
        if (mc.player.getEyePos().distanceTo(point) > range.getValue().floatValue()) return false;

        return isWithinFov(entity);
    }

    private boolean isNaked(PlayerEntity player) {
        for (ItemStack armorStack : player.getArmorItems()) {
            if (!armorStack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean isWeapon() {
        Item item = mc.player.getMainHandStack().getItem();
        return item != Items.AIR && (item instanceof SwordItem
                || item instanceof PickaxeItem
                || item instanceof AxeItem
                || item instanceof HoeItem
                || item instanceof ShovelItem
                || item instanceof MaceItem
                || item == Items.MACE);
    }

    public LivingEntity getTarget() {
        return target;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        target = null;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        target = null;
    }
}
