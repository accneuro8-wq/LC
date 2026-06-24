package fun.lumis.features.impl.render;

import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import lombok.Getter;
import net.minecraft.util.Identifier;

public class Capes extends Module {
    @Getter
    private static Capes instance;

    // Путь к файлу: src/main/resources/assets/minecraft/textures/cape/cape.png
    private final Identifier capeTexture = Identifier.of("minecraft", "textures/cape/cape.png");

    public Capes() {
        super("Capes", "Capes", ModuleCategory.RENDER);
        instance = this;
    }

    public Identifier getCapeTexture() {
        return capeTexture;
    }

    public static boolean isEnabled() {
        return instance != null && instance.isState();
    }
}