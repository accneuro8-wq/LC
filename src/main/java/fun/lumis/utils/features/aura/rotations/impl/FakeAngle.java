package fun.lumis.utils.features.aura.rotations.impl;

import fun.lumis.lumis;
import fun.lumis.features.impl.combat.Aura;
import fun.lumis.utils.features.aura.point.Vector;
import fun.lumis.utils.features.aura.rotations.constructor.RotateConstructor;
import fun.lumis.utils.features.aura.striking.StrikeManager;
import fun.lumis.utils.features.aura.utils.MathAngle;
import fun.lumis.utils.features.aura.warp.Turns;
import fun.lumis.utils.math.time.StopWatch;
import fun.lumis.utils.math.time.TimerUtil;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class FakeAngle extends RotateConstructor {
    private static final Random RANDOM = new Random();
    private int swingCount = 0;
    private boolean hasSwungTwice = false;
    private boolean hasSwung = false;
    private boolean disableRotation = false;
    TimerUtil timer = new TimerUtil();

    public FakeAngle() {
        super("FakeAngle to SlothAC");
    }

    @Override
    public Turns limitAngleChange(Turns currentAngle, Turns targetAngle, Vec3d vec3d, Entity entity) {

        if (entity != null) {
            Vec3d aimPoint = Vector.brain(entity, 0F, 5F);
            targetAngle = MathAngle.calculateAngle(aimPoint);
        }
        Turns angleDelta = MathAngle.calculateDelta(currentAngle, targetAngle);
        float yawDelta = angleDelta.getYaw();
        float pitchDelta = angleDelta.getPitch();

        float rotationDifference = (float) Math.hypot(yawDelta, pitchDelta);

        float straightLineYaw = Math.abs(yawDelta / rotationDifference) * 360;
        float straightLinePitch = Math.abs(pitchDelta / rotationDifference) * 360;

        float jitterYaw = (float) (8 * Math.sin(System.currentTimeMillis() / 85D));
        float jitterPitch = (float) (8 * Math.sin(System.currentTimeMillis() / 95D));

        float newYaw = currentAngle.getYaw() + Math.min(Math.max(yawDelta, -straightLineYaw), straightLineYaw) ;
        float newPitch = currentAngle.getPitch() + Math.min(Math.max(pitchDelta, -straightLinePitch), straightLinePitch) ;

        return new Turns(newYaw, newPitch);
    }

    @Override
    public Vec3d randomValue() {
        return new Vec3d(0.05, 0.1, 0.02);
    }

    private float randomLerp(float min, float max) {
        return MathHelper.lerp(RANDOM.nextFloat(), min, max);
    }
}
