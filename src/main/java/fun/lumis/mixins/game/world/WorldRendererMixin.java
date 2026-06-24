package fun.lumis.mixins.game.world;

import net.minecraft.client.render.*;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import fun.lumis.features.impl.render.BlockOverlay;
import fun.lumis.events.render.WorldRenderEvent;
import fun.lumis.utils.display.geometry.Render3D;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @Shadow
    protected abstract void renderMain(FrameGraphBuilder frameGraphBuilder, Frustum frustum, Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, Fog fog, boolean renderBlockOutline, boolean hasEntitiesToRender, RenderTickCounter renderTickCounter, Profiler profiler);

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;renderMain(Lnet/minecraft/client/render/FrameGraphBuilder;Lnet/minecraft/client/render/Frustum;Lnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/render/Fog;ZZLnet/minecraft/client/render/RenderTickCounter;Lnet/minecraft/util/profiler/Profiler;)V"))
    private void onRenderRedirect(WorldRenderer instance, FrameGraphBuilder frameGraphBuilder, Frustum frustum, Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, Fog fog, boolean renderBlockOutline, boolean hasEntitiesToRender, RenderTickCounter renderTickCounter, Profiler profiler) {

        this.renderMain(frameGraphBuilder, frustum, camera, positionMatrix, projectionMatrix, fog, !BlockOverlay.getInstance().isState(), hasEntitiesToRender, renderTickCounter, profiler);

        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(positionMatrix);

        float partialTicks = renderTickCounter.getTickDelta(true);

        Render3D.onWorldRender(new WorldRenderEvent(matrices, partialTicks));
    }
}