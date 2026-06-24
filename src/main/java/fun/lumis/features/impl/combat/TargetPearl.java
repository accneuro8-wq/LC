package fun.lumis.features.impl.combat;

import fun.lumis.events.player.TickEvent;
import fun.lumis.features.impl.combat.Aura;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.BindSetting;
import fun.lumis.features.module.setting.implement.SelectSetting;
import fun.lumis.features.module.setting.implement.SliderSettings;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.utils.features.aura.utils.MathAngle;
import fun.lumis.utils.features.aura.warp.Turns;
import fun.lumis.utils.features.aura.warp.TurnsConfig;
import fun.lumis.utils.features.aura.warp.TurnsConnection;
import fun.lumis.utils.interactions.interact.PlayerInteractionHelper;
import fun.lumis.utils.math.task.TaskPriority;
import fun.lumis.utils.math.time.StopWatch;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public class TargetPearl extends Module {

    private static final double PEARL_GRAVITY = 0.03;
    private static final double PEARL_DRAG = 0.99;
    private static final double PEARL_THROW_VELOCITY = 1.5;

    private static final int MAX_PREDICT_TICKS = 160;
    private static final int MAX_SOLVE_TICKS = 160;

    private static final ItemStack PEARL_STACK = new ItemStack(Items.ENDER_PEARL);

    private final SelectSetting modeSetting = new SelectSetting("Когда кидать", "Когда кидать")
            .value("Bind", "Always").selected("Always");
    private final SelectSetting targetSetting = new SelectSetting("Цели", "Цели")
            .value("Aura Target", "All").selected("Aura Target");
    private final BindSetting throwSetting = new BindSetting("Клавиша", "Клавиша").visible(() -> modeSetting.isSelected("Bind"));

    private final SliderSettings distanceSetting = new SliderSettings("Макс.Дистанция", "Макс. дистанция")
            .setValue(15).range(5, 30);
    private final SliderSettings minPearlDist = new SliderSettings("Мин.Дистанция", "Мин. дистанция")
            .setValue(3).range(1, 10);
    private final SliderSettings accuracySetting = new SliderSettings("Точность", "Точность расчёта")
            .setValue(10).range(5, 20);

    private final StopWatch timer = new StopWatch();

    private EnderPearlEntity lastTargetPearl = null;
    private int lastThrowTick = 0;

    public TargetPearl() {
        super("TargetPearl", "TargetPearl   §e§l[BETA]", ModuleCategory.COMBAT);
        setup(modeSetting, targetSetting, throwSetting, distanceSetting, minPearlDist, accuracySetting);
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onTick(TickEvent ignored) {
        if (mc.player == null || mc.world == null) return;

        if (modeSetting.isSelected("Bind") && !PlayerInteractionHelper.isKey(throwSetting)) return;

        if (!timer.finished(200)) return;

        if (mc.player.getItemCooldownManager().isCoolingDown(PEARL_STACK)) return;

        boolean myPearlInAir = PlayerInteractionHelper.streamEntities()
                .anyMatch(e -> e instanceof EnderPearlEntity && Objects.equals(((EnderPearlEntity) e).getOwner(), mc.player));
        if (myPearlInAir) return;

        Optional<Candidate> best = findBestCandidate();
        if (best.isEmpty()) return;

        Candidate c = best.get();

        int currentTick = mc.player.age;
        if (lastTargetPearl != null && lastTargetPearl.equals(c.pearl) && currentTick - lastThrowTick < 10) return;

        Turns turns = solveThrowTurnsToLand(c.landingPos);
        if (turns == null) return;

        if (!tryThrowPearl(turns)) return;

        timer.reset();
        lastTargetPearl = c.pearl;
        lastThrowTick = currentTick;
    }

    private boolean tryThrowPearl(Turns turns) {
        if (mc.player == null) return false;
        if (mc.player.getItemCooldownManager().isCoolingDown(PEARL_STACK)) return false;

        TurnsConnection.INSTANCE.rotateTo(turns, TurnsConfig.DEFAULT, TaskPriority.HIGH_IMPORTANCE_3, this);

        if (mc.player.getOffHandStack().isOf(Items.ENDER_PEARL)) {
            PlayerInteractionHelper.interactItem(Hand.OFF_HAND, turns);
            return true;
        }

        int slot = findPearlInHotbar();
        if (slot == -1) return false;

        int prev = mc.player.getInventory().selectedSlot;
        if (prev != slot) {
            mc.player.getInventory().selectedSlot = slot;
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }

        PlayerInteractionHelper.interactItem(Hand.MAIN_HAND, turns);

        if (prev != slot) {
            mc.player.getInventory().selectedSlot = prev;
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(prev));
        }

        return true;
    }

    private int findPearlInHotbar() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.ENDER_PEARL)) return i;
        }
        return -1;
    }

    private Optional<Candidate> findBestCandidate() {
        LivingEntity auraTarget = Aura.getInstance().getTarget();

        return PlayerInteractionHelper.streamEntities()
                .filter(e -> e instanceof EnderPearlEntity)
                .map(e -> (EnderPearlEntity) e)
                .filter(p -> p.getOwner() != null && !Objects.equals(p.getOwner(), mc.player))
                .filter(p -> !targetSetting.isSelected("Aura Target") || (auraTarget != null && p.getOwner().getUuid().equals(auraTarget.getUuid())))
                .map(p -> {
                    Landing landing = predictLanding(p.getPos(), p.getVelocity(), MAX_PREDICT_TICKS);
                    if (landing == null) return null;
                    return new Candidate(p, landing.pos, landing.ticks);
                })
                .filter(Objects::nonNull)
                .filter(c -> {
                    if (mc.player == null) return false;
                    double d = mc.player.getEyePos().distanceTo(c.landingPos);
                    return d >= minPearlDist.getValue() && d <= distanceSetting.getValue();
                })
                .min(Comparator.comparingDouble(c -> scoreCandidate(c)));
    }

    private double scoreCandidate(Candidate c) {
        if (mc.player == null) return Double.MAX_VALUE;
        double dist = mc.player.getEyePos().distanceTo(c.landingPos);
        return c.landingTicks + dist * 0.15;
    }

    private Landing predictLanding(Vec3d startPos, Vec3d startVel, int maxTicks) {
        if (mc.world == null || mc.player == null) return null;

        Vec3d pos = startPos;
        Vec3d vel = startVel;

        for (int t = 0; t < maxTicks; t++) {
            Vec3d prev = pos;

            vel = vel.multiply(PEARL_DRAG).add(0.0, -PEARL_GRAVITY, 0.0);
            Vec3d next = pos.add(vel);

            HitResult hit = mc.world.raycast(new RaycastContext(prev, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) return new Landing(hit.getPos(), t + 1);

            pos = next;
        }

        return null;
    }

    private Turns solveThrowTurnsToLand(Vec3d targetPos) {
        if (mc.player == null || mc.world == null) return null;

        Vec3d origin = mc.player.getEyePos();
        Vec3d delta = targetPos.subtract(origin);

        double dx = delta.x;
        double dz = delta.z;
        double dy = delta.y;

        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 0.001) return null;

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);

        float pitchGuess = ballisticPitchNoDrag(horizontal, dy);
        float bestPitch = Float.isFinite(pitchGuess) ? pitchGuess : 10.0f;

        float window = (float) MathHelper.clamp(accuracySetting.getValue() * 1.6, 10.0, 45.0);
        float coarseStep = 1.0f;
        float fineStep = 0.25f;

        double bestError = Double.MAX_VALUE;

        float start = MathHelper.clamp(bestPitch - window, -89.0f, 89.0f);
        float end = MathHelper.clamp(bestPitch + window, -89.0f, 89.0f);

        for (float pitch = start; pitch <= end; pitch += coarseStep) {
            Vec3d impact = simulateOurImpact(origin, yaw, pitch);
            if (impact == null) continue;
            double err = impact.distanceTo(targetPos);
            if (err < bestError) {
                bestError = err;
                bestPitch = pitch;
            }
        }

        float fineStart = MathHelper.clamp(bestPitch - 3.0f, -89.0f, 89.0f);
        float fineEnd = MathHelper.clamp(bestPitch + 3.0f, -89.0f, 89.0f);

        for (float pitch = fineStart; pitch <= fineEnd; pitch += fineStep) {
            Vec3d impact = simulateOurImpact(origin, yaw, pitch);
            if (impact == null) continue;
            double err = impact.distanceTo(targetPos);
            if (err < bestError) {
                bestError = err;
                bestPitch = pitch;
            }
        }

        if (bestError > 2.5) return null;

        Vec3d dir = dirFromYawPitch(yaw, bestPitch);
        return MathAngle.fromVec3d(dir);
    }

    private float ballisticPitchNoDrag(double horizontal, double dy) {
        double v = PEARL_THROW_VELOCITY;
        double g = PEARL_GRAVITY;

        double v2 = v * v;
        double disc = v2 * v2 - g * (g * horizontal * horizontal + 2.0 * dy * v2);
        if (disc < 0.0) return Float.NaN;

        double sqrt = Math.sqrt(disc);
        double tan = (v2 - sqrt) / (g * horizontal);
        double angle = Math.atan(tan);
        return (float) -Math.toDegrees(angle);
    }

    private Vec3d simulateOurImpact(Vec3d origin, float yaw, float pitch) {
        if (mc.world == null || mc.player == null) return null;

        Vec3d pos = origin;
        Vec3d vel = dirFromYawPitch(yaw, pitch).multiply(PEARL_THROW_VELOCITY);

        for (int t = 0; t < MAX_SOLVE_TICKS; t++) {
            Vec3d prev = pos;

            vel = vel.multiply(PEARL_DRAG).add(0.0, -PEARL_GRAVITY, 0.0);
            Vec3d next = pos.add(vel);

            HitResult hit = mc.world.raycast(new RaycastContext(prev, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) return hit.getPos();

            pos = next;
        }

        return pos;
    }

    private Vec3d dirFromYawPitch(float yaw, float pitch) {
        double ry = Math.toRadians(yaw);
        double rp = Math.toRadians(pitch);

        double x = -Math.sin(ry) * Math.cos(rp);
        double y = -Math.sin(rp);
        double z = Math.cos(ry) * Math.cos(rp);

        return new Vec3d(x, y, z);
    }

    private static final class Candidate {
        private final EnderPearlEntity pearl;
        private final Vec3d landingPos;
        private final int landingTicks;

        private Candidate(EnderPearlEntity pearl, Vec3d landingPos, int landingTicks) {
            this.pearl = pearl;
            this.landingPos = landingPos;
            this.landingTicks = landingTicks;
        }
    }

    private static final class Landing {
        private final Vec3d pos;
        private final int ticks;

        private Landing(Vec3d pos, int ticks) {
            this.pos = pos;
            this.ticks = ticks;
        }
    }
}