package fun.lumis.mixins.client;

import fun.lumis.events.container.SetScreenEvent;
import fun.lumis.events.player.HotBarUpdateEvent;
import fun.lumis.features.impl.combat.NoInteract;
import fun.lumis.features.impl.misc.SelfDestruct;
import fun.lumis.utils.client.logs.Logger;
import fun.lumis.utils.client.managers.event.EventManager;
import fun.lumis.utils.client.managers.file.exception.FileProcessingException;
import fun.lumis.utils.client.sound.SoundManager;
import fun.lumis.utils.client.window.WindowStyle;
import fun.lumis.utils.client.window.WindowTitleAnimation;
import fun.lumis.utils.display.font.Fonts;
import fun.lumis.lumis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings({"UnusedMixin", "unused"})
@Environment(EnvType.CLIENT)
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Shadow @Nullable
    public ClientPlayerInteractionManager interactionManager;

    @Shadow @Nullable
    public ClientPlayerEntity player;

    @Shadow @Final
    public GameRenderer gameRenderer;

    @Unique
    private final WindowTitleAnimation titleAnimation = WindowTitleAnimation.getInstance();

    // ================================
    // Инициализация и завершение
    // ================================

    @Inject(method = "<init>", at = @At("TAIL"))
    private void lumis$onInit(RunArgs args, CallbackInfo ci) {
        if (SelfDestruct.unhooked) return;

        Fonts.init();
        lumis$updateWindowTitle();
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void lumis$onStop(CallbackInfo ci) {
        if (SelfDestruct.unhooked) return;

        Logger.info("Stopping MinecraftClient");
        SoundManager.playSound(SoundManager.SHUTDOWN);

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        lumis$saveFiles();
    }

    @Unique
    private void lumis$saveFiles() {
        lumis instance = lumis.getInstance();
        if (instance == null || !instance.isInitialized()) return;

        try {
            instance.getFileController().saveFiles();
        } catch (FileProcessingException e) {
            Logger.error("Error saving files: " + e.getMessage());
        } finally {
            instance.getFileController().stopAutoSave();
        }
    }

    // ================================
    // NoInteract
    // ================================

    @Inject(
            method = "doItemUse",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Hand;values()[Lnet/minecraft/util/Hand;"),
            cancellable = true
    )
    private void lumis$onItemUse(CallbackInfo ci) {
        if (SelfDestruct.unhooked) return;
        if (player == null || interactionManager == null) return;
        if (!NoInteract.getInstance().isState()) return;

        for (Hand hand : Hand.values()) {
            ItemStack stack = player.getStackInHand(hand);
            if (stack == null || stack.isEmpty()) continue;

            ActionResult result = interactionManager.interactItem(player, hand);
            if (!result.isAccepted()) continue;

            if (result instanceof ActionResult.Success success
                    && success.swingSource() == ActionResult.SwingSource.CLIENT) {
                gameRenderer.firstPersonRenderer.resetEquipProgress(hand);
                player.swingHand(hand);
            }

            ci.cancel();
            return;
        }
    }

    // ================================
    // Screen handling
    // ================================

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    protected void lumis$onSetScreen(Screen screen, CallbackInfo ci) {
        if (SelfDestruct.unhooked) return;

        lumis instance = lumis.getInstance();
        if (instance == null || !instance.isInitialized()) return;

        SetScreenEvent event = new SetScreenEvent(screen);
        EventManager.callEvent(event);

        instance.getDraggableRepository()
                .draggable()
                .forEach(drag -> drag.setScreen(event));

        Screen newScreen = event.getScreen();
        if (screen != newScreen) {
            MinecraftClient.getInstance().setScreen(newScreen);
            ci.cancel();
        }
    }

    // ================================
    // HotBar update event
    // ================================

    @Inject(
            method = "handleInputEvents",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getInventory()Lnet/minecraft/entity/player/PlayerInventory;"),
            cancellable = true
    )
    private void lumis$onHotBarUpdate(CallbackInfo ci) {
        if (SelfDestruct.unhooked) return;

        HotBarUpdateEvent event = new HotBarUpdateEvent();
        EventManager.callEvent(event);

        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    // ================================
    // Window title & style
    // ================================

    @Inject(method = "tick", at = @At("HEAD"))
    private void lumis$onTick(CallbackInfo ci) {
        if (SelfDestruct.unhooked) return;

        titleAnimation.updateTitle();
        lumis$updateWindowTitle();
    }

    @Inject(method = "updateWindowTitle", at = @At("HEAD"), cancellable = true)
    private void lumis$onUpdateWindowTitle(CallbackInfo ci) {
        if (SelfDestruct.unhooked) return;

        lumis$updateWindowTitle();
        ci.cancel();
    }

    @Inject(method = "onResolutionChanged", at = @At("TAIL"))
    private void lumis$onResolutionChanged(CallbackInfo ci) {
        if (SelfDestruct.unhooked) return;

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            WindowStyle.setDarkMode(MinecraftClient.getInstance().getWindow().getHandle());
        }
    }

    @Unique
    private void lumis$updateWindowTitle() {
        MinecraftClient.getInstance()
                .getWindow()
                .setTitle(titleAnimation.getCurrentTitle());
    }
}