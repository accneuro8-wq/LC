package fun.lumis.features.impl.movement;

import fun.lumis.events.player.TickEvent;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.SelectSetting;
import fun.lumis.features.module.setting.implement.SliderSettings;
import fun.lumis.utils.client.managers.event.EventHandler;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LongJump extends Module {

    private static final double Y_POWER_DIVISOR = 20.0;
    private static final double XZ_SPEED_DIVISOR = 10.0;
    private static final double SOUL_SAND_Y_MULTIPLIER = 0.8;

    private static final int BOOST_TICKS = 4;
    private static final double MAX_VELOCITY_PER_TICK = 0.42;
    private static final double MAX_TOTAL_BOOST = MAX_VELOCITY_PER_TICK * BOOST_TICKS;

    final SelectSetting modeSetting = new SelectSetting("Режим", "Алгоритм")
            .value("Boat", "Shulker Screen", "Soul Sand", "Slime Block")
            .selected("Boat");

    final SliderSettings power = new SliderSettings("Сила Y", "Мощность вверх")
            .setValue(8.0f).range(1.0f, 50.0f);

    final SliderSettings speed = new SliderSettings("Сила X/Z", "Скорость вперед")
            .setValue(4.0f).range(0.0f, 50.0f);

    int boostTicksLeft = 0;
    double stepX = 0;
    double stepY = 0;
    double stepZ = 0;

    boolean boosted = false;
    boolean wasOnGround = false;
    boolean wasInShulkerScreen = false;
    double lastVelocityY = 0;

    public LongJump() {
        super("LongJump", "Long Jump", ModuleCategory.MOVEMENT);
        setup(modeSetting, power, speed);
    }

    @Override
    public void activate() {
        resetState();
        if (mc.player != null) {
            wasOnGround = mc.player.isOnGround();
            lastVelocityY = mc.player.getVelocity().y;
        }
    }

    @Override
    public void deactivate() {
        resetState();
    }

    private void resetState() {
        boosted = false;
        wasOnGround = false;
        wasInShulkerScreen = false;
        boostTicksLeft = 0;
        stepX = 0;
        stepY = 0;
        stepZ = 0;
        lastVelocityY = 0;
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        boolean onGround = mc.player.isOnGround();
        boolean inShulkerScreen = mc.currentScreen instanceof ShulkerBoxScreen;

        if (boostTicksLeft > 0) {
            mc.player.addVelocity(stepX, stepY, stepZ);
            boostTicksLeft--;
            if (onGround && boostTicksLeft <= BOOST_TICKS - 2) {
                resetState();
            }
            lastVelocityY = mc.player.getVelocity().y;
            wasOnGround = onGround;
            wasInShulkerScreen = inShulkerScreen;
            return;
        }

        if (shouldResetBoost(onGround, inShulkerScreen)) {
            boosted = false;
        }

        if (!boosted) {
            double yBoost = power.getValue() / Y_POWER_DIVISOR;
            double xzBoost = speed.getValue() / XZ_SPEED_DIVISOR;

            String selected = modeSetting.getSelected();
            if (selected.equals("Boat")) {
                handleBoat(yBoost, xzBoost);
            } else if (selected.equals("Shulker Screen")) {
                handleShulker(yBoost, xzBoost, inShulkerScreen);
            } else if (selected.equals("Soul Sand")) {
                handleSoulSand(yBoost, xzBoost);
            } else if (selected.equals("Slime Block")) {
                handleSlimeBlock(yBoost, xzBoost, onGround);
            }
        }

        lastVelocityY = mc.player.getVelocity().y;
        wasOnGround = onGround;
        wasInShulkerScreen = inShulkerScreen;
    }

    private boolean shouldResetBoost(boolean onGround, boolean inShulkerScreen) {
        if (modeSetting.isSelected("Shulker Screen")) {
            return wasInShulkerScreen && !inShulkerScreen;
        }
        if (modeSetting.isSelected("Slime Block")) {
            return onGround && wasOnGround;
        }
        return !wasOnGround && onGround;
    }

    private void handleBoat(double y, double xz) {
        if (mc.player.isOnGround() || !wasOnGround) return;
        BoatEntity boat = findNearestBoat();
        if (boat != null && mc.player.getY() >= boat.getY() - 0.5) {
            startBoost(y, xz);
        }
    }

    private void handleShulker(double y, double xz, boolean inScreen) {
        if (inScreen) {
            startBoost(y, xz);
        }
    }

    private void handleSoulSand(double y, double xz) {
        boolean canBoost = !mc.player.isOnGround()
                && (mc.player.isTouchingWater() || mc.player.isInSneakingPose());
        if (canBoost) {
            startBoost(y * SOUL_SAND_Y_MULTIPLIER, xz);
        }
    }

    private void handleSlimeBlock(double y, double xz, boolean onGround) {
        boolean wasFalling = lastVelocityY < -0.1;

        boolean justBounced = !onGround && wasOnGround && mc.player.getVelocity().y > 0.1;

        boolean standingOnSlime = onGround && !wasOnGround && wasFalling;

        if (justBounced && isSlimeBelow()) {
            startBoost(y, xz);
            return;
        }

        if (standingOnSlime && isSlimeBelow()) {
            startBoost(y, xz);
        }
    }

    private boolean isSlimeBelow() {
        BlockPos pos = mc.player.getBlockPos();

        if (mc.world.getBlockState(pos.down()).isOf(Blocks.SLIME_BLOCK)) return true;
        if (mc.world.getBlockState(pos).isOf(Blocks.SLIME_BLOCK)) return true;

        double playerY = mc.player.getY();
        double blockY = Math.floor(playerY);
        if (playerY - blockY < 0.1) {
            BlockPos deepPos = new BlockPos(pos.getX(), (int) blockY - 1, pos.getZ());
            if (mc.world.getBlockState(deepPos).isOf(Blocks.SLIME_BLOCK)) return true;
        }

        Box feet = mc.player.getBoundingBox().contract(0.01, 0, 0.01).offset(0, -0.01, 0);
        int minX = (int) Math.floor(feet.minX);
        int maxX = (int) Math.floor(feet.maxX);
        int minZ = (int) Math.floor(feet.minZ);
        int maxZ = (int) Math.floor(feet.maxZ);
        int checkY = (int) Math.floor(feet.minY);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (mc.world.getBlockState(new BlockPos(x, checkY, z)).isOf(Blocks.SLIME_BLOCK)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void startBoost(double totalY, double totalXZ) {
        totalXZ = Math.min(totalXZ, MAX_TOTAL_BOOST);
        totalY = Math.min(totalY, MAX_TOTAL_BOOST);

        Vec3d look = mc.player.getRotationVector();
        double horizontalDist = Math.sqrt(look.x * look.x + look.z * look.z);
        if (horizontalDist < 0.001) horizontalDist = 1.0;

        double normalX = look.x / horizontalDist;
        double normalZ = look.z / horizontalDist;

        double rawStepX = normalX * (totalXZ / BOOST_TICKS);
        double rawStepY = totalY / BOOST_TICKS;
        double rawStepZ = normalZ * (totalXZ / BOOST_TICKS);

        this.stepX = clamp(rawStepX, MAX_VELOCITY_PER_TICK);
        this.stepY = clamp(rawStepY, MAX_VELOCITY_PER_TICK);
        this.stepZ = clamp(rawStepZ, MAX_VELOCITY_PER_TICK);

        this.boostTicksLeft = BOOST_TICKS;
        this.boosted = true;
    }

    private double clamp(double value, double limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    private BoatEntity findNearestBoat() {
        Box searchBox = mc.player.getBoundingBox().expand(1.5);
        List<BoatEntity> boats = mc.world.getEntitiesByClass(BoatEntity.class, searchBox, Entity::isAlive);
        BoatEntity nearest = null;
        double minDistSq = Double.MAX_VALUE;
        for (BoatEntity boat : boats) {
            double distSq = mc.player.squaredDistanceTo(boat);
            if (distSq < minDistSq) {
                minDistSq = distSq;
                nearest = boat;
            }
        }
        return nearest;
    }
}