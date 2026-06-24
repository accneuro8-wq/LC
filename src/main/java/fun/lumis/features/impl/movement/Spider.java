package fun.lumis.features.impl.movement;

import fun.lumis.events.player.PostTickEvent;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.SelectSetting;
import fun.lumis.features.module.setting.implement.SliderSettings;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.utils.interactions.interact.PlayerInteractionHelper;
import fun.lumis.utils.interactions.inv.InventoryTask;
import fun.lumis.utils.math.time.StopWatch;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PaneBlock;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@Setter
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Spider extends Module {

    SelectSetting mode = new SelectSetting("Режим", "Выбирает режим обхода")
            .value("Water Bucket", "Glass Pane", "Grim")
            .selected("Water Bucket");

    SliderSettings climbSpeed = new SliderSettings("Скорость", "Скорость подъема")
            .range(0.1f, 1.0f).setValue(0.4f)
            .visible(() -> mode.isSelected("Grim"));

    SliderSettings glassSpeed = new SliderSettings("Скорость панели", "Скорость движения при Glass Pane")
            .range(0.05f, 0.3f).setValue(0.1f)
            .visible(() -> mode.isSelected("Glass Pane"));

    SliderSettings jumpDelay = new SliderSettings("Задержка прыжка", "Задержка между прыжками (мс)")
            .range(50f, 500f).setValue(200f)
            .visible(() -> mode.isSelected("Water Bucket"));

    StopWatch timer = new StopWatch();
    StopWatch waterCollectTimer = new StopWatch();

    @NonFinal int waterStage = 0;
    @NonFinal boolean grimJumped = false;
    @NonFinal double grimJumpY = 0;
    @NonFinal int grimGroundTicks = 0;

    public Spider() {
        super("Spider", ModuleCategory.MOVEMENT);
        setup(mode, climbSpeed, glassSpeed, jumpDelay);
    }

    @Override
    public void activate() {
        waterStage = 0;
        grimJumped = false;
        grimJumpY = 0;
        grimGroundTicks = 0;
        timer.reset();
        waterCollectTimer.reset();
    }

    @Override
    public void deactivate() {
        waterStage = 0;
        grimJumped = false;
        grimGroundTicks = 0;
    }

    @EventHandler
    public void onPostTick(PostTickEvent e) {
        if (mc.player == null || mc.world == null) return;

        String selected = mode.getSelected();

        if (selected.equals("Glass Pane")) {
            handleGlassPane();
            return;
        }

        if (selected.equals("Water Bucket")) {
            handleWaterBucket();
            return;
        }

        if (selected.equals("Grim")) {
            handleGrim();
        }
    }

    // ========================
    // WATER BUCKET
    // ========================

    private void handleWaterBucket() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!mc.player.horizontalCollision) {
            waterStage = 0;
            return;
        }

        // Проверяем что перед нами действительно стена
        BlockPos wallPos = mc.player.getBlockPos().offset(mc.player.getHorizontalFacing());
        if (mc.world.getBlockState(wallPos).isAir()) return;

        int waterSlot = findHotbarItem(Items.WATER_BUCKET);
        int bucketSlot = findHotbarItem(Items.BUCKET);

        if (waterStage == 0) {
            // Стадия 0: Ставим воду под ноги и прыгаем
            if (!timer.finished((long) jumpDelay.getValue())) return;
            if (waterSlot == -1) return;

            int oldSlot = mc.player.getInventory().selectedSlot;

            // Ставим воду — смотрим вниз
            InventoryTask.switchTo(waterSlot);
            sendLookPacket(90.0f);
            PlayerInteractionHelper.interactItem(Hand.MAIN_HAND);
            InventoryTask.switchTo(oldSlot);

            // Прыгаем (ванильная скорость прыжка)
            mc.player.setVelocity(mc.player.getVelocity().x, 0.42, mc.player.getVelocity().z);
            mc.player.fallDistance = 0;

            waterStage = 1;
            timer.reset();

        } else if (waterStage == 1) {
            // Стадия 1: Собираем воду обратно
            if (timer.finished(600)) {
                // Таймаут — сбрасываем
                waterStage = 0;
                return;
            }

            // Ждём немного перед сбором
            if (!timer.finished(120)) return;

            // Ищем ведро (пустое) — после использования water_bucket оно стало пустым
            bucketSlot = findHotbarItem(Items.BUCKET);
            if (bucketSlot == -1) {
                waterStage = 0;
                return;
            }

            // Проверяем что под ногами или рядом есть вода
            BlockPos below = mc.player.getBlockPos().down();
            BlockPos atFeet = mc.player.getBlockPos();
            boolean hasWater = mc.world.getBlockState(below).getBlock() == Blocks.WATER
                    || mc.world.getBlockState(atFeet).getBlock() == Blocks.WATER;

            if (!hasWater && !timer.finished(400)) return; // Ждём ещё если воды нет

            int oldSlot = mc.player.getInventory().selectedSlot;

            InventoryTask.switchTo(bucketSlot);
            sendLookPacket(90.0f);
            PlayerInteractionHelper.interactItem(Hand.MAIN_HAND);
            InventoryTask.switchTo(oldSlot);

            waterStage = 0;
            timer.reset();
        }
    }

    // ========================
    // GLASS PANE
    // ========================

    private void handleGlassPane() {
        if (mc.player == null || mc.world == null) return;

        BlockPos playerPos = mc.player.getBlockPos();
        Direction facing = mc.player.getHorizontalFacing();
        BlockPos frontPos = playerPos.offset(facing);

        // Расширенная проверка панелей: перед нами, на нашей позиции,
        // под ногами, над головой, а также перед нами вверх/вниз
        boolean hasPane = isGlassPane(frontPos)
                || isGlassPane(playerPos)
                || isGlassPane(playerPos.down())
                || isGlassPane(frontPos.down())
                || isGlassPane(playerPos.up())
                || isGlassPane(frontPos.up());

        if (!hasPane) return;

        // Правильный расчёт скорости с учётом strafe (movementForward + movementSideways)
        float forward = mc.player.input.movementForward;
        float strafe = mc.player.input.movementSideways;

        if (forward != 0 || strafe != 0) {
            double speed = glassSpeed.getValue();
            float yaw = mc.player.getYaw();

            // Рассчитываем угол движения с учётом strafe
            float moveAngle = yaw;
            if (forward != 0 || strafe != 0) {
                if (forward > 0) {
                    if (strafe > 0) moveAngle -= 45;
                    else if (strafe < 0) moveAngle += 45;
                } else if (forward < 0) {
                    if (strafe > 0) moveAngle -= 135;
                    else if (strafe < 0) moveAngle += 135;
                    else moveAngle += 180;
                } else {
                    if (strafe > 0) moveAngle -= 90;
                    else if (strafe < 0) moveAngle += 90;
                }
            }

            float yawRad = moveAngle * 0.017453292f;
            double mx = -MathHelper.sin(yawRad) * speed;
            double mz = MathHelper.cos(yawRad) * speed;

            mc.player.setVelocity(mx, mc.player.getVelocity().y, mz);
        }

        // Прыжок только при коллизии со стеной
        if (!mc.player.horizontalCollision) return;

        // Рандомизированная задержка: 80мс + [0..25]мс
        long delay = 80 + (long) (Math.random() * 25.0);

        if (timer.finished(delay)) {
            mc.player.setOnGround(true);
            mc.player.jump();
            mc.player.fallDistance = 0;
            timer.reset();
        }
    }

    // ========================
    // GRIM
    // ========================

    private void handleGrim() {
        if (mc.player == null || mc.world == null) return;

        if (!mc.player.horizontalCollision) {
            grimJumped = false;
            grimGroundTicks = 0;
            return;
        }

        // Проверяем что перед нами стена (солид блок)
        Direction facing = mc.player.getHorizontalFacing();
        BlockPos wallPos = mc.player.getBlockPos().offset(facing);
        BlockPos wallUp = wallPos.up();
        BlockPos wallUp2 = wallPos.up(2);

        boolean solidWall = !mc.world.getBlockState(wallPos).isAir();
        if (!solidWall) return;

        // Проверяем есть ли пространство над стеной (чтобы не биться головой)
        boolean headRoom = mc.world.getBlockState(mc.player.getBlockPos().up(2)).isAir();

        if (mc.player.isOnGround()) {
            grimGroundTicks++;

            // Даём 1-2 тика на земле перед прыжком (легитность)
            if (grimGroundTicks >= 2) {
                // Имитируем ванильный прыжок
                double jumpVelocity = 0.42;

                // Учитываем Jump Boost эффект
                if (mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.JUMP_BOOST)) {
                    jumpVelocity += (mc.player.getStatusEffect(net.minecraft.entity.effect.StatusEffects.JUMP_BOOST).getAmplifier() + 1) * 0.1;
                }

                mc.player.setVelocity(mc.player.getVelocity().x, jumpVelocity, mc.player.getVelocity().z);
                mc.player.fallDistance = 0;
                grimJumped = true;
                grimJumpY = mc.player.getY();
                grimGroundTicks = 0;
            }
        } else {
            grimGroundTicks = 0;

            if (grimJumped) {
                Vec3d vel = mc.player.getVelocity();
                double currentY = mc.player.getY();

                // Пока поднимаемся — применяем ванильную гравитацию (0.08 за тик)
                // Когда начинаем падать — прижимаемся к стене (замедляем падение)
                if (vel.y < 0) {
                    // Замедляем падение, имитируя "скольжение" по стене
                    // Grim проверяет что velocity.y не слишком аномальная
                    double climbY = -(0.08 * (1.0 - climbSpeed.getValue() * 0.5));
                    climbY = Math.max(climbY, -0.08); // Не замедляем слишком сильно

                    mc.player.setVelocity(vel.x, climbY, vel.z);

                    // Периодически отправляем onGround для "сброса" позиции
                    if (mc.player.age % 5 == 0) {
                        mc.player.networkHandler.sendPacket(
                                new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision)
                        );
                        mc.player.fallDistance = 0;
                    }
                }

                // Защита: если упали слишком далеко вниз — сбрасываем
                if (currentY < grimJumpY - 2.0) {
                    grimJumped = false;
                }
            }
        }
    }

    // ========================
    // УТИЛИТЫ
    // ========================

    private boolean isGlassPane(BlockPos pos) {
        if (mc.world == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();
        return block instanceof PaneBlock && block != Blocks.IRON_BARS;
    }

    private int findHotbarItem(net.minecraft.item.Item item) {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    private void sendLookPacket(float pitch) {
        if (mc.player == null) return;
        mc.player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.LookAndOnGround(
                        mc.player.getYaw(),
                        pitch,
                        mc.player.isOnGround(),
                        mc.player.horizontalCollision
                )
        );
    }
}