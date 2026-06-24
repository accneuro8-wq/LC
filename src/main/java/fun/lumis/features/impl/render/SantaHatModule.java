package fun.lumis.features.impl.render;

import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;

public class SantaHatModule extends Module {
   private static SantaHatModule instance;

   public SantaHatModule() {
      super("SantaHat", "Santa Hat", ModuleCategory.RENDER);
      instance = this;
   }

   public static SantaHatModule getInstance() {
      return instance;
   }

   public void activate() {
      super.activate();
   }

   public void deactivate() {
      super.deactivate();
   }
}
