package fun.lumis.mixins.player.cape;

import com.mojang.authlib.GameProfile;
import fun.lumis.features.impl.render.Capes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListEntry.class)
public abstract class MixinPlayerListEntry {

    @Shadow @Final private GameProfile profile;

    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void injectCustomCape(CallbackInfoReturnable<SkinTextures> cir) {
        // Проверка через наш класс Capes
        if (!Capes.isEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();

        // Проверяем UUID, чтобы плащ был только у тебя
        if (client.getSession() != null && profile.getId().equals(client.getSession().getUuidOrNull())) {
            SkinTextures original = cir.getReturnValue();
            Identifier customCape = Capes.getInstance().getCapeTexture();

            // В 1.21.4 SkinTextures это рекорд, создаем копию с нашим плащом
            cir.setReturnValue(new SkinTextures(
                    original.texture(),
                    original.textureUrl(),
                    customCape,     // Cape
                    customCape,     // Elytra
                    original.model(),
                    original.secure()
            ));
        }
    }
}