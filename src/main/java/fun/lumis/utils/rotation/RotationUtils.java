package fun.lumis.utils.rotation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class RotationUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static float[] getRotations(Vec3d target) {
        if (mc.player == null) {
            return new float[]{0, 0};
        }

        Vec3d eyePos = mc.player.getEyePos();
        double dx = target.x - eyePos.x;
        double dy = target.y - eyePos.y;
        double dz = target.z - eyePos.z;

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

        return new float[]{yaw, pitch};
    }
}
