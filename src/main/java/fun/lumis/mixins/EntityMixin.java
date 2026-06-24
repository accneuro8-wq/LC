package fun.lumis.mixins;

import fun.lumis.utils.mixin.IEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Entity.class)
public abstract class EntityMixin implements IEntity {

    public abstract boolean getFlag(int flag);

}
