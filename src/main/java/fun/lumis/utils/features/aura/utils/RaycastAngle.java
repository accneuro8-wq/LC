package fun.lumis.utils.features.aura.utils;

import fun.lumis.utils.features.aura.warp.Turns;
import fun.lumis.utils.features.aura.warp.TurnsConnection;
import lombok.experimental.UtilityClass;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import fun.lumis.utils.display.interfaces.QuickImports;
import fun.lumis.utils.features.aura.striking.StrikerConstructor;

import java.util.Objects;

@UtilityClass
public class RaycastAngle implements QuickImports {

    public static BlockHitResult raycast(Vec3d start, Vec3d end, RaycastContext.ShapeType shapeType) {
        return mc.world.raycast(new RaycastContext(
                start,
                end,
                shapeType,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
    }

    public static boolean rayTrace(StrikerConstructor.AttackPerpetratorConfigurable config) {
        if (mc.player == null || config.getTarget() == null) return false;
        if (mc.player.isGliding() && config.getTarget().isGliding()) return true;

        return rayTrace(TurnsConnection.INSTANCE.getRotation().toVector(),
                config.getMaximumRange(),
                config.getBox());
    }

    public static boolean rayTrace(double range, Box box) {
        return rayTrace(TurnsConnection.INSTANCE.getRotation().toVector(), range, box);
    }

    public static boolean rayTrace(Vec3d rotationVec, double range, Box box) {
        if (mc.player == null) return false;
        Vec3d start = mc.player.getEyePos();
        Vec3d end = start.add(rotationVec.multiply(range));
        Box expandedBox = box.expand(0.12);
        return expandedBox.contains(start) || expandedBox.raycast(start, end).isPresent();
    }

    public static BlockHitResult raycast(double range, Turns angle, boolean includeFluids) {
        Vec3d start = Objects.requireNonNull(mc.player).getCameraPosVec(1.0F);
        Vec3d end = start.add(angle.toVector().multiply(range));
        RaycastContext.FluidHandling fluids = includeFluids ? RaycastContext.FluidHandling.ANY : RaycastContext.FluidHandling.NONE;
        return mc.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, fluids, mc.player));
    }
}