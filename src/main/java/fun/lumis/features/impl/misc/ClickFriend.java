package fun.lumis.features.impl.misc;

import antidaunleak.api.annotation.Native;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.features.module.setting.implement.BindSetting;
import fun.lumis.common.repository.friend.FriendUtils;
import fun.lumis.events.keyboard.KeyEvent;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ClickFriend extends Module {

    BindSetting friendBind = new BindSetting("Добавить друга", "Добавить/удалить друга");

    public ClickFriend() {
        super("ClickFriend", "Click Friend", ModuleCategory.MISC);
        setup(friendBind);
    }

    @EventHandler
    @Native(type = Native.Type.VMProtectBeginMutation)
    public void onKey(KeyEvent e) {
        if (e.isKeyDown(friendBind.getKey()) && mc.crosshairTarget instanceof EntityHitResult result && result.getEntity() instanceof PlayerEntity player) {
            if (FriendUtils.isFriend(player)) FriendUtils.removeFriend(player);
            else FriendUtils.addFriend(player);
        }
    }
}
