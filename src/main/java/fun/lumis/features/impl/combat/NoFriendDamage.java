package fun.lumis.features.impl.combat;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import fun.lumis.common.repository.friend.FriendUtils;
import fun.lumis.features.module.Module;
import fun.lumis.features.module.ModuleCategory;
import fun.lumis.utils.client.managers.event.EventHandler;
import fun.lumis.events.player.AttackEvent;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NoFriendDamage extends Module {
    public NoFriendDamage() {
        super("NoFriendDamage", "No Friend Damage", ModuleCategory.COMBAT);
    }

    @EventHandler
    public void onAttack(AttackEvent e) {
        e.setCancelled(FriendUtils.isFriend(e.getEntity()));
    }
}

