package fun.lumis.utils.features.aura.point;

import fun.lumis.utils.features.aura.utils.MathAngle;
import fun.lumis.utils.features.aura.warp.Turns;
import fun.lumis.utils.features.aura.utils.RaycastAngle;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import fun.lumis.utils.display.interfaces.QuickImports;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MultiPoint implements QuickImports {
    private final Random random = new Random();
    private Vec3d offset = Vec3d.ZERO;

    public Pair<Vec3d, Box> computeVector(LivingEntity entity, float maxDistance, Turns initialAngle, Vec3d velocity, boolean ignoreWalls) {
        Pair<List<Vec3d>, Box> candidatePoints = generateCandidatePoints(entity, maxDistance, ignoreWalls);
        Vec3d bestVector = findBestVector(candidatePoints.getLeft(), initialAngle);
        updateOffset(velocity);
        Vec3d base = bestVector == null ? entity.getEyePos() : bestVector;
        return new Pair<>(base.add(offset), candidatePoints.getRight());
    }

    public Pair<List<Vec3d>, Box> generateCandidatePoints(LivingEntity entity, float maxDistance, boolean ignoreWalls) {
        Box entityBox = entity.getBoundingBox();
        double stepY = entityBox.getLengthY() / 10.0;
        List<Vec3d> list = Stream.iterate(entityBox.minY, y -> y <= entityBox.maxY, y -> y + stepY)
                .map(y -> new Vec3d(entityBox.getCenter().x, y, entityBox.getCenter().z))
                .filter(point -> isValidPoint(mc.player.getEyePos(), point, maxDistance, ignoreWalls))
                .toList();
        return new Pair<>(list, entityBox);
    }

    public boolean hasValidPoint(LivingEntity entity, float maxDistance, boolean ignoreWalls) {
        Box entityBox = entity.getBoundingBox();
        double stepY = entityBox.getLengthY() / 10.0;
        return Stream.iterate(entityBox.minY, y -> y <= entityBox.maxY, y -> y + stepY)
                .map(y -> new Vec3d(entityBox.getCenter().x, y, entityBox.getCenter().z))
                .anyMatch(point -> isValidPoint(mc.player.getEyePos(), point, maxDistance, ignoreWalls));
    }

    private boolean isValidPoint(Vec3d startPoint, Vec3d endPoint, float maxDistance, boolean ignoreWalls) {
        if (startPoint.distanceTo(endPoint) > maxDistance) return false;
        if (ignoreWalls) return true;
        return RaycastAngle.raycast(startPoint, endPoint, RaycastContext.ShapeType.COLLIDER).getType() != HitResult.Type.BLOCK;
    }

    private Vec3d findBestVector(List<Vec3d> candidatePoints, Turns initialAngle) {
        return candidatePoints.stream()
                .min(Comparator.comparing(point -> calculateRotationDifference(mc.player.getEyePos(), point, initialAngle)))
                .orElse(null);
    }

    private double calculateRotationDifference(Vec3d startPoint, Vec3d endPoint, Turns initialAngle) {
        Turns targetAngle = MathAngle.fromVec3d(endPoint.subtract(startPoint));
        Turns delta = MathAngle.calculateDelta(initialAngle, targetAngle);
        return Math.hypot(delta.getYaw(), delta.getPitch());
    }

    private void updateOffset(Vec3d velocity) {
        double multi = velocity.horizontalLength() * 0.1;
        offset = new Vec3d(
                random.nextGaussian() * multi,
                random.nextGaussian() * 0.01,
                random.nextGaussian() * multi
        );
    }
}