package fun.lumis.features.impl.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import fun.lumis.events.player.TickEvent;
import fun.lumis.events.render.WorldLoadEvent;
import fun.lumis.events.render.WorldRenderEvent;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.BooleanSetting;
import fun.lumis.features.module.setting.implement.ColorSetting;
import fun.lumis.features.module.setting.implement.SelectSetting;
import fun.lumis.features.module.setting.implement.SliderSettings;
import fun.lumis.utils.client.Instance;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.utils.display.color.ColorAssist;
import fun.lumis.utils.display.geometry.Render3D;
import fun.lumis.utils.math.time.StopWatch;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class Trails extends Module {

    public static Trails getInstance() {
        return Instance.get(Trails.class);
    }

    final BooleanSetting xp = new BooleanSetting("XP бутылки", "Отображать трейлы для XP бутылок").setValue(false);
    final SelectSetting pearls = new SelectSetting("Эндер-жемчуг", "Режим отображения для эндер-жемчуга")
            .value("Частицы", "Выкл").selected("Частицы");
    final SelectSetting arrows = new SelectSetting("Стрелы", "Режим отображения для стрел")
            .value("Частицы", "Выкл").selected("Частицы");
    final SelectSetting players = new SelectSetting("Игроки", "Режим отображения для игроков")
            .value("Частицы", "Kagune", "Tail", "Выкл").selected("Частицы"); 
    final BooleanSetting onlySelf = new BooleanSetting("Только я", "Отображать только для себя")
            .setValue(false).visible(() -> !players.isSelected("Выкл"));
    final BooleanSetting hideFirstPerson = new BooleanSetting("От 1 лица", "Скрывать от первого лица")
            .setValue(true).visible(() -> !players.isSelected("Выкл"));

    final SliderSettings tailLength = new SliderSettings("Tail Length", "Длина хвоста")
            .setValue(250F).range(150F, 350F)
            .visible(() -> players.isSelected("Tail"));

    final SelectSetting particleMode = new SelectSetting("Тип частиц", "Тип отображаемых частиц")
            .value("Звезды", "Блум", "Сакура", "Луна", "Спарк", "Треугольник", "Куб", "Крест").selected("Спарк")
            .visible(() -> players.isSelected("Частицы"));
    final SelectSetting physics = new SelectSetting("Физика", "Физика частиц")
            .value("Падение", "Парение").selected("Парение")
            .visible(() -> players.isSelected("Частицы"));
    final SliderSettings particleScale = new SliderSettings("Размер частиц", "Размер частиц")
            .setValue(3).range(1, 10)
            .visible(() -> players.isSelected("Частицы"));
    final SliderSettings amount = new SliderSettings("Количество", "Количество частиц за спавн")
            .setValue(3).range(1, 10)
            .visible(() -> players.isSelected("Частицы"));
    final SliderSettings lifeTime = new SliderSettings("Время жизни", "Время жизни частиц в секундах")
            .setValue(2).range(1, 10)
            .visible(() -> players.isSelected("Частицы"));

    final SliderSettings kaguneLength = new SliderSettings("Kagune Length", "Длина хвоста")
            .setValue(60f).range(20f, 120f)
            .visible(() -> players.isSelected("Kagune"));
    final SliderSettings kaguneSize = new SliderSettings("Kagune Size", "Размер частиц")
            .setValue(0.25f).range(0.05f, 0.8f)
            .visible(() -> players.isSelected("Kagune"));
    final SliderSettings kaguneAlpha = new SliderSettings("Kagune Alpha", "Прозрачность")
            .setValue(0.85f).range(0.1f, 1.0f)
            .visible(() -> players.isSelected("Kagune"));
    final SliderSettings kaguneSmooth = new SliderSettings("Kagune Smooth", "Плавность следования")
            .setValue(0.3f).range(0.1f, 0.9f)
            .visible(() -> players.isSelected("Kagune"));

    final SelectSetting colorMode = new SelectSetting("Режим цвета", "Режим цвета")
            .value("Sync", "Custom").selected("Sync");
    final ColorSetting customColor = new ColorSetting("Кастом цвет", "Кастомный цвет")
            .value(0xFF50b4b4).visible(() -> colorMode.isSelected("Custom"));

    final Identifier STAR_TEXTURE = Identifier.of("textures/new_particles/star.png");
    final Identifier BLOOM_TEXTURE = Identifier.of("textures/new_particles/glow.png");
    final Identifier SAKURA_TEXTURE = Identifier.of("textures/new_particles/feather.png");
    final Identifier MOON_TEXTURE = Identifier.of("textures/new_particles/moon.png");
    final Identifier SPARK_TEXTURE = Identifier.of("textures/new_particles/spark.png");
    final Identifier TRIANGLE_TEXTURE = Identifier.of("textures/new_particles/triangle.png");
    final Identifier CUBE_TEXTURE = Identifier.of("textures/new_particles/cube.png");
    final Identifier MCROSS_TEXTURE = Identifier.of("textures/new_particles/mcross.png");
    final List<Particle> particles = new ArrayList<>();

    final Map<java.util.UUID, Deque<KagunePoint>> kaguneTrails = new HashMap<>();
    final Map<java.util.UUID, Long> kaguneLastMoveTime = new HashMap<>();
    final Map<java.util.UUID, Vec3d> kaguneLastPos = new HashMap<>();
    final Map<java.util.UUID, Float> kaguneVisibility = new HashMap<>();
    final List<TailPoint> tailPoints = new ArrayList<>();
    final Random random = new Random();
    private int tickCounter = 0;
    
    private static final long KAGUNE_FADE_DURATION = 500L;

    public Trails() {
        super("Trails", "Trails", ModuleCategory.RENDER);
        setup(xp, pearls, arrows, players, onlySelf, hideFirstPerson, 
              tailLength, particleMode, physics, particleScale, amount, lifeTime,
              kaguneLength, kaguneSize, kaguneAlpha, kaguneSmooth,
              colorMode, customColor);
    }

    @Override
    public void deactivate() {
        super.deactivate();
        particles.clear();
        tailPoints.clear();
        kaguneTrails.clear();
        kaguneLastMoveTime.clear();
        kaguneLastPos.clear();
        kaguneVisibility.clear();
        tickCounter = 0;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        particles.clear();
        tailPoints.clear();
        kaguneTrails.clear();
        kaguneLastMoveTime.clear();
        kaguneLastPos.clear();
        kaguneVisibility.clear();
    }

    @EventHandler
    public void onTick(TickEvent e) {
        tickCounter++;
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (mc.player == null || mc.world == null) return;

        MatrixStack stack = e.getStack();
        float tickDelta = e.getPartialTicks();

        for (Entity en : mc.world.getEntities()) {
            if (en instanceof ExperienceBottleEntity && xp.isValue()) {
                calcTrajectory(en);
            }
        }

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player != mc.player && onlySelf.isValue()) continue;

            boolean isMoving = player.getVelocity().horizontalLengthSquared() > 0.001 
                    || Math.abs(player.getY() - player.prevY) > 0.01;

            if (players.isSelected("Частицы") && isMoving) {
                if (tickCounter % 2 == 0) {
                    spawnParticles(player);
                }
            }

            if (players.isSelected("Kagune")) {
                updateKaguneTrail(player);
            }
        }

        if (players.isSelected("Tail") && mc.gameRenderer.getCamera().isThirdPerson()) {
            renderTail(stack, tickDelta);
        }

        if (players.isSelected("Kagune")) {
            renderKagune(tickDelta);
        }

        if (players.isSelected("Частицы") && !particles.isEmpty()) {
            renderParticles();
        }

        long maxLife = lifeTime.getInt() * 1000L;
        particles.removeIf(p -> System.currentTimeMillis() - p.time > maxLife);
    }

    private void spawnParticles(PlayerEntity player) {
        boolean isFirstPerson = hideFirstPerson.isValue() 
                && mc.options.getPerspective().isFirstPerson() 
                && player == mc.player;
        if (isFirstPerson) return;

        int count = amount.getInt();
        for (int i = 0; i < count; i++) {
            double px = player.getX() + randomFloat(-0.25f, 0.25f);
            double py = player.getY() + randomFloat(0.2f, 1.4f);
            double pz = player.getZ() + randomFloat(-0.25f, 0.25f);

            Color col = getColor(i);
            particles.add(new Particle(px, py, pz, col));
        }
    }

    private void renderParticles() {
        boolean isFirstPerson = hideFirstPerson.isValue() && mc.options.getPerspective().isFirstPerson();

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.setShaderTexture(0, getParticleTexture());
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getPos();

        int rendered = 0;
        int vertexCount = 0;
        for (Particle p : particles) {
            if (rendered++ > 300) break;

            p.update();

            float scale = particleScale.getValue() / 10f;
            float age = (System.currentTimeMillis() - p.time) / (lifeTime.getInt() * 1000f);
            float alpha = (1f - age) * 0.9f;
            if (alpha <= 0) continue;

            MatrixStack matrices = new MatrixStack();
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
            matrices.translate(p.x - camPos.x, p.y - camPos.y, p.z - camPos.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p.rotation));

            Matrix4f matrix = matrices.peek().getPositionMatrix();
            int color = ColorAssist.replAlpha(p.color.getRGB(), (int) (alpha * 255));

            buffer.vertex(matrix, -scale, -scale, 0).texture(0, 0).color(color);
            buffer.vertex(matrix, -scale, scale, 0).texture(0, 1).color(color);
            buffer.vertex(matrix, scale, scale, 0).texture(1, 1).color(color);
            buffer.vertex(matrix, scale, -scale, 0).texture(1, 0).color(color);
            vertexCount += 4;
        }

        if (vertexCount > 0) {
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }


    private void updateKaguneTrail(PlayerEntity player) {
        Deque<KagunePoint> deque = kaguneTrails.computeIfAbsent(player.getUuid(), id -> new ArrayDeque<>());
        long now = System.currentTimeMillis();

        Vec3d targetPos = new Vec3d(
                MathHelper.lerp(mc.getRenderTickCounter().getTickDelta(true), player.prevX, player.getX()),
                MathHelper.lerp(mc.getRenderTickCounter().getTickDelta(true), player.prevY, player.getY()) + player.getHeight() * 0.5,
                MathHelper.lerp(mc.getRenderTickCounter().getTickDelta(true), player.prevZ, player.getZ())
        );

        boolean isMoving = player.getVelocity().horizontalLengthSquared() > 0.0001
                || Math.abs(player.getY() - player.prevY) > 0.01;
        
        Vec3d lastPos = kaguneLastPos.get(player.getUuid());
        if (!isMoving && lastPos != null) {
            isMoving = lastPos.squaredDistanceTo(targetPos) > 0.0001;
        }
        
        if (isMoving) {
            kaguneLastMoveTime.put(player.getUuid(), now);
        }
        kaguneLastPos.put(player.getUuid(), targetPos);

        Long lastMoveTime = kaguneLastMoveTime.get(player.getUuid());
        boolean recentlyMoved = lastMoveTime != null && (now - lastMoveTime) < 150;

        float currentVisibility = kaguneVisibility.getOrDefault(player.getUuid(), 1f);
        float targetVisibility = (isMoving || recentlyMoved) ? 1f : 0f;
        float fadeSpeed = 0.08f;
        
        if (currentVisibility < targetVisibility) {
            currentVisibility = Math.min(targetVisibility, currentVisibility + fadeSpeed * 2f);
        } else if (currentVisibility > targetVisibility) {
            currentVisibility = Math.max(targetVisibility, currentVisibility - fadeSpeed);
        }
        kaguneVisibility.put(player.getUuid(), currentVisibility);

        KagunePoint head = deque.peekFirst();
        
        if (head != null) {
            double distance = head.pos.distanceTo(targetPos);
            double maxGap = 0.15;
            
            if (distance > maxGap) {
                int subdivisions = (int) Math.ceil(distance / maxGap);
                for (int i = 1; i <= subdivisions; i++) {
                    float t = (float) i / subdivisions;
                    Vec3d interpPos = lerpVec(head.pos, targetPos, t);
                    deque.addFirst(new KagunePoint(interpPos, now));
                }
            } else if (distance > 0.005) {
                float smooth = kaguneSmooth.getValue();
                Vec3d smoothedPos = lerpVec(head.pos, targetPos, smooth);
                deque.addFirst(new KagunePoint(smoothedPos, now));
            } else if (isMoving || recentlyMoved) {
                deque.addFirst(new KagunePoint(targetPos, now));
            }
        } else {
            deque.addFirst(new KagunePoint(targetPos, now));
        }

        int maxPoints = kaguneLength.getInt();
        while (deque.size() > maxPoints) {
            deque.removeLast();
        }
    }

    private void renderKagune(float tickDelta) {
        if (kaguneTrails.isEmpty()) return;

        boolean isFirstPerson = hideFirstPerson.isValue() && mc.options.getPerspective().isFirstPerson();
        if (isFirstPerson) return;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderTexture(0, BLOOM_TEXTURE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getPos();

        int vertexCount = 0;
        for (Map.Entry<java.util.UUID, Deque<KagunePoint>> entry : kaguneTrails.entrySet()) {
            java.util.UUID playerId = entry.getKey();
            Deque<KagunePoint> deque = entry.getValue();
            List<KagunePoint> points = new ArrayList<>(deque);
            int total = points.size();
            if (total < 2) continue;

            float visibility = kaguneVisibility.getOrDefault(playerId, 1f);
            if (visibility <= 0.01f) continue;

            int renderPoints = Math.max(total, 30);
            
            for (int i = 0; i < renderPoints; i++) {
                float exactIndex = (float) i / (renderPoints - 1) * (total - 1);
                int idx1 = (int) Math.floor(exactIndex);
                int idx2 = Math.min(idx1 + 1, total - 1);
                float localT = exactIndex - idx1;
                
                KagunePoint p1 = points.get(idx1);
                KagunePoint p2 = points.get(idx2);
                
                Vec3d pos = lerpVec(p1.pos, p2.pos, localT);

                float positionFactor = (float) i / (float) (renderPoints - 1);
                
                float baseAlpha = (float) Math.pow(1f - positionFactor, 1.5f);
                
                float fadePower = 1f + positionFactor * 2f;
                float visibilityFade = (float) Math.pow(visibility, fadePower);
                
                float alpha = kaguneAlpha.getValue() * baseAlpha * visibilityFade;
                if (alpha <= 0.01f) continue;

                float sizeFactor = 1f - positionFactor * 0.6f;
                float size = kaguneSize.getValue() * sizeFactor;

                int baseColor = colorMode.isSelected("Sync")
                        ? ColorAssist.fade(8, (int)(positionFactor * total * 8), ColorAssist.getClientColor(), ColorAssist.getClientColor(0.5f))
                        : customColor.getColor();

                MatrixStack matrices = new MatrixStack();
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
                matrices.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

                Matrix4f m = matrices.peek().getPositionMatrix();

                float glowSize = size * 2.5f;
                int glowColor = ColorAssist.replAlpha(baseColor, (int) (alpha * 0.3f * 255));
                buffer.vertex(m, -glowSize, -glowSize, 0).texture(0, 0).color(glowColor);
                buffer.vertex(m, -glowSize, glowSize, 0).texture(0, 1).color(glowColor);
                buffer.vertex(m, glowSize, glowSize, 0).texture(1, 1).color(glowColor);
                buffer.vertex(m, glowSize, -glowSize, 0).texture(1, 0).color(glowColor);

                int coreColor = ColorAssist.replAlpha(baseColor, (int) (alpha * 255));
                buffer.vertex(m, -size, -size, 0).texture(0, 0).color(coreColor);
                buffer.vertex(m, -size, size, 0).texture(0, 1).color(coreColor);
                buffer.vertex(m, size, size, 0).texture(1, 1).color(coreColor);
                buffer.vertex(m, size, -size, 0).texture(1, 0).color(coreColor);
                vertexCount += 8;
            }
        }

        if (vertexCount > 0) {
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private void renderTail(MatrixStack stack, float tickDelta) {
        if (mc.player == null) return;

        tailPoints.removeIf(point -> point.time.finished(tailLength.getValue()));

        Vec3d playerPos = new Vec3d(
                MathHelper.lerp(tickDelta, mc.player.prevX, mc.player.getX()),
                MathHelper.lerp(tickDelta, mc.player.prevY, mc.player.getY()),
                MathHelper.lerp(tickDelta, mc.player.prevZ, mc.player.getZ())
        );

        if (tailPoints.isEmpty() || tailPoints.get(tailPoints.size() - 1).pos.squaredDistanceTo(playerPos) > 0.001) {
            tailPoints.add(new TailPoint(playerPos));
        }

        if (tailPoints.size() < 2) return;

        MatrixStack.Entry entry = stack.peek();
        float playerHeight = mc.player.getHeight();
        int size = tailPoints.size();

        for (int i = 0; i < size - 1; i++) {
            TailPoint p1 = tailPoints.get(i);
            TailPoint p2 = tailPoints.get(i + 1);

            int color1 = ColorAssist.fade(3, i, ColorAssist.getClientColor(), ColorAssist.getClientColor(0.3f));
            int color2 = ColorAssist.fade(3, i + 1, ColorAssist.getClientColor(), ColorAssist.getClientColor(0.3f));
            float alpha1 = Math.min((float) i / (float) size, 1F);
            float alpha2 = Math.min((float) (i + 1) / (float) size, 1F);
            int finalColor1 = ColorAssist.replAlpha(color1, alpha1 / 2F);
            int finalColor2 = ColorAssist.replAlpha(color2, alpha2 / 2F);

            Vec3d top1 = new Vec3d(p1.pos.x, p1.pos.y + playerHeight, p1.pos.z);
            Vec3d top2 = new Vec3d(p2.pos.x, p2.pos.y + playerHeight, p2.pos.z);
            Vec3d bottom1 = new Vec3d(p1.pos.x, p1.pos.y + 0.0005F, p1.pos.z);
            Vec3d bottom2 = new Vec3d(p2.pos.x, p2.pos.y + 0.0005F, p2.pos.z);

            Render3D.drawQuad(entry, top1, top2, bottom2, bottom1, finalColor1, false);
            Render3D.drawLine(entry, top1, top2, finalColor1, finalColor2, 2F, false);
            Render3D.drawLine(entry, bottom1, bottom2, finalColor1, finalColor2, 2F, false);
        }
    }


    private void calcTrajectory(Entity e) {
        double motionX = e.getVelocity().x;
        double motionY = e.getVelocity().y;
        double motionZ = e.getVelocity().z;
        double x = e.getX();
        double y = e.getY();
        double z = e.getZ();
        Vec3d lastPos = new Vec3d(x, y, z);

        for (int i = 0; i < 300; i++) {
            lastPos = new Vec3d(x, y, z);
            x += motionX;
            y += motionY;
            z += motionZ;

            if (mc.world.getBlockState(BlockPos.ofFloored(x, y, z)).getBlock() == Blocks.WATER) {
                motionX *= 0.8;
                motionY *= 0.8;
                motionZ *= 0.8;
            } else {
                motionX *= 0.99;
                motionY *= 0.99;
                motionZ *= 0.99;
            }

            if (e instanceof ArrowEntity) {
                motionY -= 0.05;
            } else {
                motionY -= 0.03f;
            }

            Vec3d pos = new Vec3d(x, y, z);

            HitResult hitResult = mc.world.raycast(new RaycastContext(lastPos, pos, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
            if (hitResult != null && hitResult.getType() != HitResult.Type.MISS) {
                break;
            }

            if (y <= -65) break;
            if (e.getVelocity().lengthSquared() < 0.0001) continue;

            int alpha = (int) MathHelper.clamp((255f * (i / 8f)), 0, 255);
            int lineColor = colorMode.isSelected("Sync")
                    ? ColorAssist.replAlpha(ColorAssist.getClientColor(), alpha)
                    : ColorAssist.replAlpha(customColor.getColor(), alpha);

            Render3D.drawLine(lastPos, pos, lineColor, 2f, true);
        }
    }


    private Color getColor(int index) {
        if (colorMode.isSelected("Sync")) {
            return new Color(ColorAssist.getClientColor());
        }
        return new Color(customColor.getColor());
    }

    private Identifier getParticleTexture() {
        return switch (particleMode.getSelected()) {
            case "Блум" -> BLOOM_TEXTURE;
            case "Звезды" -> STAR_TEXTURE;
            case "Сакура" -> SAKURA_TEXTURE;
            case "Луна" -> MOON_TEXTURE;
            case "Треугольник" -> TRIANGLE_TEXTURE;
            case "Куб" -> CUBE_TEXTURE;
            case "Крест" -> MCROSS_TEXTURE;
            default -> SPARK_TEXTURE;
        };
    }

    private float randomFloat(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    private Vec3d lerpVec(Vec3d a, Vec3d b, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return new Vec3d(
                MathHelper.lerp(t, a.x, b.x),
                MathHelper.lerp(t, a.y, b.y),
                MathHelper.lerp(t, a.z, b.z)
        );
    }


    private record KagunePoint(Vec3d pos, long createdAt) {}

    private static class TailPoint {
        final Vec3d pos;
        final StopWatch time = new StopWatch();

        TailPoint(Vec3d pos) {
            this.pos = pos;
        }
    }

    private class Particle {
        double x, y, z;
        double motionX, motionY, motionZ;
        long time;
        Color color;
        float rotation;
        float rotationSpeed;

        Particle(double x, double y, double z, Color color) {
            this.x = x;
            this.y = y;
            this.z = z;
            float speed = 0.02f;
            this.motionX = randomFloat(-speed, speed);
            this.motionY = randomFloat(-speed, speed);
            this.motionZ = randomFloat(-speed, speed);
            this.time = System.currentTimeMillis();
            this.color = color;
            this.rotation = randomFloat(0, 360);
            this.rotationSpeed = randomFloat(-3f, 3f);
        }

        void update() {
            x += motionX;
            y += motionY;
            z += motionZ;

            rotation += rotationSpeed;

            if (physics.isSelected("Падение")) {
                motionY -= 0.001f;
            }

            motionX *= 0.98;
            motionY *= 0.98;
            motionZ *= 0.98;
            rotationSpeed *= 0.99f;

            if (mc.world != null) {
                Block block = mc.world.getBlockState(BlockPos.ofFloored(x, y - 0.1, z)).getBlock();
                if (block != Blocks.AIR && block != Blocks.WATER && block != Blocks.LAVA) {
                    motionY = Math.abs(motionY) * 0.3;
                }
            }
        }
    }
}
